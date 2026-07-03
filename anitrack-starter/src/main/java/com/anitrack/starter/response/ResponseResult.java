package com.anitrack.starter.response;

import lombok.Getter;

@Getter
public class ResponseResult<T> {

    private final int status;
    private final String message;
    private final T data;

    private ResponseResult(int status, String message, T data) {
        this.status = status;
        this.message = message;
        this.data = data;
    }

    public static <T> ResponseResult<T> success(T data) {
        return new ResponseResult<>(1, null, data);
    }

    public static <T> ResponseResult<T> success() {
        return new ResponseResult<>(1, null, null);
    }

    public static <T> ResponseResult<T> fail(String message) {
        return new ResponseResult<>(0, message, null);
    }
}
