package com.anitrack.application.exception;

import lombok.Getter;

@Getter
public class AnitrackAppException extends RuntimeException {

    private final int code;

    private AnitrackAppException(int code, String message) {
        super(message);
        this.code = code;
    }

    public static AnitrackAppException build(String messageFormat, Object... args) {
        return new AnitrackAppException(50000, String.format(messageFormat, args));
    }

    public static AnitrackAppException build(AppExceptionEnum exceptionEnum) {
        return new AnitrackAppException(exceptionEnum.getCode(), exceptionEnum.getMessage());
    }
}
