package com.docstorage.exception;

import lombok.Getter;

/**
 * 认证/授权异常 (401/403)
 */
@Getter
public class AuthException extends BizException {

    public AuthException(String message) {
        super(401, message);
    }

    public AuthException(Integer code, String message) {
        super(code, message);
    }
}