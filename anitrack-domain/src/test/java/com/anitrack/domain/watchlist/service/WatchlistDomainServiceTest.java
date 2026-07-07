package com.anitrack.domain.watchlist.service;

import com.anitrack.domain.anime.exception.AnimeNotFoundException;
import com.anitrack.domain.anime.exception.AnimeTotalEpisodesInvalidException;
import com.anitrack.domain.anime.model.Anime;
import com.anitrack.domain.anime.repo.AnimeRepo;
import com.anitrack.domain.watchlist.enums.WatchStatus;
import com.anitrack.domain.watchlist.exception.WatchlistItemAlreadyExistsException;
import com.anitrack.domain.watchlist.exception.WatchlistItemNotFoundException;
import com.anitrack.domain.watchlist.model.WatchStatusChangedEvent;
import com.anitrack.domain.watchlist.model.WatchlistItem;
import com.anitrack.domain.watchlist.repo.WatchlistRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WatchlistDomainServiceTest {

    @Mock
    private WatchlistRepo mockWatchlistRepo;

    @Mock
    private AnimeRepo mockAnimeRepo;

    @InjectMocks
    private WatchlistDomainService sut;

    private Anime createTestAnime() {
        return Anime.reconstitute(100L, 999L, "中文名", "Original Title", "http://cover.jpg", 12,
            LocalDate.of(2024, 1, 1), "简介");
    }

    @Test
    void addToWatchlist_whenAnimeExistsAndNotDuplicate_shouldCreateAndAdd() {
        // given
        when(mockAnimeRepo.getById(100L)).thenReturn(createTestAnime());
        when(mockWatchlistRepo.getByUserAndAnime(1L, 100L)).thenReturn(null);
        WatchlistItem saved = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WANT_TO_WATCH, 0, null);
        when(mockWatchlistRepo.add(any(WatchlistItem.class))).thenReturn(saved);

        // when
        WatchlistItem result = sut.addToWatchlist(1L, 100L);

        // then
        assertThat(result).isEqualTo(saved);
        verify(mockWatchlistRepo, times(1)).add(any(WatchlistItem.class));
    }

    @Test
    void addToWatchlist_whenAnimeNotFound_shouldThrowException() {
        // given
        when(mockAnimeRepo.getById(100L)).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> sut.addToWatchlist(1L, 100L))
            .isInstanceOf(AnimeNotFoundException.class);

        verify(mockWatchlistRepo, never()).add(any());
    }

    @Test
    void addToWatchlist_whenAlreadyExists_shouldThrowException() {
        // given
        when(mockAnimeRepo.getById(100L)).thenReturn(createTestAnime());
        WatchlistItem existing = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WANT_TO_WATCH, 0, null);
        when(mockWatchlistRepo.getByUserAndAnime(1L, 100L)).thenReturn(existing);

        // when & then
        assertThatThrownBy(() -> sut.addToWatchlist(1L, 100L))
            .isInstanceOf(WatchlistItemAlreadyExistsException.class);

        verify(mockWatchlistRepo, never()).add(any());
    }

    @Test
    void addToWatchlist_whenTotalEpisodesInvalid_shouldStillAllowAdd() {
        // given
        Anime animeWithInvalidEpisodes = Anime.reconstitute(100L, 999L, "中文名", "Original Title", "http://cover.jpg",
            null, LocalDate.of(2024, 1, 1), "简介");
        when(mockAnimeRepo.getById(100L)).thenReturn(animeWithInvalidEpisodes);
        when(mockWatchlistRepo.getByUserAndAnime(1L, 100L)).thenReturn(null);
        WatchlistItem saved = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WANT_TO_WATCH, 0, null);
        when(mockWatchlistRepo.add(any(WatchlistItem.class))).thenReturn(saved);

        // when
        WatchlistItem result = sut.addToWatchlist(1L, 100L);

        // then
        assertThat(result).isEqualTo(saved);
        verify(mockWatchlistRepo, times(1)).add(any(WatchlistItem.class));
    }

    @Test
    void updateProgress_whenItemAndAnimeExist_shouldDelegateToAggregateAndUpdate() {
        // given
        WatchlistItem item = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WATCHING, 0, null);
        when(mockWatchlistRepo.getByUserAndAnime(1L, 100L)).thenReturn(item);
        when(mockAnimeRepo.getById(100L)).thenReturn(createTestAnime());

        // when
        WatchlistItem result = sut.updateProgress(1L, 100L, 5);

        // then
        assertThat(result.getCurrentEpisode()).isEqualTo(5);
        verify(mockWatchlistRepo, times(1)).update(item);
    }

    @Test
    void updateProgress_whenItemNotFound_shouldThrowException() {
        // given
        when(mockWatchlistRepo.getByUserAndAnime(1L, 100L)).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> sut.updateProgress(1L, 100L, 5))
            .isInstanceOf(WatchlistItemNotFoundException.class);

        verify(mockWatchlistRepo, never()).update(any());
    }

    @Test
    void updateProgress_whenAnimeNotFound_shouldThrowException() {
        // given
        WatchlistItem item = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WATCHING, 0, null);
        when(mockWatchlistRepo.getByUserAndAnime(1L, 100L)).thenReturn(item);
        when(mockAnimeRepo.getById(100L)).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> sut.updateProgress(1L, 100L, 5))
            .isInstanceOf(AnimeNotFoundException.class);

        verify(mockWatchlistRepo, never()).update(any());
    }

    @Test
    void changeStatus_whenTransitionToWatching_shouldDelegateAndUpdateAndReturnEvent() {
        // given
        WatchlistItem item = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WANT_TO_WATCH, 0, null);
        when(mockWatchlistRepo.getByUserAndAnime(1L, 100L)).thenReturn(item);
        when(mockAnimeRepo.getById(100L)).thenReturn(createTestAnime());

        // when
        WatchStatusChangedEvent event = sut.changeStatus(1L, 100L, WatchStatus.WATCHING);

        // then
        assertThat(event.oldStatus()).isEqualTo(WatchStatus.WANT_TO_WATCH);
        assertThat(event.newStatus()).isEqualTo(WatchStatus.WATCHING);
        verify(mockWatchlistRepo, times(1)).update(item);
    }

    @Test
    void changeStatus_whenTransitionToWatched_shouldSetProgressToTotalEpisodes() {
        // given
        WatchlistItem item = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WATCHING, 5, null);
        when(mockWatchlistRepo.getByUserAndAnime(1L, 100L)).thenReturn(item);
        when(mockAnimeRepo.getById(100L)).thenReturn(createTestAnime());

        // when
        sut.changeStatus(1L, 100L, WatchStatus.WATCHED);

        // then
        assertThat(item.getStatus()).isEqualTo(WatchStatus.WATCHED);
        assertThat(item.getCurrentEpisode()).isEqualTo(12);
        verify(mockWatchlistRepo, times(1)).update(item);
    }

    @Test
    void changeStatus_whenTransitionToWatchingAndTotalEpisodesInvalid_shouldThrow() {
        // given
        Anime animeWithInvalidEpisodes = Anime.reconstitute(100L, 999L, "中文名", "Original Title", "http://cover.jpg",
            null, LocalDate.of(2024, 1, 1), "简介");
        WatchlistItem item = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WANT_TO_WATCH, 0, null);
        when(mockWatchlistRepo.getByUserAndAnime(1L, 100L)).thenReturn(item);
        when(mockAnimeRepo.getById(100L)).thenReturn(animeWithInvalidEpisodes);

        // when & then
        assertThatThrownBy(() -> sut.changeStatus(1L, 100L, WatchStatus.WATCHING))
            .isInstanceOf(AnimeTotalEpisodesInvalidException.class);

        verify(mockWatchlistRepo, never()).update(any());
    }

    @Test
    void changeStatus_whenTransitionToDropped_shouldNotCheckTotalEpisodes() {
        // given
        Anime animeWithInvalidEpisodes = Anime.reconstitute(100L, 999L, "中文名", "Original Title", "http://cover.jpg",
            null, LocalDate.of(2024, 1, 1), "简介");
        WatchlistItem item = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WATCHING, 5, null);
        when(mockWatchlistRepo.getByUserAndAnime(1L, 100L)).thenReturn(item);
        when(mockAnimeRepo.getById(100L)).thenReturn(animeWithInvalidEpisodes);

        // when
        sut.changeStatus(1L, 100L, WatchStatus.DROPPED);

        // then
        assertThat(item.getStatus()).isEqualTo(WatchStatus.DROPPED);
        verify(mockWatchlistRepo, times(1)).update(item);
    }

    @Test
    void changeStatus_whenItemNotFound_shouldThrowException() {
        // given
        when(mockWatchlistRepo.getByUserAndAnime(1L, 100L)).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> sut.changeStatus(1L, 100L, WatchStatus.WATCHING))
            .isInstanceOf(WatchlistItemNotFoundException.class);

        verify(mockWatchlistRepo, never()).update(any());
    }

    @Test
    void changeStatus_whenAnimeNotFound_shouldThrowException() {
        // given
        WatchlistItem item = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WANT_TO_WATCH, 0, null);
        when(mockWatchlistRepo.getByUserAndAnime(1L, 100L)).thenReturn(item);
        when(mockAnimeRepo.getById(100L)).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> sut.changeStatus(1L, 100L, WatchStatus.WATCHING))
            .isInstanceOf(AnimeNotFoundException.class);

        verify(mockWatchlistRepo, never()).update(any());
    }
}
