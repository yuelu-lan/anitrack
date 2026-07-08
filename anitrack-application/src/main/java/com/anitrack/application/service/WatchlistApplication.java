package com.anitrack.application.service;

import com.anitrack.application.assembler.WatchlistAssembler;
import com.anitrack.application.converter.WatchlistBOConverter;
import com.anitrack.application.exception.AnitrackAppException;
import com.anitrack.application.exception.AppExceptionEnum;
import com.anitrack.application.model.WatchlistItemBO;
import com.anitrack.application.model.WatchlistItemViewBO;
import com.anitrack.domain.anime.exception.AnimeNotFoundException;
import com.anitrack.domain.anime.exception.AnimeTotalEpisodesInvalidException;
import com.anitrack.domain.anime.model.Anime;
import com.anitrack.domain.anime.repo.AnimeRepo;
import com.anitrack.domain.watchlist.enums.WatchStatus;
import com.anitrack.domain.watchlist.exception.IllegalWatchProgressException;
import com.anitrack.domain.watchlist.exception.IllegalWatchStatusTransitionException;
import com.anitrack.domain.watchlist.exception.WatchlistItemAlreadyExistsException;
import com.anitrack.domain.watchlist.exception.WatchlistItemNotFoundException;
import com.anitrack.domain.watchlist.model.WatchStatusChangedEvent;
import com.anitrack.domain.watchlist.model.WatchlistItem;
import com.anitrack.domain.watchlist.repo.WatchlistRepo;
import com.anitrack.domain.watchlist.service.WatchlistDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WatchlistApplication {

    private final WatchlistDomainService watchlistDomainService;
    private final WatchlistRepo watchlistRepo;
    private final AnimeRepo animeRepo;
    private final WatchlistAssembler watchlistAssembler;
    private final WatchlistBOConverter watchlistBOConverter;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public WatchlistItemBO addToWatchlist(Long userId, Long animeId) {
        WatchlistItem item;
        try {
            item = watchlistDomainService.addToWatchlist(userId, animeId);
        } catch (AnimeNotFoundException e) {
            throw AnitrackAppException.build(AppExceptionEnum.ANIME_NOT_FOUND);
        } catch (WatchlistItemAlreadyExistsException e) {
            throw AnitrackAppException.build(AppExceptionEnum.WATCHLIST_ITEM_ALREADY_EXISTS);
        }
        return watchlistBOConverter.watchlistItem2BO(item);
    }

    @Transactional
    public WatchlistItemBO changeStatus(Long userId, Long animeId, WatchStatus newStatus) {
        WatchStatusChangedEvent event;
        try {
            event = watchlistDomainService.changeStatus(userId, animeId, newStatus);
        } catch (WatchlistItemNotFoundException e) {
            throw AnitrackAppException.build(AppExceptionEnum.WATCHLIST_ITEM_NOT_FOUND);
        } catch (AnimeNotFoundException e) {
            throw AnitrackAppException.build(AppExceptionEnum.ANIME_NOT_FOUND);
        } catch (AnimeTotalEpisodesInvalidException e) {
            throw AnitrackAppException.build(AppExceptionEnum.ANIME_TOTAL_EPISODES_INVALID);
        } catch (IllegalWatchStatusTransitionException e) {
            throw AnitrackAppException.build(AppExceptionEnum.ILLEGAL_WATCH_STATUS_TRANSITION);
        }
        eventPublisher.publishEvent(event);
        WatchlistItem item = watchlistRepo.getByUserAndAnime(userId, animeId);
        if (item == null) {
            throw AnitrackAppException.build(AppExceptionEnum.WATCHLIST_ITEM_NOT_FOUND);
        }
        return watchlistBOConverter.watchlistItem2BO(item);
    }

    @Transactional
    public WatchlistItemBO updateProgress(Long userId, Long animeId, Integer episode) {
        WatchlistItem item;
        try {
            item = watchlistDomainService.updateProgress(userId, animeId, episode);
        } catch (WatchlistItemNotFoundException e) {
            throw AnitrackAppException.build(AppExceptionEnum.WATCHLIST_ITEM_NOT_FOUND);
        } catch (AnimeNotFoundException e) {
            throw AnitrackAppException.build(AppExceptionEnum.ANIME_NOT_FOUND);
        } catch (IllegalWatchProgressException e) {
            throw AnitrackAppException.build(AppExceptionEnum.ILLEGAL_WATCH_PROGRESS);
        }
        return watchlistBOConverter.watchlistItem2BO(item);
    }

    public WatchlistItemBO getWatchlistItem(Long userId, Long animeId) {
        WatchlistItem item = watchlistRepo.getByUserAndAnime(userId, animeId);
        if (item == null) {
            throw AnitrackAppException.build(AppExceptionEnum.WATCHLIST_ITEM_NOT_FOUND);
        }
        return watchlistBOConverter.watchlistItem2BO(item);
    }

    public List<WatchlistItemViewBO> listMyWatchlist(Long userId, WatchStatus status) {
        List<WatchlistItem> items = watchlistRepo.listByUser(userId, status);
        List<Long> animeIds = items.stream().map(WatchlistItem::getAnimeId).toList();
        List<Anime> animes = animeRepo.listByIds(animeIds);
        return watchlistAssembler.assemble(items, animes);
    }
}
