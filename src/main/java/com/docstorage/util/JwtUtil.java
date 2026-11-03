package com.docstorage.util;

import com.docstorage.exception.AuthException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具类
 */
@Slf4j
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expire-minutes}")
    private int expireMinutes;

    @Value("${jwt.prefix}")
    private String prefix;

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成Token
     */
    public String generate(Long userId, String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", userId);
        claims.put("username", username);

        long now = System.currentTimeMillis();
        long expire = now + (long) expireMinutes * 60 * 1000;

        return Jwts.builder()
                .claims(claims)
                .issuedAt(new Date(now))
                .expiration(new Date(expire))
                .signWith(getKey())
                .compact();
    }

    /**
     * 解析Token
     */
    public Claims parse(String token) {
        // 去掉 Bearer 前缀
        if (token.startsWith(prefix)) {
            token = token.substring(prefix.length());
        }
        try {
            return Jwts.parser()
                    .verifyWith(getKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.warn("JWT parse failed: {}", e.getMessage());
            throw new AuthException(401, "Token无效或已过期");
        }
    }

    /**
     * 从Token中获取用户ID
     */
    public Long getUserId(String token) {
        Claims claims = parse(token);
        return claims.get("uid", Long.class);
    }

    /**
     * 从Token中获取用户名
     */
    public String getUsername(String token) {
        Claims claims = parse(token);
        return claims.get("username", String.class);
    }

    public int getExpireMinutes() {
        return expireMinutes;
    }
}