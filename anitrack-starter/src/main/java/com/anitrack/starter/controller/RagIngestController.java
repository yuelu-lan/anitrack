package com.anitrack.starter.controller;

import com.anitrack.application.service.RagApplication;
import com.anitrack.domain.rag.model.RagDocumentSummary;
import com.anitrack.starter.response.RagDocumentSummaryResponse;
import com.anitrack.starter.response.ResponseResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagIngestController {

    private final RagApplication ragApplication;

    @PostMapping("/ingest")
    public ResponseResult<Integer> ingest(@RequestParam List<Long> bangumiIds) {
        return ResponseResult.success(ragApplication.ingestAnimeWiki(bangumiIds));
    }

    @PostMapping("/ingest_by_criteria")
    public ResponseResult<Integer> ingestByCriteria(@RequestParam int year, @RequestParam double minRating) {
        return ResponseResult.success(ragApplication.ingestByCriteria(year, minRating));
    }

    @GetMapping("/documents")
    public ResponseResult<List<RagDocumentSummaryResponse>> documents() {
        List<RagDocumentSummaryResponse> list = ragApplication.listDocuments().stream()
                .map(s -> new RagDocumentSummaryResponse(s.getAnimeId(), s.getTitle()))
                .toList();
        return ResponseResult.success(list);
    }
}
