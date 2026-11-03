package com.docstorage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.docstorage.dto.req.CompleteUploadReq;
import com.docstorage.dto.req.InitUploadReq;
import com.docstorage.dto.res.CompleteUploadRes;
import com.docstorage.dto.res.InitUploadRes;
import com.docstorage.entity.DocFile;
import com.docstorage.entity.DocUploadPart;
import com.docstorage.entity.DocFileVersion;
import com.docstorage.enums.FileStatusEnum;
import com.docstorage.enums.PartStatusEnum;
import com.docstorage.exception.BizException;
import com.docstorage.mapper.DocFileMapper;
import com.docstorage.mapper.DocFileVersionMapper;
import com.docstorage.mapper.DocUploadPartMapper;
import com.docstorage.service.AsyncLogService;
import com.docstorage.service.FileUploadService;
import com.docstorage.storage.StorageStrategy;
import com.docstorage.storage.StorageStrategyFactory;
import com.docstorage.storage.StorageProperties;
import com.docstorage.util.Md5Util;
import com.docstorage.util.RedisUtil;
import com.docstorage.util.StoragePathUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文件分片上传服务实现
 * <p>
 * 完整流程 (面试必讲):
 * ┌─────────────┐     ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
 * │ 1.初始化    │────▶│ 2.上传分片  │────▶│ 3.查询已上传│────▶│ 4.合并分片  │
 * │  MD5秒传    │     │  Storage    │     │  断点续传   │     │  完整性校验 │
 * └─────────────┘     └─────────────┘     └─────────────┘     └─────────────┘
 * <p>
 * 存储策略: 当前基于 LocalFileStorageStrategy 本地磁盘分片存储
 * 架构预留 StorageStrategy 接口, 后续可扩展对接对象存储
 * <p>
 * 线程模型:
 * - 初始化/合并: 主请求线程 (需要事务)
 * - 分片上传: 可并行 (每个分片独立请求, 存储层保证幂等)
 * <p>
 * 存储模型:
 * - 业务编码 fileCode 是幂等键, 相同fileCode只初始化一次
 * - uploadId 是分片上传句柄, 一个 uploadId 对应一个文件
 */
@Slf4j
@Service
public class FileUploadServiceImpl implements FileUploadService {

    private final StorageStrategy storageStrategy;
    private final StorageProperties storageProperties;
    private final DocFileMapper fileMapper;
    private final DocUploadPartMapper uploadPartMapper;
    private final DocFileVersionMapper versionMapper;
    private final RedisUtil redisUtil;
    private final AsyncLogService asyncLogService;

    public FileUploadServiceImpl(StorageStrategyFactory factory,
                                 StorageProperties storageProperties,
                                 DocFileMapper fileMapper,
                                 DocUploadPartMapper uploadPartMapper,
                                 DocFileVersionMapper versionMapper,
                                 RedisUtil redisUtil,
                                 AsyncLogService asyncLogService) {
        this.storageStrategy = factory.getStrategy();
        this.storageProperties = storageProperties;
        this.fileMapper = fileMapper;
        this.uploadPartMapper = uploadPartMapper;
        this.versionMapper = versionMapper;
        this.redisUtil = redisUtil;
        this.asyncLogService = asyncLogService;
    }

    @Value("${cache.expire.upload-part}")
    private long uploadStateExpire;

    // ==================== 1. 初始化分片上传 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InitUploadRes initUpload(InitUploadReq req, Long userId) {
        String fileCode = req.getFileCode();

        // 0. 幂等检查: 同一 fileCode 不能重复初始化
        DocFile existingByCode = fileMapper.selectOne(
                new LambdaQueryWrapper<DocFile>().eq(DocFile::getFileCode, fileCode)
        );
        if (existingByCode != null && existingByCode.getFileStatus() == FileStatusEnum.NORMAL.getCode()) {
            throw new BizException("文件编码已存在, 请勿重复初始化");
        }

        // ========== MD5秒传核心逻辑 ==========
        String md5CacheKey = String.format(RedisUtil.KEY_MD5_EXIST, req.getFileMd5());
        DocFile md5File = redisUtil.get(md5CacheKey);
        if (md5File == null) {
            md5File = fileMapper.selectExistingByMd5(req.getFileMd5());
            if (md5File != null) {
                redisUtil.set(md5CacheKey, md5File, uploadStateExpire);
            }
        }

        if (md5File != null) {
            // 命中秒传! 直接复用已有文件, 不占用存储空间
            log.info("Instant upload hit: fileMd5={}, reuseFileId={}", req.getFileMd5(), md5File.getId());

            DocFile newRef = new DocFile();
            newRef.setFileCode(fileCode);
            newRef.setFilename(req.getFilename());
            newRef.setFileExt(extractExt(req.getFilename()));
            newRef.setFileSize(md5File.getFileSize());
            newRef.setFileMd5(req.getFileMd5());
            newRef.setFileType(req.getFileType());
            newRef.setStoragePath(md5File.getStoragePath());
            newRef.setBucketName(md5File.getBucketName());
            newRef.setOwnerId(userId);
            newRef.setParentId(req.getParentId() != null ? req.getParentId() : 0L);
            newRef.setFileStatus(FileStatusEnum.NORMAL.getCode());
            newRef.setVersion(1);
            fileMapper.insert(newRef);

            return InitUploadRes.builder()
                    .instantUpload(true)
                    .existingFileId(newRef.getId())
                    .fileCode(fileCode)
                    .build();
        }

        // ========== 非秒传, 创建分片上传 ==========
        long partSize = req.getPartSize() != null ? req.getPartSize() : storageProperties.getDefaultPartSize();
        int totalParts = (int) Math.ceil((double) req.getFileSize() / partSize);

        // 1. 确保存储根存在
        String storageRoot = resolveStorageRoot();
        storageStrategy.ensureStorageRoot(storageRoot);

        // 2. 生成存储路径 + uploadId
        String storagePath = StoragePathUtil.generate(extractExt(req.getFilename()));
        String uploadId = storageStrategy.initMultipartUpload(storageRoot, storagePath, req.getFileType());

        // 3. 写入 DocFile (状态=上传中)
        DocFile docFile = new DocFile();
        docFile.setFileCode(fileCode);
        docFile.setFilename(req.getFilename());
        docFile.setFileExt(extractExt(req.getFilename()));
        docFile.setFileSize(req.getFileSize());
        docFile.setFileMd5(req.getFileMd5());
        docFile.setFileType(req.getFileType());
        docFile.setStoragePath(storagePath);
        docFile.setBucketName(storageRoot);
        docFile.setUploadId(uploadId);
        docFile.setOwnerId(userId);
        docFile.setParentId(req.getParentId() != null ? req.getParentId() : 0L);
        docFile.setFileStatus(FileStatusEnum.UPLOADING.getCode());
        docFile.setVersion(1);
        fileMapper.insert(docFile);

        // 4. Redis 记录上传状态
        String redisStateKey = String.format(RedisUtil.KEY_UPLOAD_STATE, fileCode);
        redisUtil.set(redisStateKey, uploadId, uploadStateExpire);
        redisUtil.hSet(redisStateKey, "bucket", storageRoot);
        redisUtil.hSet(redisStateKey, "objectName", storagePath);
        redisUtil.hSet(redisStateKey, "totalParts", totalParts);
        redisUtil.hSet(redisStateKey, "uploadedParts", new ArrayList<Integer>());

        // 5. 断点续传: 查询已上传分片
        List<Integer> uploadedParts = queryUploadedParts(uploadId);
        if (!uploadedParts.isEmpty()) {
            log.info("Resume upload: fileCode={}, uploadId={}, alreadyUploaded={}",
                    fileCode, uploadId, uploadedParts.size());
        }

        return InitUploadRes.builder()
                .uploadId(uploadId)
                .partSize(partSize)
                .totalParts(totalParts)
                .instantUpload(false)
                .uploadedParts(uploadedParts)
                .build();
    }

    // ==================== 2. 上传单个分片 ====================

    @Override
    public String uploadPart(String uploadId, String fileCode, int partNumber,
                             InputStream stream, long size, Long userId) {
        // 1. 查找文件记录
        DocFile docFile = fileMapper.selectOne(
                new LambdaQueryWrapper<DocFile>().eq(DocFile::getFileCode, fileCode)
        );
        if (docFile == null) {
            throw new BizException("文件记录不存在");
        }
        if (!uploadId.equals(docFile.getUploadId())) {
            throw new BizException("uploadId 不匹配");
        }

        // 2. 调用存储策略上传分片
        String etag = storageStrategy.uploadPart(
                docFile.getBucketName(),
                docFile.getStoragePath(),
                uploadId,
                partNumber,
                stream,
                size
        );

        // 3. 记录分片到 DB (唯一索引防止重复)
        DocUploadPart part = new DocUploadPart();
        part.setUploadId(uploadId);
        part.setFileCode(fileCode);
        part.setPartNumber(partNumber);
        part.setPartSize(size);
        part.setEtag(etag);
        part.setStatus(PartStatusEnum.UPLOADED.getCode());

        try {
            uploadPartMapper.insert(part);
        } catch (Exception e) {
            log.warn("Part already exists: uploadId={}, partNumber={}", uploadId, partNumber);
            return etag;
        }

        // 4. 更新 Redis 上传状态
        String redisKey = String.format(RedisUtil.KEY_UPLOAD_STATE, fileCode);
        @SuppressWarnings("unchecked")
        List<Integer> uploaded = (List<Integer>) redisUtil.get(redisKey + ":parts");
        if (uploaded != null && !uploaded.contains(partNumber)) {
            uploaded.add(partNumber);
            redisUtil.set(redisKey + ":parts", uploaded, uploadStateExpire);
        }

        log.info("Part uploaded: uploadId={}, partNumber={}, etag={}", uploadId, partNumber, etag);
        return etag;
    }

    // ==================== 3. 查询已上传分片 (断点续传) ====================

    @Override
    public List<Integer> queryUploadedParts(String uploadId) {
        // 优先从 DB 查 (最稳定, 存储层 listParts 也可以但 DB 已记录)
        try {
            List<DocUploadPart> dbParts = uploadPartMapper.selectList(
                    new LambdaQueryWrapper<DocUploadPart>()
                            .eq(DocUploadPart::getUploadId, uploadId)
                            .eq(DocUploadPart::getStatus, PartStatusEnum.UPLOADED.getCode())
                            .orderByAsc(DocUploadPart::getPartNumber)
            );
            return dbParts.stream()
                    .map(DocUploadPart::getPartNumber)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Query uploaded parts failed: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    // ==================== 4. 合并所有分片 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CompleteUploadRes completeUpload(CompleteUploadReq req, Long userId) {
        String uploadId = req.getUploadId();

        // 1. 查找文件记录
        DocFile docFile = fileMapper.selectOne(
                new LambdaQueryWrapper<DocFile>().eq(DocFile::getFileCode, req.getFileCode())
        );
        if (docFile == null) {
            throw new BizException("文件记录不存在");
        }

        // 2. 状态机校验: 必须是上传中才能合并
        if (!FileStatusEnum.UPLOADING.getCode().equals(docFile.getFileStatus())) {
            if (FileStatusEnum.NORMAL.getCode().equals(docFile.getFileStatus())) {
                return CompleteUploadRes.builder()
                        .fileId(docFile.getId())
                        .fileCode(docFile.getFileCode())
                        .storagePath(docFile.getStoragePath())
                        .fileSize(docFile.getFileSize())
                        .version(docFile.getVersion())
                        .md5Matched(true)
                        .build();
            }
            throw new BizException("当前状态不允许合并: " + docFile.getFileStatus());
        }

        // ========== 分片并发合并冲突处理 ==========
        String lockKey = "doc:merge:lock:" + uploadId;
        boolean locked = redisUtil.tryLock(lockKey, String.valueOf(userId), 120);
        if (!locked) {
            throw new BizException("文件正在合并中, 请勿重复操作");
        }

        try {
            // 3. 调用存储策略合并分片
            storageStrategy.completeMultipartUpload(
                    docFile.getBucketName(),
                    docFile.getStoragePath(),
                    uploadId
            );

            // 4. 最终 MD5 校验 (服务端兜底, 防篡改)
            String actualMd5;
            try {
                actualMd5 = Md5Util.calculateStreamMd5(
                        storageStrategy.getObject(docFile.getBucketName(), docFile.getStoragePath())
                );
            } catch (java.io.IOException e) {
                throw new BizException("MD5校验失败: " + e.getMessage());
            }
            boolean md5Matched = actualMd5.equalsIgnoreCase(docFile.getFileMd5());
            if (!md5Matched) {
                log.error("MD5 mismatch! expected={}, actual={}", docFile.getFileMd5(), actualMd5);
            }

            // 5. 更新文件状态为正常
            docFile.setFileStatus(FileStatusEnum.NORMAL.getCode());
            docFile.setUploadId(null);
            fileMapper.updateById(docFile);

            // 6. 更新分片记录为已合并
            uploadPartMapper.update(
                    new DocUploadPart(),
                    new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<DocUploadPart>()
                            .eq("upload_id", uploadId)
                            .set("status", PartStatusEnum.MERGED.getCode())
            );

            // 7. 写入版本记录
            DocFileVersion version = new DocFileVersion();
            version.setFileId(docFile.getId());
            version.setVersionNumber(1);
            version.setFileSize(docFile.getFileSize());
            version.setFileMd5(docFile.getFileMd5());
            version.setStoragePath(docFile.getStoragePath());
            version.setBucketName(docFile.getBucketName());
            version.setOperatorId(userId);
            version.setChangeLog("初始版本");
            versionMapper.insert(version);

            // 8. 清理 Redis 状态
            String redisKey = String.format(RedisUtil.KEY_UPLOAD_STATE, req.getFileCode());
            redisUtil.delete(redisKey);

            // 9. 发送异步任务
            asyncLogService.sendThumbnailTask(docFile.getId(), docFile.getStoragePath(), docFile.getBucketName());
            asyncLogService.sendAccessLog(userId, docFile.getId(), "upload", 0, true, null);

            return CompleteUploadRes.builder()
                    .fileId(docFile.getId())
                    .fileCode(docFile.getFileCode())
                    .storagePath(docFile.getStoragePath())
                    .fileSize(docFile.getFileSize())
                    .version(docFile.getVersion())
                    .md5Matched(md5Matched)
                    .build();

        } finally {
            redisUtil.releaseLock(lockKey);
        }
    }

    // ==================== 5. 取消分片上传 ====================

    @Override
    public void abortUpload(String uploadId) {
        DocFile docFile = fileMapper.selectOne(
                new LambdaQueryWrapper<DocFile>().eq(DocFile::getUploadId, uploadId)
        );
        if (docFile == null) return;

        storageStrategy.abortMultipartUpload(docFile.getBucketName(), docFile.getStoragePath(), uploadId);
        docFile.setDeleted(1);
        fileMapper.updateById(docFile);

        uploadPartMapper.delete(
                new LambdaQueryWrapper<DocUploadPart>().eq(DocUploadPart::getUploadId, uploadId)
        );

        String redisKey = String.format(RedisUtil.KEY_UPLOAD_STATE, docFile.getFileCode());
        redisUtil.delete(redisKey);
    }

    // ==================== 内部方法 ====================

    /**
     * 解析存储根 (本地存储模式返回类型标识, 实际路径由 LocalFileStorageStrategy 内部解析)
     */
    private String resolveStorageRoot() {
        return storageStrategy.getType();
    }

    private String extractExt(String filename) {
        if (filename == null) return null;
        int idx = filename.lastIndexOf('.');
        return idx >= 0 ? filename.substring(idx + 1) : null;
    }
}