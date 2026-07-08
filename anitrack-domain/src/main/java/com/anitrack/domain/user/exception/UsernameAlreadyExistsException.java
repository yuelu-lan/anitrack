package com.anitrack.domain.user.exception;

import com.anitrack.domain.common.AnitrackDomainException;

public class UsernameAlreadyExistsException extends AnitrackDomainException {

    public UsernameAlreadyExistsException(String username) {
        super("用户名 " + username + " 已存在");
    }
}
