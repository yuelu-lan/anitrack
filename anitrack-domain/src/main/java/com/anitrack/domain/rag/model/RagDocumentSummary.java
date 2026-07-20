package com.anitrack.domain.rag.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RagDocumentSummary {
    private final Long animeId;
    private final String title;
    private final String originalName;
    private final String airDate;
    private final Double score;
    private final Integer ratingTotal;
    private final Integer totalEpisodes;

    public static RagDocumentSummary of(Long animeId, String title, String originalName,
            String airDate, Double score, Integer ratingTotal, Integer totalEpisodes) {
        return new RagDocumentSummary(animeId, title, originalName, airDate,
                score, ratingTotal, totalEpisodes);
    }

    public static RagDocumentSummary of(Long animeId, String title) {
        return of(animeId, title, null, null, null, null, null);
    }
}
