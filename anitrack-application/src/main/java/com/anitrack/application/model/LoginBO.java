package com.anitrack.application.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginBO {
    private final UserBO user;
    private final String token;
}
