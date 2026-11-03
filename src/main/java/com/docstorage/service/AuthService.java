package com.docstorage.service;

import com.docstorage.dto.req.LoginReq;
import com.docstorage.dto.res.LoginRes;

/**
 * 认证服务接口
 */
public interface AuthService {

    LoginRes login(LoginReq req, String clientIp, String userAgent);
}