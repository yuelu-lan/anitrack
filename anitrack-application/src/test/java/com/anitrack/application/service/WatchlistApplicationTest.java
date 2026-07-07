package com.anitrack.application.service;

import com.anitrack.application.assembler.WatchlistAssembler;
import com.anitrack.application.converter.WatchlistBOConverter;
import com.anitrack.application.exception.AnitrackAppException;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WatchlistApplicationTest {

    @Mock
    private WatchlistDomainService mockWatchlistDomainService;

    @Mock
    private WatchlistRepo mockWatchlistRepo;

    @Mock
    private AnimeRepo mockAnimeRepo;

    @Mock
    private WatchlistAssembler mockWatchlistAssembler;

    @Mock
    private WatchlistBOConverter mockWatchlistBOConverter;

    @Mock
    private ApplicationEventPublisher mockEventPublisher;

    @InjectMocks
    private WatchlistApplication sut;

    @Test
    void addToWatchlist_whenSucceeds_shouldReturnBO() {
        // given
        WatchlistItem item = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WANT_TO_WATCH, 0, null);
        when(mockWatchlistDomainService.addToWatchlist(1L, 100L)).thenReturn(item);
        WatchlistItemBO expectedBO = WatchlistItemBO.builder().id(10L).status(WatchStatus.WANT_TO_WATCH).build();
        when(mockWatchlistBOConverter.watchlistItem2BO(item)).thenReturn(expectedBO);

        // when
        WatchlistItemBO result = sut.addToWatchlist(1L, 100L);

        // then
        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getStatus()).isEqualTo(WatchStatus.WANT_TO_WATCH);
    }

    @Test
    void addToWatchlist_whenAnimeNotFound_shouldThrowAppException() {
        // given
        when(mockWatchlistDomainService.addToWatchlist(1L, 100L))
            .thenThrow(new AnimeNotFoundException(100L));

        // when & then
        assertThatThrownBy(() -> sut.addToWatchlist(1L, 100L))
            .isInstanceOf(AnitrackAppException.class)
            .hasMessageContaining("番剧不存在");
    }

    @Test
    void addToWatchlist_whenAlreadyExists_shouldThrowAppException() {
        // given
        when(mockWatchlistDomainService.addToWatchlist(1L, 100L))
            .thenThrow(new WatchlistItemAlreadyExistsException(1L, 100L));

        // when & then
        assertThatThrownBy(() -> sut.addToWatchlist(1L, 100L))
            .isInstanceOf(AnitrackAppException.class)
            .hasMessageContaining("该番剧已在追番列表中");
    }

    @Test
    void changeStatus_whenSucceeds_shouldReturnBOAndPublishEvent() {
        // given
        WatchStatusChangedEvent event = new WatchStatusChangedEvent(1L, 100L, WatchStatus.WATCHING, WatchStatus.WATCHED);
        WatchlistItem item = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WATCHED, 12, null);
        when(mockWatchlistDomainService.changeStatus(1L, 100L, WatchStatus.WATCHED)).thenReturn(event);
        when(mockWatchlistRepo.getByUserAndAnime(1L, 100L)).thenReturn(item);
        WatchlistItemBO expectedBO = WatchlistItemBO.builder().status(WatchStatus.WATCHED).build();
        when(mockWatchlistBOConverter.watchlistItem2BO(item)).thenReturn(expectedBO);

        // when
        WatchlistItemBO result = sut.changeStatus(1L, 100L, WatchStatus.WATCHED);

        // then
        assertThat(result.getStatus()).isEqualTo(WatchStatus.WATCHED);
        verify(mockEventPublisher, times(1)).publishEvent(event);
    }

    @Test
    void changeStatus_whenItemNotFound_shouldThrowAppException() {
        // given
        when(mockWatchlistDomainService.changeStatus(1L, 100L, WatchStatus.WATCHING))
            .thenThrow(new WatchlistItemNotFoundException(1L, 100L));

        // when & then
        assertThatThrownBy(() -> sut.changeStatus(1L, 100L, WatchStatus.WATCHING))
            .isInstanceOf(AnitrackAppException.class)
            .hasMessageContaining("追番记录不存在");

        verify(mockEventPublisher, never()).publishEvent(any());
    }

    @Test
    void changeStatus_whenTransitionIllegal_shouldThrowAppException() {
        // given
        when(mockWatchlistDomainService.changeStatus(1L, 100L, WatchStatus.WATCHING))
            .thenThrow(new IllegalWatchStatusTransitionException(WatchStatus.WATCHED, WatchStatus.WATCHING));

        // when & then
        assertThatThrownBy(() -> sut.changeStatus(1L, 100L, WatchStatus.WATCHING))
            .isInstanceOf(AnitrackAppException.class)
            .hasMessageContaining("非法的追番状态转移");

        verify(mockEventPublisher, never()).publishEvent(any());
    }

    @Test
    void changeStatus_whenTotalEpisodesInvalid_shouldThrowAppException() {
        // given
        when(mockWatchlistDomainService.changeStatus(1L, 100L, WatchStatus.WATCHING))
            .thenThrow(new AnimeTotalEpisodesInvalidException(100L));

        // when & then
        assertThatThrownBy(() -> sut.changeStatus(1L, 100L, WatchStatus.WATCHING))
            .isInstanceOf(AnitrackAppException.class)
            .hasMessageContaining("番剧总集数无效");

        verify(mockEventPublisher, never()).publishEvent(any());
    }

    @Test
    void updateProgress_whenSucceeds_shouldReturnBO() {
        // given
        WatchlistItem item = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WATCHING, 5, null);
        when(mockWatchlistDomainService.updateProgress(1L, 100L, 5)).thenReturn(item);
        WatchlistItemBO expectedBO = WatchlistItemBO.builder().currentEpisode(5).build();
        when(mockWatchlistBOConverter.watchlistItem2BO(item)).thenReturn(expectedBO);

        // when
        WatchlistItemBO result = sut.updateProgress(1L, 100L, 5);

        // then
        assertThat(result.getCurrentEpisode()).isEqualTo(5);
    }

    @Test
    void updateProgress_whenItemNotFound_shouldThrowAppException() {
        // given
        when(mockWatchlistDomainService.updateProgress(1L, 100L, 5))
            .thenThrow(new WatchlistItemNotFoundException(1L, 100L));

        // when & then
        assertThatThrownBy(() -> sut.updateProgress(1L, 100L, 5))
            .isInstanceOf(AnitrackAppException.class)
            .hasMessageContaining("追番记录不存在");
    }

    @Test
    void updateProgress_whenAnimeNotFound_shouldThrowAppException() {
        // given
        when(mockWatchlistDomainService.updateProgress(1L, 100L, 5))
            .thenThrow(new AnimeNotFoundException(100L));

        // when & then
        assertThatThrownBy(() -> sut.updateProgress(1L, 100L, 5))
            .isInstanceOf(AnitrackAppException.class)
            .hasMessageContaining("番剧不存在");
    }

    @Test
    void updateProgress_whenProgressIllegal_shouldThrowAppException() {
        // given
        when(mockWatchlistDomainService.updateProgress(1L, 100L, -1))
            .thenThrow(new IllegalWatchProgressException("观看进度必须大于0"));

        // when & then
        assertThatThrownBy(() -> sut.updateProgress(1L, 100L, -1))
            .isInstanceOf(AnitrackAppException.class)
            .hasMessageContaining("追番进度更新不合法");
    }

    @Test
    void getWatchlistItem_whenExists_shouldReturnBO() {
        // given
        WatchlistItem item = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WATCHING, 5, null);
        when(mockWatchlistRepo.getByUserAndAnime(1L, 100L)).thenReturn(item);
        WatchlistItemBO expectedBO = WatchlistItemBO.builder().id(10L).build();
        when(mockWatchlistBOConverter.watchlistItem2BO(item)).thenReturn(expectedBO);

        // when
        WatchlistItemBO result = sut.getWatchlistItem(1L, 100L);

        // then
        assertThat(result.getId()).isEqualTo(10L);
    }

    @Test
    void getWatchlistItem_whenNotFound_shouldThrowAppException() {
        // given
        when(mockWatchlistRepo.getByUserAndAnime(1L, 100L)).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> sut.getWatchlistItem(1L, 100L))
            .isInstanceOf(AnitrackAppException.class)
            .hasMessageContaining("追番记录不存在");
    }

    @Test
    void listMyWatchlist_whenCalled_shouldFetchAnimesAndAssemble() {
        // given
        WatchlistItem item = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WATCHING, 5, null);
        Anime anime = Anime.reconstitute(100L, 999L, "中文名", "Original Title", "http://cover.jpg", 12,
            LocalDate.of(2024, 1, 1), "简介");
        WatchlistItemViewBO viewBO = WatchlistItemViewBO.builder().id(10L).animeId(100L).build();
        when(mockWatchlistRepo.listByUser(1L, WatchStatus.WATCHING)).thenReturn(List.of(item));
        when(mockAnimeRepo.listByIds(List.of(100L))).thenReturn(List.of(anime));
        when(mockWatchlistAssembler.assemble(List.of(item), List.of(anime))).thenReturn(List.of(viewBO));

        // when
        List<WatchlistItemViewBO> result = sut.listMyWatchlist(1L, WatchStatus.WATCHING);

        // then
        assertThat(result).containsExactly(viewBO);
    }
}
