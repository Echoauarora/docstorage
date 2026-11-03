package com.docstorage.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka 配置类
 * <p>
 * 核心配置策略:
 * 1. 生产者: acks=all + enable.idempotence=true, 保证消息不丢不重
 * 2. 消费者: enable-auto-commit=false, 手动ACK, 确保业务处理完成才确认
 * 3. 死信队列: 消费失败3次后进入死信队列, 不阻塞主消费流程
 * <p>
 * 坑点: 手动ACK时如果抛异常但没有设置errorHandler, 消息会一直重入
 */
@Slf4j
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.topics.access-log}")
    private String accessLogTopic;
    @Value("${spring.kafka.topics.thumbnail-task}")
    private String thumbnailTaskTopic;
    @Value("${spring.kafka.topics.dead-letter}")
    private String deadLetterTopic;

    /**
     * 创建 Topic Bean (应用启动时自动创建)
     * replicationFactor=1 适合开发环境, 生产环境建议 >=3
     */
    @Bean
    public NewTopic accessLogTopic() {
        return new NewTopic(accessLogTopic, 4, (short) 1);
    }

    @Bean
    public NewTopic thumbnailTaskTopic() {
        return new NewTopic(thumbnailTaskTopic, 2, (short) 1);
    }

    @Bean
    public NewTopic deadLetterTopic() {
        return new NewTopic(deadLetterTopic, 2, (short) 1);
    }

    /**
     * 手动ACK的监听器工厂
     * - ackMode=MANUAL: 手动调用 ack.acknowledge()
     * - 死信队列处理: 重试3次后进入死信队列
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> manualAckListenerFactory(
            KafkaTemplate<String, Object> kafkaTemplate,
            ConsumerFactory<String, Object> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        // 手动ACK模式
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        // 单线程消费, 分片上传等任务需要顺序处理时可用
        factory.setConcurrency(1);

        // 死信队列处理器
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        // FixedBackOff: 固定间隔重试, maxAttempts=3 后进入死信
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(1000L, 3L)
        );
        // 忽略的异常 (业务异常直接进死信, 不重试)
        errorHandler.addNotRetryableExceptions(
                IllegalArgumentException.class,
                IllegalStateException.class
        );
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }
}