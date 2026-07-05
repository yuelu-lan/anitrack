package com.anitrack.starter.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class AnimeResponse {

    private Long id;
    private Long bangumiId;
    private String titleCn;
    private String titleOriginal;
    private String coverUrl;
    private Integer totalEpisodes;
    private LocalDate airDate;
    private String summary;
}
