package com.anitrack.domain.common;

public class AnitrackDomainException extends RuntimeException {

    public AnitrackDomainException(String message) {
        super(message);
    }

    public AnitrackDomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
