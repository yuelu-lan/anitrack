package com.anitrack.domain.watchlist.repo;

import com.anitrack.domain.watchlist.enums.WatchStatus;
import com.anitrack.domain.watchlist.model.WatchlistItem;

import java.util.List;

public interface WatchlistRepo {

    WatchlistItem getByUserAndAnime(Long userId, Long animeId);

    List<WatchlistItem> listByUser(Long userId, WatchStatus status);

    WatchlistItem add(WatchlistItem item);

    void update(WatchlistItem item);
}
