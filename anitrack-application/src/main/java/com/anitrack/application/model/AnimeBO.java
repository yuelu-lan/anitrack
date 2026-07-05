package com.anitrack.application.model;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class AnimeBO {

    private Long id;
    private Long bangumiId;
    private String titleCn;
    private String titleOriginal;
    private String coverUrl;
    private Integer totalEpisodes;
    private LocalDate airDate;
    private String summary;
}
