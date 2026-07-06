package com.anitrack.application.model;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ReviewWithAnimeViewBO {

    private Long id;
    private Long animeId;
    private String animeTitleCn;
    private String animeTitleOriginal;
    private String animeCoverUrl;
    private Integer score;
    private String content;
    private LocalDateTime createTime;
}
