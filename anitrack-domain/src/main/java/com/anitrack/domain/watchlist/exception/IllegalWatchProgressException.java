package com.anitrack.domain.watchlist.exception;

import com.anitrack.domain.common.AnitrackDomainException;

public class IllegalWatchProgressException extends AnitrackDomainException {

    public IllegalWatchProgressException(String message) {
        super(message);
    }
}
