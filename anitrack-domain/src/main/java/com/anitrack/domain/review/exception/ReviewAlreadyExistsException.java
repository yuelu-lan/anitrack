package com.anitrack.domain.review.exception;

import com.anitrack.domain.common.AnitrackDomainException;

public class ReviewAlreadyExistsException extends AnitrackDomainException {

    public ReviewAlreadyExistsException(Long userId, Long animeId) {
        super("用户" + userId + "已经评价过番剧" + animeId);
    }
}
