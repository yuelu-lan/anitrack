package com.anitrack.infra.gateway.bangumi;

import com.anitrack.domain.anime.model.Anime;
import com.anitrack.infra.gateway.bangumi.dto.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class BangumiConverterTest {

    private final BangumiConverter converter = new BangumiConverter();

    @Test
    void toDomain_maps_full_subject() {
        BangumiSubjectDTO dto = new BangumiSubjectDTO();
        dto.setId(1L);
        dto.setType(2);
        dto.setName("原名");
        dto.setNameCn("中文名");
        dto.setSummary("简介");
        dto.setDate("2024-04-01");
        dto.setNsfw(false);
        dto.setLocked(false);
        dto.setSeries(false);
        dto.setPlatform("TV");
        dto.setEps(12);
        dto.setTotalEpisodes(12);
        dto.setVolumes(0);
        dto.setMetaTags(List.of("科幻"));

        BangumiImagesDTO img = new BangumiImagesDTO();
        img.setLarge("L"); img.setCommon("C"); img.setMedium("M"); img.setSmall("S"); img.setGrid("G");
        dto.setImages(img);

        BangumiTagDTO tag = new BangumiTagDTO();
        tag.setName("科幻"); tag.setCount(80);
        dto.setTags(List.of(tag));

        BangumiRatingDTO rating = new BangumiRatingDTO();
        rating.setScore(8.5); rating.setRank(12); rating.setTotal(102);
        rating.setCount(Map.of("10", 100));
        dto.setRating(rating);

        BangumiCollectionDTO col = new BangumiCollectionDTO();
        col.setWish(50); col.setCollect(200); col.setDoing(10); col.setOnHold(5); col.setDropped(3);
        dto.setCollection(col);

        BangumiInfoboxDTO info = new BangumiInfoboxDTO();
        info.setKey("中文名"); info.setValueText("代号");
        dto.setInfobox(List.of(info));

        Anime anime = converter.toDomain(dto);

        assertThat(anime.getBangumiId()).isEqualTo(1L);
        assertThat(anime.getType()).isEqualTo(2);
        assertThat(anime.getImages().getLarge()).isEqualTo("L");
        assertThat(anime.getRating().getScore()).isEqualTo(8.5);
        assertThat(anime.getTags()).hasSize(1);
        assertThat(anime.getCollection().getCollect()).isEqualTo(200);
        assertThat(anime.getInfobox()).hasSize(1);
        assertThat(anime.getCoverUrl()).isEqualTo("L");
    }
}
