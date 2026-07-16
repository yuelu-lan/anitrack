package com.anitrack.infra.gateway.bangumi;

import com.anitrack.domain.anime.model.Anime;
import com.anitrack.domain.anime.model.AnimeImages;
import com.anitrack.domain.anime.model.AnimeTag;
import com.anitrack.domain.anime.model.Collection;
import com.anitrack.domain.anime.model.Infobox;
import com.anitrack.domain.anime.model.InfoboxItem;
import com.anitrack.domain.anime.model.Rating;
import com.anitrack.infra.gateway.bangumi.dto.BangumiCollectionDTO;
import com.anitrack.infra.gateway.bangumi.dto.BangumiImagesDTO;
import com.anitrack.infra.gateway.bangumi.dto.BangumiInfoboxDTO;
import com.anitrack.infra.gateway.bangumi.dto.BangumiRatingDTO;
import com.anitrack.infra.gateway.bangumi.dto.BangumiSubjectDTO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
public class BangumiConverter {

    public Anime toDomain(BangumiSubjectDTO dto) {
        BangumiImagesDTO img = dto.getImages();
        AnimeImages images = img == null ? null
                : AnimeImages.of(img.getLarge(), img.getCommon(), img.getMedium(), img.getSmall(), img.getGrid());

        List<AnimeTag> tags = dto.getTags() == null ? List.of()
                : dto.getTags().stream().map(t -> AnimeTag.of(t.getName(), t.getCount())).toList();

        BangumiRatingDTO r = dto.getRating();
        Rating rating = r == null ? Rating.empty()
                : Rating.of(r.getScore(), r.getRank(), r.getTotal(), r.getCount() == null ? Map.of() : r.getCount());

        BangumiCollectionDTO c = dto.getCollection();
        Collection collection = c == null ? Collection.empty()
                : Collection.of(c.getWish(), c.getCollect(), c.getDoing(), c.getOnHold(), c.getDropped());

        List<Infobox> infobox = dto.getInfobox() == null ? List.of()
                : dto.getInfobox().stream().map(this::toInfobox).toList();

        return Anime.fromBangumi(
                dto.getId(), dto.getType(), dto.getName(), dto.getNameCn(),
                dto.getSummary(), dto.getNsfw(), dto.getLocked(), dto.getSeries(),
                parseAirDate(dto.getDate()), dto.getPlatform(), images,
                dto.getEps(), dto.getTotalEpisodes(), dto.getVolumes(),
                dto.getMetaTags() == null ? List.of() : dto.getMetaTags(),
                tags, rating, collection, infobox);
    }

    private Infobox toInfobox(BangumiInfoboxDTO dto) {
        if (dto.getValueText() != null) {
            return Infobox.ofText(dto.getKey(), dto.getValueText());
        }
        List<InfoboxItem> items = dto.getValueItems() == null ? List.of()
                : dto.getValueItems().stream().map(i -> InfoboxItem.of(i.getK(), i.getV())).toList();
        return Infobox.ofItems(dto.getKey(), items);
    }

    private LocalDate parseAirDate(String date) {
        return StringUtils.hasText(date) ? LocalDate.parse(date) : null;
    }
}
