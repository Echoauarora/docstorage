package com.docstorage.interceptor;

import com.docstorage.exception.AuthException;
import com.docstorage.mapper.SysUserMapper;
import com.docstorage.util.RedisUtil;
import com.docstorage.util.WebContextUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 系统权限拦截器
 * <p>
 * 职责: 检查用户是否拥有某接口所需的系统级权限编码
 * 缓存策略: 用户权限集合缓存30分钟, 权限变更时主动清理
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionInterceptor implements HandlerInterceptor {

    private final SysUserMapper userMapper;
    private final RedisUtil redisUtil;

    @Value("${cache.expire.user}")
    private long userCacheExpire;

    @Override
    public boolean preHandle(jakarta.servlet.http.HttpServletRequest request,
                             jakarta.servlet.http.HttpServletResponse response,
                             Object handler) {
        Long userId = WebContextUtil.getUserId();
        if (userId == null) {
            throw new AuthException(401, "未登录");
        }

        // 管理员放行
        if (userId == 1L) {
            return true;
        }

        // 从缓存获取用户权限集合
        String cacheKey = String.format(RedisUtil.KEY_USER_INFO, userId);
        Set<String> permCodes = redisUtil.get(cacheKey);

        if (permCodes == null) {
            // 缓存未命中, 查DB
            log.info("Permission cache miss, userId={}", userId);
            permCodes = userMapper.selectPermCodesByUserId(userId);
            if (permCodes != null && !permCodes.isEmpty()) {
                redisUtil.set(cacheKey, permCodes, userCacheExpire);
            }
        }

        // 路由规则 -> 权限编码映射
        String uri = request.getRequestURI();
        String method = request.getMethod();
        String requiredPerm = mapUriToPerm(uri, method);

        if (requiredPerm != null && permCodes != null && !permCodes.contains(requiredPerm)) {
            log.warn("Permission denied: userId={}, require={}", userId, requiredPerm);
            throw new AuthException(403, "没有权限执行此操作: " + requiredPerm);
        }

        return true;
    }

    /**
     * URI + HTTP方法 -> 权限编码映射
     * 简化版: 实际项目可通过注解 + 切面实现
     */
    private String mapUriToPerm(String uri, String method) {
        if (uri.startsWith("/file") || uri.startsWith("/doc")) {
            return switch (method.toUpperCase()) {
                case "GET", "HEAD" -> "file:view";
                case "POST" -> "file:upload";
                case "PUT", "PATCH" -> "file:edit";
                case "DELETE" -> "file:delete";
                default -> null;
            };
        }
        return null;
    }
}