package com.anitrack.domain.review.exception;

import com.anitrack.domain.common.AnitrackDomainException;

public class IllegalReviewScoreException extends AnitrackDomainException {

    public IllegalReviewScoreException(String message) {
        super(message);
    }
}
