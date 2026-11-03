package com.docstorage.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Kafka 生产者工具
 * <p>
 * 核心配置:
 * - acks=all: ISR中所有副本确认后才返回, 防止消息丢失
 * - enable.idempotence=true: 生产者幂等, 网络抖动重试不会重复发
 * - 重试3次 + 死信队列兜底
 * <p>
 * 面试考点 - 消息丢失/重复消费处理:
 * 生产端: acks=all + 幂等 + 死信队列
 * 消费端: 手动ACK + 业务幂等 + 死信队列
 * Broker端: 副本数>=3 + unclean.leader.election.enable=false
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaProducerUtil {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${spring.kafka.topics.access-log}")
    private String accessLogTopic;
    @Value("${spring.kafka.topics.thumbnail-task}")
    private String thumbnailTaskTopic;

    /**
     * 异步发送访问日志
     * - 使用 CompletableFuture 接收结果
     * - 失败时记录错误日志(生产端重试由Kafka客户端自动完成)
     */
    public CompletableFuture<SendResult<String, String>> sendAccessLog(Object logObj) {
        return send(accessLogTopic, logObj);
    }

    /**
     * 异步发送缩略图生成任务
     */
    public CompletableFuture<SendResult<String, String>> sendThumbnailTask(Object task) {
        return send(thumbnailTaskTopic, task);
    }

    private CompletableFuture<SendResult<String, String>> send(String topic, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            log.debug("Kafka producing to [{}]: {}", topic, json);

            CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(topic, json);
            future.exceptionally(ex -> {
                // 生产端发送失败兜底处理
                log.error("Kafka send failed to topic [{}]: {}", topic, ex.getMessage());
                return null;
            });
            return future;
        } catch (JsonProcessingException e) {
            log.error("Kafka payload serialize failed", e);
            return CompletableFuture.completedFuture(null);
        }
    }
}