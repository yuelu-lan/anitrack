package com.anitrack.infra.converter;

import com.anitrack.domain.watchlist.enums.WatchStatus;
import com.anitrack.domain.watchlist.model.WatchlistItem;
import com.anitrack.infra.dal.po.WatchlistItemPO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface WatchlistItemConverter {

    WatchlistItemPO toPO(WatchlistItem item);

    default WatchlistItem toDomain(WatchlistItemPO po) {
        if (po == null) {
            return null;
        }
        return WatchlistItem.reconstitute(po.getId(), po.getUserId(), po.getAnimeId(),
            WatchStatus.valueOf(po.getStatus()), po.getCurrentEpisode(), po.getUpdateTime());
    }
}
