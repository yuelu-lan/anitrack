package com.anitrack.domain.watchlist.enums;

import lombok.Getter;

@Getter
public enum WatchStatus {
    WANT_TO_WATCH(1),
    WATCHING(2),
    WATCHED(3),
    DROPPED(4);

    private final Integer code;

    WatchStatus(Integer code) {
        this.code = code;
    }
}
