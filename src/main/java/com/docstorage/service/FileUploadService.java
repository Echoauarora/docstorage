package com.docstorage.service;

import com.docstorage.dto.req.CompleteUploadReq;
import com.docstorage.dto.req.InitUploadReq;
import com.docstorage.dto.res.CompleteUploadRes;
import com.docstorage.dto.res.InitUploadRes;

import java.util.List;

/**
 * 文件分片上传服务接口
 */
public interface FileUploadService {

    /**
     * 初始化分片上传
     * - 检查MD5是否已存在 (秒传)
     * - 创建分片上传会话
     * - 记录上传状态到 Redis
     */
    InitUploadRes initUpload(InitUploadReq req, Long userId);

    /**
     * 上传单个分片
     * - 通过 StorageStrategy 写入存储层
     * - 记录分片状态到 Redis + DB
     */
    String uploadPart(String uploadId, String fileCode, int partNumber,
                      java.io.InputStream stream, long size, Long userId);

    /**
     * 查询已上传分片列表 (断点续传)
     */
    List<Integer> queryUploadedParts(String uploadId);

    /**
     * 合并所有分片
     * - 通过 StorageStrategy 合并分片
     * - 校验最终MD5
     * - 落库 DocFile + DocFileVersion
     */
    CompleteUploadRes completeUpload(CompleteUploadReq req, Long userId);

    /**
     * 取消/清理分片上传
     */
    void abortUpload(String uploadId);
}