package com.anitrack.domain.anime.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Anime {

    private Long id;
    private Long bangumiId;
    private String titleCn;
    private String titleOriginal;
    private String coverUrl;
    private Integer totalEpisodes;
    private LocalDate airDate;
    private String summary;

    public static Anime fromBangumi(Long bangumiId, String titleCn, String titleOriginal, String coverUrl,
                                     Integer totalEpisodes, LocalDate airDate, String summary) {
        return Anime.builder()
            .bangumiId(bangumiId)
            .titleCn(titleCn)
            .titleOriginal(titleOriginal)
            .coverUrl(coverUrl)
            .totalEpisodes(totalEpisodes)
            .airDate(airDate)
            .summary(summary)
            .build();
    }

    public static Anime reconstitute(Long id, Long bangumiId, String titleCn, String titleOriginal, String coverUrl,
                                      Integer totalEpisodes, LocalDate airDate, String summary) {
        return Anime.builder()
            .id(id)
            .bangumiId(bangumiId)
            .titleCn(titleCn)
            .titleOriginal(titleOriginal)
            .coverUrl(coverUrl)
            .totalEpisodes(totalEpisodes)
            .airDate(airDate)
            .summary(summary)
            .build();
    }
}
