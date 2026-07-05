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
        return Anime.reconstitute(po.getId(), po.getBangumiId(), po.getTitleCn(), po.getTitleOriginal(),
            po.getCoverUrl(), po.getTotalEpisodes(), po.getAirDate(), po.getSummary());
    }
}
