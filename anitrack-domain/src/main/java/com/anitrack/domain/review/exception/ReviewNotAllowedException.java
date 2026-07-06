package com.anitrack.domain.review.exception;

import com.anitrack.domain.common.AnitrackDomainException;

public class ReviewNotAllowedException extends AnitrackDomainException {

    public ReviewNotAllowedException(Long userId, Long animeId) {
        super("用户" + userId + "尚未看完番剧" + animeId + "，不能评价");
    }
}
