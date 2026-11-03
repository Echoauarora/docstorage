package com.docstorage.common;

import lombok.extern.slf4j.Slf4j;

import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.docstorage.exception.AuthException;
import com.docstorage.exception.BizException;
import com.docstorage.exception.IdempotentConflictException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * <p>
 * 职责: 将各类异常转换为统一 Result 格式返回
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常 (400~499)
     */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException e) {
        log.warn("[BizException] code={}, msg={}", e.getCode(), e.getMessage());
        Result<Void> result = Result.fail(e.getCode(), e.getMessage());
        return result;
    }

    /**
     * 认证异常 (401/403)
     */
    @ExceptionHandler(AuthException.class)
    public Result<Void> handleAuthException(AuthException e) {
        log.warn("[AuthException] code={}, msg={}", e.getCode(), e.getMessage());
        Result<Void> result = Result.fail(e.getCode(), e.getMessage());
        return result;
    }

    /**
     * 幂等冲突异常 (409)
     */
    @ExceptionHandler(IdempotentConflictException.class)
    public Result<String> handleIdempotentException(IdempotentConflictException e) {
        log.warn("[IdempotentConflict] msg={}, key冲突返回缓存结果", e.getMessage());
        Result<String> result = Result.conflict(e.getMessage());
        result.setData(e.getOriginalResponse());
        return result;
    }

    /**
     * 参数校验异常 (@Validated)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("[ValidException] {}", msg);
        return Result.badRequest(msg);
    }

    /**
     * 参数绑定异常
     */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return Result.badRequest(msg);
    }

    /**
     * 缺失请求参数
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return Result.badRequest("缺少参数: " + e.getParameterName());
    }

    /**
     * 文件上传大小超限
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        return Result.badRequest("文件大小超出限制");
    }

    /**
     * 兜底异常 (500)
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("[UnhandledException] type={}, msg={}", e.getClass().getSimpleName(), e.getMessage(), e);
        return Result.fail(500, "服务器内部错误: " + e.getClass().getSimpleName() + " - " + e.getMessage());
    }
}