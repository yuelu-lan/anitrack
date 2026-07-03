package com.anitrack.starter.config;

import com.anitrack.application.exception.AnitrackAppException;
import com.anitrack.domain.common.AnitrackDomainException;
import com.anitrack.starter.response.ResponseResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AnitrackAppException.class)
    public ResponseResult<Void> handleAppException(AnitrackAppException e) {
        log.warn("应用层异常: code={}, message={}", e.getCode(), e.getMessage());
        return ResponseResult.fail(e.getMessage());
    }

    @ExceptionHandler(AnitrackDomainException.class)
    public ResponseResult<Void> handleDomainException(AnitrackDomainException e) {
        log.warn("领域层异常: message={}", e.getMessage());
        return ResponseResult.fail(e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseResult<Void> handleValidationException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError == null ? "参数校验失败" : fieldError.getDefaultMessage();
        return ResponseResult.fail(message);
    }

    @ExceptionHandler(Exception.class)
    public ResponseResult<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return ResponseResult.fail("系统异常，请稍后重试");
    }
}
