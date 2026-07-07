package com.anitrack.domain.anime.exception;

import com.anitrack.domain.common.AnitrackDomainException;

public class AnimeTotalEpisodesInvalidException extends AnitrackDomainException {

    public AnimeTotalEpisodesInvalidException(Long animeId) {
        super("番剧总集数无效: " + animeId);
    }
}
