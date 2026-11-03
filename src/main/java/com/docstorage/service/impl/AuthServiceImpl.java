package com.docstorage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.docstorage.dto.req.LoginReq;
import com.docstorage.dto.res.LoginRes;
import com.docstorage.entity.SysUser;
import com.docstorage.exception.AuthException;
import com.docstorage.exception.BizException;
import com.docstorage.mapper.SysUserMapper;
import com.docstorage.service.AuthService;
import com.docstorage.util.JwtUtil;
import com.docstorage.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 认证服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;

    @Value("${cache.expire.user}")
    private long userCacheExpire;

    @Override
    public LoginRes login(LoginReq req, String clientIp, String userAgent) {
        // 1. 根据用户名查询用户
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, req.getUsername())
        );
        if (user == null || user.getDeleted() == 1) {
            throw new AuthException(401, "用户不存在");
        }

        // 2. BCrypt 密码校验
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        if (!encoder.matches(req.getPassword(), user.getPassword())) {
            log.warn("Login failed (wrong password): username={}, ip={}", req.getUsername(), clientIp);
            throw new AuthException(401, "密码错误");
        }

        // 3. 状态检查
        if (user.getStatus() != 1) {
            throw new AuthException(403, "账号已禁用");
        }

        // 4. 生成 JWT Token
        String token = jwtUtil.generate(user.getId(), user.getUsername());

        // 5. 更新最后登录信息
        user.setLastLoginTime(LocalDateTime.now());
        user.setLastLoginIp(clientIp);
        userMapper.updateById(user);

        // 6. 预热用户缓存
        String userCacheKey = String.format(RedisUtil.KEY_USER_INFO, user.getId());
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("userId", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("nickname", user.getNickname());
        redisUtil.set(userCacheKey, userInfo, userCacheExpire);

        log.info("Login success: userId={}, username={}, ip={}", user.getId(), user.getUsername(), clientIp);

        return LoginRes.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .token(token)
                .expireMinutes((long) jwtUtil.getExpireMinutes())
                .build();
    }
}