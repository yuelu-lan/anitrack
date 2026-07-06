package com.anitrack.starter.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ReviewResponse {

    private Long id;
    private Long animeId;
    private Integer score;
    private String content;
    private LocalDateTime createTime;
}
