package com.anitrack.starter.response;

import com.anitrack.domain.watchlist.enums.WatchStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class WatchlistItemResponse {

    private Long id;
    private Long animeId;
    private WatchStatus status;
    private Integer currentEpisode;
    private LocalDateTime updateTime;
}
