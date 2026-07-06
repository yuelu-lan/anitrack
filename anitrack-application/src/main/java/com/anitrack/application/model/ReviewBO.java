package com.anitrack.application.model;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ReviewBO {

    private Long id;
    private Long animeId;
    private Integer score;
    private String content;
    private LocalDateTime createTime;
}
