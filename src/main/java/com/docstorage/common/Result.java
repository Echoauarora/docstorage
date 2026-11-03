package com.docstorage.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一返回结果封装
 *
 * @param <T> 数据泛型
 */
@Data
public class Result<T> implements Serializable {

    private Integer code;
    private String message;
    private T data;
    private Long timestamp = System.currentTimeMillis();
    private String traceId;

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.setCode(200);
        r.setMessage("success");
        r.setData(data);
        return r;
    }

    public static <T> Result<T> fail(String message) {
        return fail(500, message);
    }

    public static <T> Result<T> fail(Integer code, String message) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMessage(message);
        return r;
    }

    public static <T> Result<T> badRequest(String message) {
        return fail(400, message);
    }

    public static <T> Result<T> unauthorized(String message) {
        return fail(401, message);
    }

    public static <T> Result<T> forbidden(String message) {
        return fail(403, message);
    }

    public static <T> Result<T> notFound(String message) {
        return fail(404, message);
    }

    public static <T> Result<T> conflict(String message) {
        return fail(409, message);
    }

    public Result<T> traceId(String traceId) {
        this.traceId = traceId;
        return this;
    }
}