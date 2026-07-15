package com.anitrack.application.service;

import com.anitrack.domain.anime.gateway.BangumiGateway;
import com.anitrack.domain.anime.model.*;
import com.anitrack.domain.rag.gateway.RagGateway;
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
        verify(rag).ingest(anyList());
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

    private Anime animeFixture() {
        return Anime.fromBangumi(1L, 2, "原名", "中文名", "简介", false, false, false,
                LocalDate.of(2024, 4, 1), "TV", null, 12, 12, 0,
                List.of(), List.of(AnimeTag.of("科幻", 10)),
                Rating.of(8.5, 1, 100, java.util.Map.of()), Collection.empty(), List.of());
    }
}
