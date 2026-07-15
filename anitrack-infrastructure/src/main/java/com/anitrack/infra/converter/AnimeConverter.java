package com.anitrack.infra.converter;

import com.anitrack.domain.anime.model.Anime;
import com.anitrack.infra.dal.po.AnimePO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AnimeConverter {

    AnimePO toPO(Anime anime);

    default Anime toDomain(AnimePO po) {
        if (po == null) {
            return null;
        }
        return Anime.reconstitute(
                po.getId(), po.getBangumiId(), po.getType(), po.getTitleOriginal(), po.getTitleCn(),
                po.getSummary(), po.getNsfw(), po.getLocked(), po.getSeries(),
                po.getAirDate(), po.getPlatform(), po.getImages(),
                po.getEps(), po.getTotalEpisodes(), po.getVolumes(),
                po.getMetaTags() == null ? java.util.List.of() : po.getMetaTags(),
                po.getTags() == null ? java.util.List.of() : po.getTags(),
                po.getRating() == null ? com.anitrack.domain.anime.model.Rating.empty() : po.getRating(),
                po.getCollection() == null ? com.anitrack.domain.anime.model.Collection.empty() : po.getCollection(),
                po.getInfobox() == null ? java.util.List.of() : po.getInfobox());
    }
}
