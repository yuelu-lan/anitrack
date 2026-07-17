package com.anitrack.starter.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RagDocumentSummaryResponse {
    private final Long animeId;
    private final String title;
}
