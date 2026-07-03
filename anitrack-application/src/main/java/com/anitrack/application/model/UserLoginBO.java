package com.anitrack.application.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserLoginBO {
    private String username;
    private String password;
}
