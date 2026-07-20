package com.anitrack.domain.rag.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RagDocument {
    private final String pageContent;
    private final String animeId;
    private final String title;
    private final String originalName;
    private final String airDate;
    private final Double score;
    private final Integer ratingTotal;

    public static RagDocument of(String pageContent, String animeId, String title,
            String originalName, String airDate, Double score, Integer ratingTotal) {
        return new RagDocument(pageContent, animeId, title, originalName, airDate,
                score, ratingTotal);
    }

    public static RagDocument of(String pageContent, String animeId, String title) {
        return of(pageContent, animeId, title, null, null, null, null);
    }
}
