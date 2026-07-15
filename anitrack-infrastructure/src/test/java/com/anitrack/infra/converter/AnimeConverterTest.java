package com.anitrack.infra.converter;

import com.anitrack.domain.anime.model.*;
import com.anitrack.infra.dal.po.AnimePO;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AnimeConverterTest {

    private final AnimeConverter converter = new AnimeConverterImpl();

    @Test
    void po_roundtrip_preserves_full_fields() {
        Rating rating = Rating.of(8.5, 12, 102, Map.of("10", 100));
        Collection collection = Collection.of(1, 2, 3, 4, 5);
        List<AnimeTag> tags = List.of(AnimeTag.of("科幻", 10));
        AnimeImages images = AnimeImages.of("L", "C", "M", "S", "G");
        List<Infobox> infobox = List.of(Infobox.ofText("中文名", "代号"));

        Anime anime = Anime.fromBangumi(
                1L, 2, "原名", "中文名", "简介", false, false, false,
                LocalDate.of(2024, 4, 1), "TV", images, 12, 12, 0,
                List.of("科幻"), tags, rating, collection, infobox);

        AnimePO po = converter.toPO(anime);
        assertThat(po.getBangumiId()).isEqualTo(1L);
        assertThat(po.getRating().getScore()).isEqualTo(8.5);
        assertThat(po.getTags()).hasSize(1);
        assertThat(po.getImages().getLarge()).isEqualTo("L");

        Anime back = converter.toDomain(po);
        assertThat(back.getRating().getScore()).isEqualTo(8.5);
        assertThat(back.getCollection().getDropped()).isEqualTo(5);
        assertThat(back.getInfobox()).hasSize(1);
    }
}
