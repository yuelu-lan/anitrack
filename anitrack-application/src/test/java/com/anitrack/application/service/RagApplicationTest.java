package com.anitrack.application.service;

import com.anitrack.domain.anime.gateway.BangumiGateway;
import com.anitrack.domain.anime.model.*;
import com.anitrack.domain.rag.gateway.RagGateway;
import com.anitrack.domain.rag.model.RagDocument;
import com.anitrack.domain.rag.model.RagDocumentSummary;
import com.anitrack.domain.rag.model.RagQuery;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RagApplicationTest {

    @Test
    void ingestAnimeWiki_pulls_and_ingests() {
        BangumiGateway bangumi = mock(BangumiGateway.class);
        RagGateway rag = mock(RagGateway.class);
        when(bangumi.getById(1L)).thenReturn(animeFixture());
        when(rag.ingest(anyList())).thenReturn(1);

        RagApplication app = new RagApplication(bangumi, rag);
        int n = app.ingestAnimeWiki(List.of(1L));

        assertThat(n).isEqualTo(1);
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<RagDocument>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(rag).ingest(captor.capture());
        List<RagDocument> docs = captor.getValue();
        assertThat(docs).hasSize(1);
        RagDocument d = docs.get(0);
        assertThat(d.getAnimeId()).isEqualTo("1");
        assertThat(d.getTitle()).isEqualTo("中文名");
        assertThat(d.getOriginalName()).isEqualTo("原名");
        assertThat(d.getAirDate()).isEqualTo("2024-04-01");
        assertThat(d.getScore()).isEqualTo(8.5);
        assertThat(d.getRatingTotal()).isEqualTo(100);
    }

    @Test
    void ingestAnimeWiki_skips_failed_fetch() {
        BangumiGateway bangumi = mock(BangumiGateway.class);
        RagGateway rag = mock(RagGateway.class);
        when(bangumi.getById(1L)).thenThrow(new RuntimeException("bangumi down"));
        when(bangumi.getById(2L)).thenReturn(animeFixture());
        when(rag.ingest(anyList())).thenReturn(1);

        RagApplication app = new RagApplication(bangumi, rag);
        int n = app.ingestAnimeWiki(List.of(1L, 2L));

        assertThat(n).isEqualTo(1);
        verify(rag).ingest(anyList());
        org.mockito.ArgumentCaptor<List> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(rag).ingest(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
    }

    @Test
    void streamChat_delegates_to_gateway() {
        BangumiGateway bangumi = mock(BangumiGateway.class);
        RagGateway rag = mock(RagGateway.class);
        when(rag.streamQuery(any())).thenReturn(Stream.of("你", "好"));

        RagApplication app = new RagApplication(bangumi, rag);
        List<String> tokens = app.streamChat(RagQuery.of("你好", null)).toList();

        assertThat(tokens).containsExactly("你", "好");
    }

    @Test
    void ingestByCriteria_uses_gateway_filter_and_ingests() {
        BangumiGateway bangumi = mock(BangumiGateway.class);
        RagGateway rag = mock(RagGateway.class);
        when(bangumi.listAnimeByYearRating(2026, 7.0)).thenReturn(List.of(animeFixture()));
        when(rag.ingest(anyList())).thenReturn(1);

        RagApplication app = new RagApplication(bangumi, rag);
        int n = app.ingestByCriteria(2026, 7.0);

        assertThat(n).isEqualTo(1);
        verify(bangumi).listAnimeByYearRating(2026, 7.0);
        verify(rag).ingest(anyList());
    }

    @Test
    void listDocuments_delegates_to_gateway() {
        BangumiGateway bangumi = mock(BangumiGateway.class);
        RagGateway rag = mock(RagGateway.class);
        when(rag.listDocuments()).thenReturn(List.of(RagDocumentSummary.of(1L, "标题A")));

        RagApplication app = new RagApplication(bangumi, rag);
        List<RagDocumentSummary> docs = app.listDocuments();

        assertThat(docs).hasSize(1);
        assertThat(docs.get(0).getAnimeId()).isEqualTo(1L);
        verify(rag).listDocuments();
    }

    private Anime animeFixture() {
        return Anime.fromBangumi(1L, 2, "原名", "中文名", "简介", false, false, false,
                LocalDate.of(2024, 4, 1), "TV", null, 12, 12, 0,
                List.of(), List.of(AnimeTag.of("科幻", 10)),
                Rating.of(8.5, 1, 100, java.util.Map.of()), Collection.empty(), List.of());
    }
}
