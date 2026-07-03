package com.anitrack.starter.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResponse {
    private String token;
    private UserInfoResponse userInfo;
}
