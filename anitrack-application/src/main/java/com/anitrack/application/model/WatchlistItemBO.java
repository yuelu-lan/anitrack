package com.anitrack.application.model;

import com.anitrack.domain.watchlist.enums.WatchStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class WatchlistItemBO {

    private Long id;
    private Long animeId;
    private WatchStatus status;
    private Integer currentEpisode;
    private LocalDateTime updateTime;
}
