package com.anitrack.starter.controller;

import com.anitrack.application.service.RagApplication;
import com.anitrack.domain.rag.model.RagDocumentSummary;
import com.anitrack.infra.auth.JwtTokenProvider;
import com.anitrack.starter.converter.HttpConverter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RagIngestController.class)
class RagIngestControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean RagApplication ragApplication;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean HttpConverter httpConverter;

    @Test
    void documents_returns_list() throws Exception {
        when(jwtTokenProvider.validateToken(any())).thenReturn(true);
        when(jwtTokenProvider.getUserId(any())).thenReturn(1L);
        when(ragApplication.listDocuments()).thenReturn(List.of(
                RagDocumentSummary.of(1L, "标题A", "原A", "2024-04-01", 8.5, 100, 12),
                RagDocumentSummary.of(2L, "标题B", null, null, null, null, null)));

        mockMvc.perform(get("/api/rag/documents")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(1))
                .andExpect(jsonPath("$.data[0].animeId").value(1))
                .andExpect(jsonPath("$.data[0].title").value("标题A"))
                .andExpect(jsonPath("$.data[0].originalName").value("原A"))
                .andExpect(jsonPath("$.data[0].airDate").value("2024-04-01"))
                .andExpect(jsonPath("$.data[0].score").value(8.5))
                .andExpect(jsonPath("$.data[0].ratingTotal").value(100))
                .andExpect(jsonPath("$.data[0].totalEpisodes").value(12))
                .andExpect(jsonPath("$.data[1].animeId").value(2));
    }

    @Test
    void documents_returns_401_without_jwt() throws Exception {
        mockMvc.perform(get("/api/rag/documents"))
                .andExpect(status().isUnauthorized());
    }
}
