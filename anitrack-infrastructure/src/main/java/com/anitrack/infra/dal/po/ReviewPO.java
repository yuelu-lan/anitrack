package com.anitrack.infra.dal.po;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReviewPO {

    private Long id;
    private Long userId;
    private Long animeId;
    private Integer score;
    private String content;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
