package com.anitrack.starter.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserInfoResponse {
    private Long id;
    private String username;
    private String nickname;
    private String avatarUrl;
}
