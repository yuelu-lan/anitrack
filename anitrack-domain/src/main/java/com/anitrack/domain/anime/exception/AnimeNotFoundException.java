package com.anitrack.domain.anime.exception;

import com.anitrack.domain.common.AnitrackDomainException;

public class AnimeNotFoundException extends AnitrackDomainException {

    public AnimeNotFoundException(Long animeId) {
        super("番剧不存在: " + animeId);
    }
}
