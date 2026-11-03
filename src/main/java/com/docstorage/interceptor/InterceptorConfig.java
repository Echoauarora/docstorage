package com.docstorage.interceptor;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 拦截器注册配置
 * <p>
 * 注册顺序决定执行顺序:
 * 1. AuthInterceptor - 先认证
 * 2. PermissionInterceptor - 再鉴权
 * 3. IdempotentInterceptor - 最后校验幂等
 */
@Configuration
@RequiredArgsConstructor
public class InterceptorConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final PermissionInterceptor permissionInterceptor;
    private final IdempotentInterceptor idempotentInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. 认证拦截器
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/auth/**",
                        "/doc.html",
                        "/webjars/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/knife4j/**",
                        "/favicon.ico",
                        "/error"
                );

        // 2. 权限拦截器
        registry.addInterceptor(permissionInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/auth/**",
                        "/doc.html",
                        "/webjars/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/knife4j/**",
                        "/favicon.ico",
                        "/error"
                );

        // 3. 幂等拦截器 (只拦截写请求)
        registry.addInterceptor(idempotentInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/auth/**",
                        "/doc.html",
                        "/webjars/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/knife4j/**",
                        "/favicon.ico",
                        "/error"
                );
    }
}