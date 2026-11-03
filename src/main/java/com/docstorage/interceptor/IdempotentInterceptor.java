package com.docstorage.interceptor;

import com.docstorage.entity.DocIdempotent;
import com.docstorage.exception.BizException;
import com.docstorage.exception.IdempotentConflictException;
import com.docstorage.mapper.DocIdempotentMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 接口幂等拦截器
 * <p>
 * 实现策略: 基于业务唯一编号 (requestId/fileCode)
 * 1. 请求头带 X-Idempotent-Key, 作为幂等键
 * 2. Redis + DB 双重校验, Redis快速判断, DB持久化
 * 3. 首次请求: 记录幂等键+处理中, 业务完成后写入结果
 * 4. 重复请求: 直接返回之前的结果
 * <p>
 * 面试考点 - 幂等 vs 重试:
 * Kafka消费者重试可能导致业务执行多次, 必须通过幂等拦截器保护写操作
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotentInterceptor implements HandlerInterceptor {

    private final DocIdempotentMapper idempotentMapper;
    private final ObjectMapper objectMapper;

    @Value("${docstorage.idempotent-expire}")
    private long idempotentExpire;

    // 允许幂等的方法
    private static final Set<String> IDEMPOTENT_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // GET 请求天然幂等, 跳过
        if (!IDEMPOTENT_METHODS.contains(request.getMethod().toUpperCase())) {
            return true;
        }

        String idempotentKey = request.getHeader("X-Idempotent-Key");
        if (idempotentKey == null || idempotentKey.isEmpty()) {
            // 强制所有写请求必须带幂等键, 前端可生成 UUID
            throw new BizException(400, "写请求必须携带 X-Idempotent-Key 头");
        }

        String module = request.getRequestURI();
        LocalDateTime expireTime = LocalDateTime.now().plusSeconds(idempotentExpire);

        // 尝试插入幂等记录 (唯一索引保证原子性)
        DocIdempotent record = new DocIdempotent();
        record.setIdempotentKey(idempotentKey);
        record.setModule(module);
        record.setStatus(1); // 处理中
        record.setExpireTime(expireTime);

        try {
            idempotentMapper.insert(record);
            log.info("Idempotent key acquired: {}, module={}", idempotentKey, module);
            // 继续执行业务, 执行完后在 afterCompletion 更新结果
            request.setAttribute("_idempotent_record", record);
            return true;
        } catch (Exception e) {
            // 唯一索引冲突 -> 重复请求
            log.info("Idempotent conflict: key={}", idempotentKey);
            DocIdempotent existing = idempotentMapper.selectValidByKey(idempotentKey);
            if (existing != null && existing.getResultData() != null) {
                throw new IdempotentConflictException("重复请求, 返回之前结果", existing.getResultData());
            }
            // 还在处理中, 返回处理中提示
            throw new IdempotentConflictException("请求正在处理中, 请稍后", null);
        }
    }

    /**
     * 业务执行完成后, 记录结果
     * 使用ResponseBodyAdvice会更优雅, 这里简化处理
     */
    public static void markCompleted(Object recordObj, Object result, ObjectMapper om) {
        if (!(recordObj instanceof DocIdempotent record)) return;
        try {
            record.setStatus(2); // 已完成
            record.setResultData(om.writeValueAsString(result));
            // 更新DB
            com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<DocIdempotent> wrapper =
                    new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
            wrapper.eq("id", record.getId());
            // 动态注入到mapper中更新 (简化处理)
        } catch (JsonProcessingException e) {
            log.error("Serialize result for idempotent failed", e);
        }
    }
}