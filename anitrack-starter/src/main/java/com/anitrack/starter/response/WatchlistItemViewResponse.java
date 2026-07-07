package com.anitrack.starter.response;

import com.anitrack.starter.response.vo.EnumVO;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class WatchlistItemViewResponse {

    private Long id;
    private Long animeId;
    private String animeTitleCn;
    private String animeTitleOriginal;
    private String animeCoverUrl;
    private EnumVO status;
    private Integer currentEpisode;
    private LocalDateTime updateTime;
}
