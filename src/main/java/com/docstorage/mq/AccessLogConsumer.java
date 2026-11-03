package com.docstorage.mq;

import com.docstorage.entity.DocAccessLog;
import com.docstorage.mapper.DocAccessLogMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Kafka 消费者 - 访问日志落库
 * <p>
 * 手动 ACK 模式, 确保业务处理完成后才确认消息
 * 异常进入死信队列 (由 DefaultErrorHandler 处理)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccessLogConsumer {

    private final ObjectMapper objectMapper;
    private final DocAccessLogMapper logMapper;

    @Value("${spring.kafka.topics.access-log}")
    private String topic;

    /**
     * 手动 ACK 消费
     * ack.acknowledge() 必须在业务成功后调用
     */
    @KafkaListener(topics = "${spring.kafka.topics.access-log}",
            groupId = "access-log-group",
            containerFactory = "manualAckListenerFactory")
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            String value = record.value();
            log.debug("Received access log: {}", value);

            JsonNode node = objectMapper.readTree(value);

            DocAccessLog accessLog = new DocAccessLog();
            accessLog.setUserId(node.has("userId") && !node.get("userId").isNull() ?
                    node.get("userId").asLong() : null);
            accessLog.setFileId(node.has("fileId") && !node.get("fileId").isNull() ?
                    node.get("fileId").asLong() : null);
            accessLog.setAccessType(node.get("accessType").asText());
            accessLog.setDuration(node.has("duration") ? node.get("duration").asInt() : null);
            accessLog.setResult(node.has("result") ? node.get("result").asInt() : 1);
            accessLog.setErrorMsg(node.has("errorMsg") && !node.get("errorMsg").isNull() ?
                    node.get("errorMsg").asText() : null);
            accessLog.setCreateTime(LocalDateTime.now());

            logMapper.insert(accessLog);

            // 手动 ACK - 业务成功后才确认
            ack.acknowledge();
            log.debug("Access log saved, offset acked");

        } catch (Exception e) {
            log.error("Access log consume failed", e);
            // 不 ack, 让 DefaultErrorHandler 重试或进死信队列
            throw new RuntimeException("Access log consume failed", e);
        }
    }
}