package com.anitrack.domain.anime.exception;

import com.anitrack.domain.common.AnitrackDomainException;

public class BangumiApiException extends AnitrackDomainException {

    public BangumiApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
