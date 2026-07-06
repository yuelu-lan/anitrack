package com.anitrack.application.model;

import com.anitrack.domain.watchlist.enums.WatchStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class WatchlistItemViewBO {

    private Long id;
    private Long animeId;
    private String animeTitleCn;
    private String animeTitleOriginal;
    private String animeCoverUrl;
    private WatchStatus status;
    private Integer currentEpisode;
    private LocalDateTime updateTime;
}
