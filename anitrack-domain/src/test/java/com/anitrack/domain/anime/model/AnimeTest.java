package com.anitrack.domain.anime.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AnimeTest {

    @Test
    void fromBangumi_whenCalled_shouldCreateAnimeWithoutLocalId() {
        // when
        Anime anime = Anime.fromBangumi(100L, "中文名", "Original Title", "http://cover.jpg", 12,
            LocalDate.of(2024, 1, 1), "简介");

        // then
        assertThat(anime.getId()).isNull();
        assertThat(anime.getBangumiId()).isEqualTo(100L);
        assertThat(anime.getTitleCn()).isEqualTo("中文名");
        assertThat(anime.getTitleOriginal()).isEqualTo("Original Title");
        assertThat(anime.getCoverUrl()).isEqualTo("http://cover.jpg");
        assertThat(anime.getTotalEpisodes()).isEqualTo(12);
        assertThat(anime.getAirDate()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(anime.getSummary()).isEqualTo("简介");
    }

    @Test
    void reconstitute_whenCalled_shouldCreateAnimeWithLocalId() {
        // when
        Anime anime = Anime.reconstitute(1L, 100L, "中文名", "Original Title", "http://cover.jpg", 12,
            LocalDate.of(2024, 1, 1), "简介");

        // then
        assertThat(anime.getId()).isEqualTo(1L);
        assertThat(anime.getBangumiId()).isEqualTo(100L);
        assertThat(anime.getTitleCn()).isEqualTo("中文名");
    }
}
