package com.anitrack.application.model;

import com.anitrack.domain.user.enums.UserRole;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserBO {
    private Long id;
    private String username;
    private String nickname;
    private String avatarUrl;
    private UserRole role;
}
