package com.anitrack.infra.gateway.bangumi;

import com.anitrack.domain.anime.model.Anime;
import com.anitrack.infra.gateway.bangumi.dto.BangumiImagesDTO;
import com.anitrack.infra.gateway.bangumi.dto.BangumiSubjectDTO;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class BangumiConverterTest {

    private final BangumiConverter sut = new BangumiConverter();

    @Test
    void toDomain_whenAllFieldsPresent_shouldMapAllFields() {
        // given
        BangumiSubjectDTO dto = new BangumiSubjectDTO();
        dto.setId(100L);
        dto.setName("Original Title");
        dto.setNameCn("中文名");
        dto.setSummary("简介");
        dto.setDate("2024-01-01");
        dto.setEps(12);
        dto.setTotalEpisodes(10);
        BangumiImagesDTO images = new BangumiImagesDTO();
        images.setLarge("http://cover.jpg");
        dto.setImages(images);

        // when
        Anime anime = sut.toDomain(dto);

        // then
        assertThat(anime.getBangumiId()).isEqualTo(100L);
        assertThat(anime.getTitleOriginal()).isEqualTo("Original Title");
        assertThat(anime.getTitleCn()).isEqualTo("中文名");
        assertThat(anime.getSummary()).isEqualTo("简介");
        assertThat(anime.getAirDate()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(anime.getTotalEpisodes()).isEqualTo(12);
        assertThat(anime.getCoverUrl()).isEqualTo("http://cover.jpg");
    }

    @Test
    void toDomain_whenDateIsMissing_shouldMapAirDateAsNull() {
        // given
        BangumiSubjectDTO dto = new BangumiSubjectDTO();
        dto.setId(100L);
        dto.setName("Original Title");
        dto.setNameCn("");
        dto.setEps(0);
        dto.setDate(null);

        // when
        Anime anime = sut.toDomain(dto);

        // then
        assertThat(anime.getAirDate()).isNull();
        assertThat(anime.getTitleCn()).isEmpty();
        assertThat(anime.getTotalEpisodes()).isZero();
    }

    @Test
    void toDomain_whenImagesIsNull_shouldMapCoverUrlAsNull() {
        // given
        BangumiSubjectDTO dto = new BangumiSubjectDTO();
        dto.setId(100L);
        dto.setName("Original Title");
        dto.setImages(null);

        // when
        Anime anime = sut.toDomain(dto);

        // then
        assertThat(anime.getCoverUrl()).isNull();
    }
}
