package com.anitrack.application.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserRegisterBO {
    private String username;
    private String password;
    private String nickname;
}
