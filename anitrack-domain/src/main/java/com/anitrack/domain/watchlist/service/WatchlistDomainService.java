package com.anitrack.domain.watchlist.service;

import com.anitrack.domain.anime.exception.AnimeNotFoundException;
import com.anitrack.domain.anime.exception.AnimeTotalEpisodesInvalidException;
import com.anitrack.domain.anime.model.Anime;
import com.anitrack.domain.anime.repo.AnimeRepo;
import com.anitrack.domain.watchlist.enums.WatchStatus;
import com.anitrack.domain.watchlist.exception.WatchlistItemAlreadyExistsException;
import com.anitrack.domain.watchlist.exception.WatchlistItemNotFoundException;
import com.anitrack.domain.watchlist.model.WatchlistItem;
import com.anitrack.domain.watchlist.model.WatchStatusChangedEvent;
import com.anitrack.domain.watchlist.repo.WatchlistRepo;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class WatchlistDomainService {

    private final WatchlistRepo watchlistRepo;
    private final AnimeRepo animeRepo;

    public WatchlistItem addToWatchlist(Long userId, Long animeId) {
        Anime anime = animeRepo.getById(animeId);
        if (anime == null) {
            throw new AnimeNotFoundException(animeId);
        }
        if (watchlistRepo.getByUserAndAnime(userId, animeId) != null) {
            throw new WatchlistItemAlreadyExistsException(userId, animeId);
        }
        WatchlistItem item = WatchlistItem.create(userId, animeId);
        return watchlistRepo.add(item);
    }

    public WatchlistItem updateProgress(Long userId, Long animeId, Integer episode) {
        WatchlistItem item = watchlistRepo.getByUserAndAnime(userId, animeId);
        if (item == null) {
            throw new WatchlistItemNotFoundException(userId, animeId);
        }
        Anime anime = animeRepo.getById(animeId);
        if (anime == null) {
            throw new AnimeNotFoundException(animeId);
        }
        item.updateProgress(episode, anime.getTotalEpisodes());
        watchlistRepo.update(item);
        return item;
    }

    public WatchStatusChangedEvent changeStatus(Long userId, Long animeId, WatchStatus newStatus) {
        WatchlistItem item = watchlistRepo.getByUserAndAnime(userId, animeId);
        if (item == null) {
            throw new WatchlistItemNotFoundException(userId, animeId);
        }
        Anime anime = animeRepo.getById(animeId);
        if (anime == null) {
            throw new AnimeNotFoundException(animeId);
        }
        Integer totalEpisodes = anime.getTotalEpisodes();
        if ((newStatus == WatchStatus.WATCHING || newStatus == WatchStatus.WATCHED)
            && (totalEpisodes == null || totalEpisodes <= 0)) {
            throw new AnimeTotalEpisodesInvalidException(animeId);
        }
        WatchStatusChangedEvent event = item.changeStatus(newStatus, totalEpisodes);
        watchlistRepo.update(item);
        return event;
    }
}
