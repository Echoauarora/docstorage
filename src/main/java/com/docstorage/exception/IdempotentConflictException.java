package com.docstorage.exception;

import lombok.Getter;

/**
 * 幂等冲突异常 (409)
 */
@Getter
public class IdempotentConflictException extends BizException {

    private final String originalResponse;

    public IdempotentConflictException(String message, String originalResponse) {
        super(409, message);
        this.originalResponse = originalResponse;
    }
}