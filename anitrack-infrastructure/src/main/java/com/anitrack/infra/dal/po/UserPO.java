package com.anitrack.infra.dal.po;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserPO {

    private Long id;
    private String username;
    private String passwordHash;
    private String nickname;
    private String avatarUrl;
    private String role;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
