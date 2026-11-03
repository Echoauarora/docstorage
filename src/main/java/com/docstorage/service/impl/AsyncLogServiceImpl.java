package com.docstorage.service.impl;

import com.docstorage.service.AsyncLogService;
import com.docstorage.util.KafkaProducerUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 异步日志/任务服务实现
 * <p>
 * 所有方法通过 Kafka 异步发送, 不阻塞主业务流程
 * Kafka 消费者会落库或执行缩略图生成
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncLogServiceImpl implements AsyncLogService {

    private final KafkaProducerUtil kafkaProducer;

    @Override
    @Async("docStorageExecutor")
    public void sendAccessLog(Long userId, Long fileId, String accessType, int duration,
                              boolean success, String errorMsg) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        payload.put("fileId", fileId);
        payload.put("accessType", accessType);
        payload.put("duration", duration);
        payload.put("result", success ? 1 : 0);
        payload.put("errorMsg", errorMsg);
        payload.put("timestamp", System.currentTimeMillis());

        kafkaProducer.sendAccessLog(payload);
    }

    @Override
    @Async("docStorageExecutor")
    public void sendThumbnailTask(Long fileId, String storagePath, String bucket) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("fileId", fileId);
        payload.put("storagePath", storagePath);
        payload.put("bucket", bucket);
        payload.put("timestamp", System.currentTimeMillis());

        kafkaProducer.sendThumbnailTask(payload);
    }
}