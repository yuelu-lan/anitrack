package com.anitrack.application.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AppExceptionEnum {

    USERNAME_ALREADY_EXISTS(40001, "用户名已存在"),
    LOGIN_FAILED(40002, "用户名或密码错误"),
    BANGUMI_SERVICE_UNAVAILABLE(40101, "番剧信息服务暂时不可用，请稍后重试"),
    ANIME_NOT_FOUND(40102, "番剧不存在");

    private final int code;
    private final String message;
}
