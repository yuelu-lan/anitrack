package com.anitrack.application.converter;

import com.anitrack.application.model.WatchlistItemBO;
import com.anitrack.domain.watchlist.model.WatchlistItem;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface WatchlistConverter {

    WatchlistItemBO watchlistItem2BO(WatchlistItem item);
}
