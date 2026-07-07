package com.anitrack.application.converter;

import com.anitrack.application.model.AnimeBO;
import com.anitrack.domain.anime.model.Anime;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AnimeConverterTest {

    private final AnimeConverter converter = new AnimeConverterImpl();

    @Test
    void anime2BO_shouldMapAllFields() {
        Anime anime = Anime.reconstitute(1L, 100L, "中文名", "Original", "http://cover.jpg", 12,
            LocalDate.of(2024, 1, 1), "简介");
        AnimeBO bo = converter.anime2BO(anime);
        assertThat(bo.getId()).isEqualTo(1L);
        assertThat(bo.getBangumiId()).isEqualTo(100L);
        assertThat(bo.getTitleCn()).isEqualTo("中文名");
        assertThat(bo.getTotalEpisodes()).isEqualTo(12);
        assertThat(bo.getSummary()).isEqualTo("简介");
    }
}
