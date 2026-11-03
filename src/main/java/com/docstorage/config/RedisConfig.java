package com.docstorage.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置类
 * <p>
 * 序列化策略:
 * - Key 使用 String 序列化 (可读性好, 便于在Redis CLI调试)
 * - Value 使用 Jackson2Json 序列化 (支持泛型, 可读性较好)
 * <p>
 * 坑点: 不要使用默认的 JdkSerializationRedisSerializer, 二进制格式不可读且容易引起 ClassCastException
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Jackson2Json 序列化器配置
        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        // 允许序列化多态类型, 写入 @class 字段, 反序列化时自动匹配类型
        om.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL);

        Jackson2JsonRedisSerializer<Object> jsonSerializer = new Jackson2JsonRedisSerializer<>(om, Object.class);
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        // Key: String序列化 (可读性好)
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        // Value: JSON序列化 (支持对象存储)
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}