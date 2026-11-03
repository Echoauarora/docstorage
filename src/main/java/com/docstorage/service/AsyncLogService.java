package com.docstorage.service;

import com.docstorage.entity.DocAccessLog;

/**
 * 异步日志服务接口
 */
public interface AsyncLogService {

    /**
     * 发送访问日志到Kafka (异步)
     */
    void sendAccessLog(Long userId, Long fileId, String accessType, int duration, boolean success, String errorMsg);

    /**
     * 发送缩略图生成任务
     */
    void sendThumbnailTask(Long fileId, String storagePath, String bucket);
}