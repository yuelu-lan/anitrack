package com.anitrack.domain.watchlist.model;

import com.anitrack.domain.watchlist.enums.WatchStatus;

public record WatchStatusChangedEvent(Long userId, Long animeId, WatchStatus oldStatus, WatchStatus newStatus) {
}
