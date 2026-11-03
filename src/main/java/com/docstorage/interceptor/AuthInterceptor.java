package com.docstorage.interceptor;

import com.docstorage.exception.AuthException;
import com.docstorage.util.JwtUtil;
import com.docstorage.util.WebContextUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 认证拦截器
 * <p>
 * 职责: 校验请求头中的 JWT Token, 解析用户ID并放入 ThreadLocal
 * 放行路径: 登录接口、健康检查
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 放行 OPTIONS (CORS预检)
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = request.getHeader("Authorization");
        if (token == null || token.isEmpty()) {
            throw new AuthException(401, "未登录");
        }

        try {
            Long userId = jwtUtil.getUserId(token);
            WebContextUtil.set(userId, request);
            log.debug("AuthInterceptor: userId={}, uri={}", userId, request.getRequestURI());
            return true;
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Auth error: {}", e.getMessage());
            throw new AuthException(401, "Token无效");
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 清理 ThreadLocal, 防止内存泄漏
        WebContextUtil.clear();
    }
}