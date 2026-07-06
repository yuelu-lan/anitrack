package com.anitrack.domain.watchlist.exception;

import com.anitrack.domain.common.AnitrackDomainException;

public class WatchlistItemAlreadyExistsException extends AnitrackDomainException {

    public WatchlistItemAlreadyExistsException(Long userId, Long animeId) {
        super("用户" + userId + "已经追番" + animeId);
    }
}
