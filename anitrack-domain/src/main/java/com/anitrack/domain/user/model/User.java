package com.anitrack.domain.user.model;

import com.anitrack.domain.user.enums.UserRole;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class User {

    private Long id;
    private String username;
    private String passwordHash;
    private String nickname;
    private String avatarUrl;
    private UserRole role;

    public static User register(String username, String passwordHash, String nickname) {
        return User.builder()
            .username(username)
            .passwordHash(passwordHash)
            .nickname(nickname)
            .role(UserRole.USER)
            .build();
    }
}
