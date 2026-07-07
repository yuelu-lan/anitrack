package com.anitrack.starter.response;

import com.anitrack.starter.response.vo.EnumVO;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class WatchlistItemResponse {

    private Long id;
    private Long animeId;
    private EnumVO status;
    private Integer currentEpisode;
    private LocalDateTime updateTime;
}
