package com.docstorage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.docstorage.dto.req.RollbackVersionReq;
import com.docstorage.dto.req.SetFilePermissionReq;
import com.docstorage.dto.res.FileInfoRes;
import com.docstorage.dto.res.VersionListRes;
import com.docstorage.entity.DocFile;
import com.docstorage.entity.DocFilePermission;
import com.docstorage.entity.DocFileVersion;
import com.docstorage.entity.DocRecycleBin;
import com.docstorage.exception.AuthException;
import com.docstorage.exception.BizException;
import com.docstorage.mapper.DocFileMapper;
import com.docstorage.mapper.DocFilePermissionMapper;
import com.docstorage.mapper.DocFileVersionMapper;
import com.docstorage.mapper.DocRecycleBinMapper;
import com.docstorage.service.AsyncLogService;
import com.docstorage.service.FileManageService;
import com.docstorage.storage.StorageStrategy;
import com.docstorage.storage.StorageStrategyFactory;
import com.docstorage.util.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文件管理服务实现
 * <p>
 * 核心能力:
 * 1. 权限校验 (文件所有者 > 直接用户授权 > 角色授权)
 * 2. 缓存策略: Redis 缓存权限结果, 变更时主动失效
 * 3. 版本管理: 回滚时指向历史版本的 storagePath
 * 4. 回收站: 软删除入回收站, 30天后物理清理
 * 5. 存储后端: 通过 StorageStrategy 抽象, 当前基于本地磁盘分片存储
 */
@Slf4j
@Service
public class FileManageServiceImpl implements FileManageService {

    private final DocFileMapper fileMapper;
    private final DocFilePermissionMapper permissionMapper;
    private final DocFileVersionMapper versionMapper;
    private final DocRecycleBinMapper recycleBinMapper;
    private final StorageStrategy storageStrategy;
    private final RedisUtil redisUtil;
    private final AsyncLogService asyncLogService;

    public FileManageServiceImpl(StorageStrategyFactory factory,
                                 DocFileMapper fileMapper,
                                 DocFilePermissionMapper permissionMapper,
                                 DocFileVersionMapper versionMapper,
                                 DocRecycleBinMapper recycleBinMapper,
                                 RedisUtil redisUtil,
                                 AsyncLogService asyncLogService) {
        this.storageStrategy = factory.getStrategy();
        this.fileMapper = fileMapper;
        this.permissionMapper = permissionMapper;
        this.versionMapper = versionMapper;
        this.recycleBinMapper = recycleBinMapper;
        this.redisUtil = redisUtil;
        this.asyncLogService = asyncLogService;
    }

    @Value("${cache.expire.permission}")
    private long permCacheExpire;

    @Value("${docstorage.recycle-days}")
    private int recycleDays;

    // ==================== 文件详情 ====================

    @Override
    public FileInfoRes getFileInfo(Long fileId, Long userId) {
        DocFile file = fileMapper.selectById(fileId);
        if (file == null || file.getDeleted() == 1) {
            throw new BizException("文件不存在");
        }

        if (!hasPermission(fileId, userId, 1)) {
            throw new AuthException(403, "没有查看权限");
        }

        return FileInfoRes.builder()
                .id(file.getId())
                .fileCode(file.getFileCode())
                .filename(file.getFilename())
                .fileExt(file.getFileExt())
                .fileSize(file.getFileSize())
                .fileMd5(file.getFileMd5())
                .fileType(file.getFileType())
                .thumbnailUrl(file.getThumbnailUrl())
                .version(file.getVersion())
                .fileStatus(file.getFileStatus())
                .ownerId(file.getOwnerId())
                .storagePath(file.getStoragePath())
                .createTime(file.getCreateTime() != null ? file.getCreateTime().toString() : null)
                .build();
    }

    // ==================== 权限校验 ====================

    @Override
    public boolean hasPermission(Long fileId, Long userId, Integer permType) {
        if (userId != null && userId == 1L) {
            return true;
        }

        String cacheKey = String.format(RedisUtil.KEY_PERMISSION, userId, fileId) + ":" + permType;
        Boolean cached = redisUtil.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        int count = permissionMapper.countUserHasPermission(fileId, userId, permType);
        boolean hasPerm = count > 0;

        redisUtil.set(cacheKey, hasPerm, permCacheExpire);
        log.debug("Permission check: fileId={}, userId={}, permType={}, result={}", fileId, userId, permType, hasPerm);

        return hasPerm;
    }

    // ==================== 设置权限 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setPermission(SetFilePermissionReq req, Long userId) {
        if (!hasPermission(req.getFileId(), userId, 3)) {
            throw new AuthException(403, "没有权限设置文件权限");
        }

        DocFilePermission perm = new DocFilePermission();
        perm.setFileId(req.getFileId());
        perm.setUserId(req.getUserId());
        perm.setRoleId(req.getRoleId());
        perm.setPermType(req.getPermType());
        permissionMapper.insert(perm);

        // ========== 缓存主动失效 ==========
        String pattern = "doc:perm:user:*:file:" + req.getFileId() + "*";
        redisUtil.deleteByPattern(pattern);

        if (req.getUserId() != null) {
            redisUtil.deleteByPattern("doc:perm:user:" + req.getUserId() + ":*");
        }

        log.info("Permission set: fileId={}, userId={}, roleId={}, permType={}",
                req.getFileId(), req.getUserId(), req.getRoleId(), req.getPermType());
    }

    // ==================== 软删除 (进入回收站) ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void softDelete(Long fileId, Long userId) {
        DocFile file = fileMapper.selectById(fileId);
        if (file == null) throw new BizException("文件不存在");

        if (!hasPermission(fileId, userId, 3)) {
            throw new AuthException(403, "没有删除权限");
        }

        DocRecycleBin recycle = new DocRecycleBin();
        recycle.setFileId(fileId);
        recycle.setFileCode(file.getFileCode());
        recycle.setFilename(file.getFilename());
        recycle.setFileSize(file.getFileSize());
        recycle.setOwnerId(file.getOwnerId());
        recycle.setDeleteUserId(userId);
        recycle.setExpireTime(LocalDateTime.now().plusDays(recycleDays));
        recycle.setStatus(1);
        recycleBinMapper.insert(recycle);

        file.setDeleted(1);
        fileMapper.updateById(file);

        redisUtil.deleteByPattern("doc:perm:*:file:" + fileId + "*");

        asyncLogService.sendAccessLog(userId, fileId, "delete", 0, true, null);

        log.info("File soft deleted: fileId={}, userId={}", fileId, userId);
    }

    // ==================== 回收站恢复 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recoverFromRecycle(Long fileId, Long userId) {
        DocRecycleBin record = recycleBinMapper.selectOne(
                new LambdaQueryWrapper<DocRecycleBin>()
                        .eq(DocRecycleBin::getFileId, fileId)
                        .eq(DocRecycleBin::getStatus, 1)
        );
        if (record == null) throw new BizException("回收站中没有该文件");

        if (!record.getOwnerId().equals(userId)) {
            throw new AuthException(403, "只能恢复自己删除的文件");
        }

        record.setStatus(2);
        recycleBinMapper.updateById(record);

        DocFile file = new DocFile();
        file.setId(fileId);
        file.setDeleted(0);
        fileMapper.updateById(file);

        log.info("File recovered: fileId={}, userId={}", fileId, userId);
    }

    // ==================== 永久删除 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void permanentlyDelete(Long fileId, Long userId) {
        DocFile file = fileMapper.selectById(fileId);
        if (file == null) throw new BizException("文件不存在");

        if (!file.getOwnerId().equals(userId) && userId != 1L) {
            throw new AuthException(403, "没有永久删除权限");
        }

        // 物理删除存储文件 (引用计数判断: 如果是秒传复用不能删物理文件)
        try {
            long refCount = fileMapper.selectCount(
                    new LambdaQueryWrapper<DocFile>()
                            .eq(DocFile::getFileMd5, file.getFileMd5())
                            .eq(DocFile::getDeleted, 0)
            );
            if (refCount <= 1) {
                storageStrategy.removeObject(file.getBucketName(), file.getStoragePath());
                log.info("Storage object physically deleted: root={}, path={}", file.getBucketName(), file.getStoragePath());
            } else {
                log.info("Skip storage delete (shared by {} files): md5={}", refCount, file.getFileMd5());
            }
        } catch (Exception e) {
            log.warn("Storage delete failed (non-critical): {}", e.getMessage());
        }

        fileMapper.deleteById(fileId);
        permissionMapper.delete(
                new LambdaQueryWrapper<DocFilePermission>().eq(DocFilePermission::getFileId, fileId)
        );
        versionMapper.delete(
                new LambdaQueryWrapper<DocFileVersion>().eq(DocFileVersion::getFileId, fileId)
        );

        redisUtil.deleteByPattern("doc:perm:*:file:" + fileId + "*");

        log.info("File permanently deleted: fileId={}, userId={}", fileId, userId);
    }

    // ==================== 版本列表 ====================

    @Override
    public VersionListRes getVersionList(Long fileId, Long userId) {
        if (!hasPermission(fileId, userId, 1)) {
            throw new AuthException(403, "没有查看权限");
        }

        DocFile file = fileMapper.selectById(fileId);
        List<DocFileVersion> versions = versionMapper.selectList(
                new LambdaQueryWrapper<DocFileVersion>()
                        .eq(DocFileVersion::getFileId, fileId)
                        .orderByDesc(DocFileVersion::getVersionNumber)
        );

        List<VersionListRes.VersionItem> items = versions.stream()
                .map(v -> VersionListRes.VersionItem.builder()
                        .versionNumber(v.getVersionNumber())
                        .fileSize(v.getFileSize())
                        .fileMd5(v.getFileMd5())
                        .changeLog(v.getChangeLog())
                        .operatorName(v.getOperatorId().toString())
                        .createTime(v.getCreateTime() != null ? v.getCreateTime().toString() : null)
                        .build())
                .collect(Collectors.toList());

        return VersionListRes.builder()
                .fileId(fileId)
                .filename(file != null ? file.getFilename() : null)
                .currentVersion(file != null ? file.getVersion() : null)
                .versions(items)
                .build();
    }

    // ==================== 版本回滚 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileInfoRes rollbackVersion(RollbackVersionReq req, Long userId) {
        if (!hasPermission(req.getFileId(), userId, 2)) {
            throw new AuthException(403, "没有编辑权限, 无法回滚版本");
        }

        DocFile file = fileMapper.selectById(req.getFileId());
        if (file == null) throw new BizException("文件不存在");

        DocFileVersion targetVersion = versionMapper.selectOne(
                new LambdaQueryWrapper<DocFileVersion>()
                        .eq(DocFileVersion::getFileId, req.getFileId())
                        .eq(DocFileVersion::getVersionNumber, req.getTargetVersion())
        );
        if (targetVersion == null) throw new BizException("目标版本不存在");

        int newVersionNum = file.getVersion() + 1;
        DocFileVersion newVersion = new DocFileVersion();
        newVersion.setFileId(file.getId());
        newVersion.setVersionNumber(newVersionNum);
        newVersion.setFileSize(targetVersion.getFileSize());
        newVersion.setFileMd5(targetVersion.getFileMd5());
        newVersion.setStoragePath(targetVersion.getStoragePath());
        newVersion.setBucketName(targetVersion.getBucketName());
        newVersion.setOperatorId(userId);
        newVersion.setChangeLog(req.getChangeLog() != null ? req.getChangeLog() : "回滚到版本 " + req.getTargetVersion());
        versionMapper.insert(newVersion);

        file.setStoragePath(targetVersion.getStoragePath());
        file.setFileSize(targetVersion.getFileSize());
        file.setFileMd5(targetVersion.getFileMd5());
        file.setVersion(newVersionNum);
        fileMapper.updateById(file);

        log.info("Version rollback: fileId={}, rollbackFrom=v{} to v{}, newVersion=v{}",
                req.getFileId(), file.getVersion(), req.getTargetVersion(), newVersionNum);

        return FileInfoRes.builder()
                .id(file.getId())
                .filename(file.getFilename())
                .fileSize(file.getFileSize())
                .version(file.getVersion())
                .storagePath(file.getStoragePath())
                .build();
    }
}