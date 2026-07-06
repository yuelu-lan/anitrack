package com.anitrack.domain.watchlist.exception;

import com.anitrack.domain.common.AnitrackDomainException;

public class WatchlistItemNotFoundException extends AnitrackDomainException {

    public WatchlistItemNotFoundException(Long userId, Long animeId) {
        super("用户" + userId + "未追番" + animeId);
    }
}
