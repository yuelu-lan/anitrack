package com.anitrack.application.exception;

import lombok.Getter;

@Getter
public class AnitrackAppException extends RuntimeException {

    private final int code;

    public AnitrackAppException(AppExceptionEnum exceptionEnum) {
        super(exceptionEnum.getMessage());
        this.code = exceptionEnum.getCode();
    }
}
