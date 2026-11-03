package com.docstorage.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka 消费者 - 缩略图生成任务
 * <p>
 * 缩略图生成是耗时IO操作 (需要下载原图 + 压缩 + 上传)
 * 实际生产中建议用独立服务处理, 这里演示完整链路
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ThumbnailTaskConsumer {

    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "${spring.kafka.topics.thumbnail-task}",
            groupId = "thumbnail-group",
            containerFactory = "manualAckListenerFactory")
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            String value = record.value();
            log.info("Received thumbnail task: {}", value);

            JsonNode node = objectMapper.readTree(value);
            Long fileId = node.get("fileId").asLong();
            String storagePath = node.get("storagePath").asText();
            String bucket = node.get("bucket").asText();

            // ========== 缩略图生成核心逻辑 (简化版) ==========
            // 生产环境需要:
            // 1. 从存储层下载原图
            // 2. 使用 ImageIO 或 OpenCV 生成缩略图 (缩放至 200x200)
            // 3. 上传缩略图到存储层
            // 4. 更新 DocFile.thumbnail_url 字段

            // 这里仅做占位日志, 演示链路
            log.info("Thumbnail would be generated: fileId={}, path={}, bucket={}",
                    fileId, storagePath, bucket);

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Thumbnail task failed", e);
            throw new RuntimeException("Thumbnail task failed", e);
        }
    }
}