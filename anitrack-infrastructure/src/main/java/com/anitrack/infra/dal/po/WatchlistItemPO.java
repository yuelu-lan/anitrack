package com.anitrack.infra.dal.po;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WatchlistItemPO {

    private Long id;
    private Long userId;
    private Long animeId;
    private String status;
    private Integer currentEpisode;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
