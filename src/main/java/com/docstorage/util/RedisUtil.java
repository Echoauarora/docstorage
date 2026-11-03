package com.docstorage.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Redis 操作工具类
 * <p>
 * 统一封装常用操作, 底层使用 RedisTemplate<String, Object>
 * 面试考点 - 缓存击穿处理:
 * 方案一 (推荐): 互斥锁 + 逻辑过期 (热点数据永不过期 + 异步刷新)
 * 方案二: 永不过期 (适合非实时数据, 定时任务刷新)
 * 方案三: 热点数据永不过期 + 布隆过滤器 (防穿透)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisUtil {

    private final RedisTemplate<String, Object> redisTemplate;

    // Lua脚本: 原子性设置nx (分布式锁)
    private static final DefaultRedisScript<Boolean> SETNX_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('setnx', KEYS[1], ARGV[1]) == 1 then " +
            "   redis.call('expire', KEYS[1], ARGV[2]) " +
            "   return 1 " +
            "else return 0 end",
            Boolean.class
    );

    // Lua脚本: 原子性检查并删除 (避免先get再del的竞态)
    private static final DefaultRedisScript<Long> DEL_IF_EXISTS_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('exists', KEYS[1]) == 1 then " +
            "   return redis.call('del', KEYS[1]) " +
            "else return 0 end",
            Long.class
    );

    public void set(String key, Object value, long expireSeconds) {
        redisTemplate.opsForValue().set(key, value, expireSeconds, TimeUnit.SECONDS);
    }

    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) redisTemplate.opsForValue().get(key);
    }

    public Boolean delete(String key) {
        return redisTemplate.delete(key);
    }

    /**
     * 原子性删除 (Lua脚本, 避免竞态)
     */
    public Long deleteIfExists(String key) {
        return redisTemplate.execute(DEL_IF_EXISTS_SCRIPT, Collections.singletonList(key));
    }

    public Boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }

    /**
     * 分布式锁 (SETNX + 过期, 原子性)
     * @return true = 获取锁成功
     */
    public boolean tryLock(String lockKey, String requestId, long expireSeconds) {
        Boolean result = redisTemplate.execute(SETNX_SCRIPT,
                Collections.singletonList(lockKey), requestId, String.valueOf(expireSeconds));
        return Boolean.TRUE.equals(result);
    }

    /**
     * 删除分布式锁 (业务处理完后释放)
     */
    public void releaseLock(String lockKey) {
        redisTemplate.delete(lockKey);
    }

    /**
     * Hash操作 - 分片上传状态记录
     */
    public void hSet(String key, String field, Object value) {
        redisTemplate.opsForHash().put(key, field, value);
    }

    public Object hGet(String key, String field) {
        return redisTemplate.opsForHash().get(key, field);
    }

    public java.util.Map<Object, Object> hGetAll(String key) {
        return redisTemplate.opsForHash().entries(key);
    }

    public Long hDelete(String key, Object... fields) {
        return redisTemplate.opsForHash().delete(key, fields);
    }

    /**
     * 模糊删除 (权限缓存主动失效时批量清理)
     * 面试点: 权限变更后需要主动清理缓存, 而不是等缓存自然过期
     */
    public void deleteByPattern(String pattern) {
        var keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("Deleted {} keys by pattern: {}", keys.size(), pattern);
        }
    }

    /**
     * 设置过期时间
     */
    public Boolean expire(String key, long seconds) {
        return redisTemplate.expire(key, seconds, TimeUnit.SECONDS);
    }

    /**
     * 缓存Key前缀常量
     */
    public static final String KEY_PERMISSION = "doc:perm:user:%s:file:%s";
    public static final String KEY_USER_INFO = "doc:user:info:%s";
    public static final String KEY_UPLOAD_STATE = "doc:upload:state:%s";
    public static final String KEY_MD5_EXIST = "doc:file:md5:%s";
    public static final String KEY_IDEMPOTENT = "doc:idempotent:%s";
}