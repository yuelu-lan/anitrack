package com.anitrack.domain.watchlist.model;

import com.anitrack.domain.watchlist.enums.WatchStatus;
import com.anitrack.domain.watchlist.exception.IllegalWatchProgressException;
import com.anitrack.domain.watchlist.exception.IllegalWatchStatusTransitionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WatchlistItemTest {

    @Test
    void create_whenCalled_shouldInitializeAsWantToWatchWithZeroProgress() {
        // when
        WatchlistItem item = WatchlistItem.create(1L, 100L);

        // then
        assertThat(item.getId()).isNull();
        assertThat(item.getUserId()).isEqualTo(1L);
        assertThat(item.getAnimeId()).isEqualTo(100L);
        assertThat(item.getStatus()).isEqualTo(WatchStatus.WANT_TO_WATCH);
        assertThat(item.getCurrentEpisode()).isZero();
    }

    @Test
    void reconstitute_whenCalled_shouldRestoreAllFields() {
        // when
        WatchlistItem item = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WATCHING, 5, null);

        // then
        assertThat(item.getId()).isEqualTo(10L);
        assertThat(item.getStatus()).isEqualTo(WatchStatus.WATCHING);
        assertThat(item.getCurrentEpisode()).isEqualTo(5);
    }

    @Test
    void changeStatus_whenWantToWatchToWatching_shouldSucceedAndReturnEvent() {
        // given
        WatchlistItem item = WatchlistItem.create(1L, 100L);

        // when
        WatchStatusChangedEvent event = item.changeStatus(WatchStatus.WATCHING);

        // then
        assertThat(item.getStatus()).isEqualTo(WatchStatus.WATCHING);
        assertThat(event.userId()).isEqualTo(1L);
        assertThat(event.animeId()).isEqualTo(100L);
        assertThat(event.oldStatus()).isEqualTo(WatchStatus.WANT_TO_WATCH);
        assertThat(event.newStatus()).isEqualTo(WatchStatus.WATCHING);
    }

    @Test
    void changeStatus_whenWatchingToWatched_shouldSucceed() {
        // given
        WatchlistItem item = WatchlistItem.create(1L, 100L);
        item.changeStatus(WatchStatus.WATCHING);

        // when
        item.changeStatus(WatchStatus.WATCHED);

        // then
        assertThat(item.getStatus()).isEqualTo(WatchStatus.WATCHED);
    }

    @Test
    void changeStatus_whenWatchingToDropped_shouldSucceed() {
        // given
        WatchlistItem item = WatchlistItem.create(1L, 100L);
        item.changeStatus(WatchStatus.WATCHING);

        // when
        item.changeStatus(WatchStatus.DROPPED);

        // then
        assertThat(item.getStatus()).isEqualTo(WatchStatus.DROPPED);
    }

    @Test
    void changeStatus_whenDroppedToWatching_shouldSucceedAndKeepProgress() {
        // given
        WatchlistItem item = WatchlistItem.create(1L, 100L);
        item.changeStatus(WatchStatus.WATCHING);
        item.updateProgress(5, 12);
        item.changeStatus(WatchStatus.DROPPED);

        // when
        item.changeStatus(WatchStatus.WATCHING);

        // then
        assertThat(item.getStatus()).isEqualTo(WatchStatus.WATCHING);
        assertThat(item.getCurrentEpisode()).isEqualTo(5);
    }

    @Test
    void changeStatus_whenWantToWatchToWatched_shouldSucceed() {
        // given
        WatchlistItem item = WatchlistItem.create(1L, 100L);

        // when
        item.changeStatus(WatchStatus.WATCHED);

        // then
        assertThat(item.getStatus()).isEqualTo(WatchStatus.WATCHED);
    }

    @Test
    void changeStatus_whenWantToWatchToDropped_shouldSucceed() {
        // given
        WatchlistItem item = WatchlistItem.create(1L, 100L);

        // when
        item.changeStatus(WatchStatus.DROPPED);

        // then
        assertThat(item.getStatus()).isEqualTo(WatchStatus.DROPPED);
    }

    @Test
    void changeStatus_whenDroppedToWantToWatch_shouldSucceedAndKeepProgress() {
        // given
        WatchlistItem item = WatchlistItem.create(1L, 100L);
        item.changeStatus(WatchStatus.WATCHING);
        item.updateProgress(5, 12);
        item.changeStatus(WatchStatus.DROPPED);

        // when
        item.changeStatus(WatchStatus.WANT_TO_WATCH);

        // then
        assertThat(item.getStatus()).isEqualTo(WatchStatus.WANT_TO_WATCH);
        assertThat(item.getCurrentEpisode()).isEqualTo(5);
    }

    @Test
    void changeStatus_whenWatchedToWatching_shouldThrowException() {
        // given
        WatchlistItem item = WatchlistItem.create(1L, 100L);
        item.changeStatus(WatchStatus.WATCHING);
        item.changeStatus(WatchStatus.WATCHED);

        // when & then
        assertThatThrownBy(() -> item.changeStatus(WatchStatus.WATCHING))
            .isInstanceOf(IllegalWatchStatusTransitionException.class);
    }

    @Test
    void changeStatus_whenWatchingToWantToWatch_shouldThrowException() {
        // given
        WatchlistItem item = WatchlistItem.create(1L, 100L);
        item.changeStatus(WatchStatus.WATCHING);

        // when & then
        assertThatThrownBy(() -> item.changeStatus(WatchStatus.WANT_TO_WATCH))
            .isInstanceOf(IllegalWatchStatusTransitionException.class);
    }

    @Test
    void updateProgress_whenStatusIsNotWatching_shouldThrowException() {
        // given
        WatchlistItem item = WatchlistItem.create(1L, 100L);

        // when & then
        assertThatThrownBy(() -> item.updateProgress(1, 12))
            .isInstanceOf(IllegalWatchProgressException.class);
    }

    @Test
    void updateProgress_whenEpisodeIsZero_shouldSucceed() {
        // given
        WatchlistItem item = WatchlistItem.create(1L, 100L);
        item.changeStatus(WatchStatus.WATCHING);

        // when
        item.updateProgress(0, 12);

        // then
        assertThat(item.getCurrentEpisode()).isZero();
    }

    @Test
    void updateProgress_whenEpisodeRegresses_shouldSucceed() {
        // given
        WatchlistItem item = WatchlistItem.create(1L, 100L);
        item.changeStatus(WatchStatus.WATCHING);
        item.updateProgress(5, 12);

        // when
        item.updateProgress(3, 12);

        // then
        assertThat(item.getCurrentEpisode()).isEqualTo(3);
    }

    @Test
    void updateProgress_whenValid_shouldRefreshUpdateTime() {
        // given
        WatchlistItem item = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WATCHING, 0, null);

        // when
        item.updateProgress(5, 12);

        // then
        assertThat(item.getUpdateTime()).isNotNull();
    }

    @Test
    void updateProgress_whenEpisodeExceedsTotalEpisodes_shouldThrowException() {
        // given
        WatchlistItem item = WatchlistItem.create(1L, 100L);
        item.changeStatus(WatchStatus.WATCHING);

        // when & then
        assertThatThrownBy(() -> item.updateProgress(13, 12))
            .isInstanceOf(IllegalWatchProgressException.class);
    }

    @Test
    void updateProgress_whenTotalEpisodesIsZero_shouldAllowAnyEpisode() {
        // given
        WatchlistItem item = WatchlistItem.create(1L, 100L);
        item.changeStatus(WatchStatus.WATCHING);

        // when
        item.updateProgress(999, 0);

        // then
        assertThat(item.getCurrentEpisode()).isEqualTo(999);
    }

    @Test
    void updateProgress_whenValid_shouldUpdateCurrentEpisode() {
        // given
        WatchlistItem item = WatchlistItem.create(1L, 100L);
        item.changeStatus(WatchStatus.WATCHING);

        // when
        item.updateProgress(5, 12);

        // then
        assertThat(item.getCurrentEpisode()).isEqualTo(5);
    }
}
