package com.anitrack.domain.rag.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RagDocumentSummary {
    private final Long animeId;
    private final String title;

    public static RagDocumentSummary of(Long animeId, String title) {
        return new RagDocumentSummary(animeId, title);
    }
}
