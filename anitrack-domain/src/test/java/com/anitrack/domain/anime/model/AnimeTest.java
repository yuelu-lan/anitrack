package com.anitrack.domain.anime.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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

    @Test
    void fromBangumi_full_fields_builds_anime() {
        var rating = Rating.of(8.5, 12, 102, Map.of("10", 100));
        var collection = Collection.of(50, 200, 10, 5, 3);
        var tags = List.of(AnimeTag.of("科幻", 80));
        var images = AnimeImages.of("L", "C", "M", "S", "G");
        var infobox = List.of(Infobox.ofText("中文名", "代号"));

        Anime anime = Anime.fromBangumi(
                1L, 2, "原名", "中文名", "summary", false, false, false,
                LocalDate.of(2024, 4, 1), "TV", images, 12, 12, 0,
                List.of("科幻"), tags, rating, collection, infobox);

        assertThat(anime.getBangumiId()).isEqualTo(1L);
        assertThat(anime.getType()).isEqualTo(2);
        assertThat(anime.getRating().getScore()).isEqualTo(8.5);
        assertThat(anime.getTags()).hasSize(1);
        assertThat(anime.getImages().getLarge()).isEqualTo("L");
        assertThat(anime.getInfobox()).hasSize(1);
    }

    @Test
    void reconstitute_full_fields_builds_anime() {
        Anime anime = Anime.reconstitute(
                10L, 1L, 2, "原名", "中文名", "summary", false, false, false,
                LocalDate.of(2024, 4, 1), "TV", null, 12, 12, 0,
                List.of(), List.of(), Rating.empty(), Collection.empty(), List.of());
        assertThat(anime.getId()).isEqualTo(10L);
        assertThat(anime.getRating().getTotal()).isZero();
    }
}
