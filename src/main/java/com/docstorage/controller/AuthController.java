package com.docstorage.controller;

import com.docstorage.common.Result;
import com.docstorage.dto.req.LoginReq;
import com.docstorage.dto.res.LoginRes;
import com.docstorage.service.AuthService;
import com.docstorage.util.WebContextUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证接口
 */
@Tag(name = "认证管理")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<LoginRes> login(@Valid @RequestBody LoginReq req) {
        return Result.success(authService.login(req, WebContextUtil.getClientIp(), WebContextUtil.getUserAgent()));
    }

    @Operation(summary = "健康检查")
    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("OK");
    }
}