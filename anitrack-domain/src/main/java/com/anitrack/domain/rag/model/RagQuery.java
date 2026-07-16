package com.anitrack.domain.rag.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RagQuery {
    private final String question;
    private final Integer topK;

    public static RagQuery of(String question, Integer topK) {
        return new RagQuery(question, topK);
    }
}
