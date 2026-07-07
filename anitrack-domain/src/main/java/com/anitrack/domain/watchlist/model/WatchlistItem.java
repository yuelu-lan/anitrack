package com.anitrack.domain.watchlist.model;

import com.anitrack.domain.watchlist.enums.WatchStatus;
import com.anitrack.domain.watchlist.exception.IllegalWatchProgressException;
import com.anitrack.domain.watchlist.exception.IllegalWatchStatusTransitionException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WatchlistItem {

    private static final Map<WatchStatus, Set<WatchStatus>> TRANSITIONS = Map.of(
        WatchStatus.WANT_TO_WATCH, Set.of(WatchStatus.WATCHING, WatchStatus.WATCHED, WatchStatus.DROPPED),
        WatchStatus.WATCHING, Set.of(WatchStatus.WATCHED, WatchStatus.DROPPED),
        WatchStatus.WATCHED, Set.of(),
        WatchStatus.DROPPED, Set.of(WatchStatus.WATCHING, WatchStatus.WANT_TO_WATCH)
    );

    private Long id;
    private Long userId;
    private Long animeId;
    private WatchStatus status;
    private Integer currentEpisode;
    private LocalDateTime updateTime;

    public static WatchlistItem create(Long userId, Long animeId) {
        return WatchlistItem.builder()
            .userId(userId)
            .animeId(animeId)
            .status(WatchStatus.WANT_TO_WATCH)
            .currentEpisode(0)
            .build();
    }

    public static WatchlistItem reconstitute(Long id, Long userId, Long animeId, WatchStatus status,
                                              Integer currentEpisode, LocalDateTime updateTime) {
        return WatchlistItem.builder()
            .id(id)
            .userId(userId)
            .animeId(animeId)
            .status(status)
            .currentEpisode(currentEpisode)
            .updateTime(updateTime)
            .build();
    }

    public WatchStatusChangedEvent changeStatus(WatchStatus newStatus) {
        if (!TRANSITIONS.getOrDefault(this.status, Set.of()).contains(newStatus)) {
            throw new IllegalWatchStatusTransitionException(this.status, newStatus);
        }
        WatchStatus oldStatus = this.status;
        this.status = newStatus;
        return new WatchStatusChangedEvent(this.userId, this.animeId, oldStatus, newStatus);
    }

    public void updateProgress(Integer episode, Integer totalEpisodes) {
        if (this.status != WatchStatus.WATCHING) {
            throw new IllegalWatchProgressException("只有观看中的番剧才能更新进度");
        }
        if (episode == null || episode < 0) {
            throw new IllegalWatchProgressException("观看进度必须大于等于0");
        }
        if (totalEpisodes != null && totalEpisodes > 0 && episode > totalEpisodes) {
            throw new IllegalWatchProgressException("观看进度不能超过总集数");
        }
        this.currentEpisode = episode;
        this.updateTime = LocalDateTime.now();
    }
}
