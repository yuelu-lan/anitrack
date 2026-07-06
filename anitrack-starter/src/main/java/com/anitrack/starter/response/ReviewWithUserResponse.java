package com.anitrack.starter.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ReviewWithUserResponse {

    private Long id;
    private Long userId;
    private String userNickname;
    private String userAvatarUrl;
    private Integer score;
    private String content;
    private LocalDateTime createTime;
}
