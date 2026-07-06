package com.anitrack.application.model;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ReviewWithUserViewBO {

    private Long id;
    private Long userId;
    private String userNickname;
    private String userAvatarUrl;
    private Integer score;
    private String content;
    private LocalDateTime createTime;
}
