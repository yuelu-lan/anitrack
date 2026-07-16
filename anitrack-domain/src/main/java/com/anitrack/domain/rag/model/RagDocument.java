package com.anitrack.domain.rag.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RagDocument {
    private final String pageContent;
    private final String animeId;
    private final String title;

    public static RagDocument of(String pageContent, String animeId, String title) {
        return new RagDocument(pageContent, animeId, title);
    }
}
