package com.docstorage.exception;

import lombok.Getter;

/**
 * 业务运行时异常
 * <p>
 * 全局异常处理器会捕获并转换为统一Result返回
 */
@Getter
public class BizException extends RuntimeException {

    private final Integer code;

    public BizException(String message) {
        super(message);
        this.code = 500;
    }

    public BizException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(Integer code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}