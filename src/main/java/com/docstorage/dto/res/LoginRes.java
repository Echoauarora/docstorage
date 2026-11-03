package com.docstorage.dto.res;

import lombok.Builder;
import lombok.Data;

/**
 * 登录响应
 */
@Data
@Builder
public class LoginRes {

    private Long userId;
    private String username;
    private String nickname;
    private String token;
    private Long expireMinutes;
}