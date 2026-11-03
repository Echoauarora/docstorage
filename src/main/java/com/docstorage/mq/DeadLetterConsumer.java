package com.docstorage.mq;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka 死信队列消费者
 * <p>
 * 所有消费失败 3 次的消息会进入此队列
 * 这里只做日志记录, 生产环境应接入告警系统或人工处理平台
 */
@Slf4j
@Component
public class DeadLetterConsumer {

    @KafkaListener(topics = "${spring.kafka.topics.dead-letter}",
            groupId = "dead-letter-monitor")
    public void consume(ConsumerRecord<String, String> record) {
        log.error("DEAD LETTER QUEUE - message: topic={}, partition={}, offset={}, key={}, value={}",
                record.topic(), record.partition(), record.offset(), record.key(), record.value());
        // TODO: 接入企业微信/钉钉告警, 通知运维人员
    }
}