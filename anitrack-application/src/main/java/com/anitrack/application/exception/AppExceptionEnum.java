package com.anitrack.application.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AppExceptionEnum {

    USERNAME_ALREADY_EXISTS(40001, "用户名已存在"),
    LOGIN_FAILED(40002, "用户名或密码错误"),
    BANGUMI_SERVICE_UNAVAILABLE(40101, "番剧信息服务暂时不可用，请稍后重试"),
    ANIME_NOT_FOUND(40102, "番剧不存在"),
    WATCHLIST_ITEM_ALREADY_EXISTS(40201, "该番剧已在追番列表中"),
    WATCHLIST_ITEM_NOT_FOUND(40202, "追番记录不存在"),
    ILLEGAL_WATCH_STATUS_TRANSITION(40203, "非法的追番状态转移"),
    ILLEGAL_WATCH_PROGRESS(40204, "追番进度更新不合法"),
    REVIEW_NOT_ALLOWED(40301, "只有看完的番剧才能评价"),
    REVIEW_ALREADY_EXISTS(40302, "该番剧已评价，请使用修改接口"),
    REVIEW_NOT_FOUND(40303, "评价记录不存在"),
    ILLEGAL_REVIEW_SCORE(40304, "评分必须在1-10之间");

    private final int code;
    private final String message;
}
