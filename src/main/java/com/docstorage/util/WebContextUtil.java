package com.docstorage.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Web 请求上下文工具
 */
public class WebContextUtil {

    private static final ThreadLocal<Context> CONTEXT = new ThreadLocal<>();

    public static void set(Long userId, HttpServletRequest request) {
        CONTEXT.set(new Context(userId, request));
    }

    public static Long getUserId() {
        Context ctx = CONTEXT.get();
        return ctx != null ? ctx.userId : null;
    }

    public static HttpServletRequest getRequest() {
        Context ctx = CONTEXT.get();
        return ctx != null ? ctx.request : null;
    }

    public static String getClientIp() {
        HttpServletRequest req = getRequest();
        if (req == null) return "unknown";

        String[] headers = {"X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"};
        for (String header : headers) {
            String ip = req.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
        }
        return req.getRemoteAddr();
    }

    public static String getUserAgent() {
        HttpServletRequest req = getRequest();
        return req != null ? req.getHeader("User-Agent") : null;
    }

    public static void clear() {
        CONTEXT.remove();
    }

    private record Context(Long userId, HttpServletRequest request) {}
}