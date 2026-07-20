package com.anitrack.starter.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RagDocumentSummaryResponse {
    private final Long animeId;
    private final String title;
    private final String originalName;
    private final String airDate;
    private final Double score;
    private final Integer ratingTotal;
    private final Integer totalEpisodes;
}
