package com.anitrack.application.service;

import com.anitrack.application.converter.AnimeBOConverter;
import com.anitrack.application.exception.AnitrackAppException;
import com.anitrack.application.model.AnimeBO;
import com.anitrack.domain.anime.exception.BangumiApiException;
import com.anitrack.domain.anime.gateway.BangumiGateway;
import com.anitrack.domain.anime.model.Anime;
import com.anitrack.domain.anime.repo.AnimeRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnimeApplicationTest {

    @Mock
    private BangumiGateway mockBangumiGateway;

    @Mock
    private AnimeRepo mockAnimeRepo;

    @Mock
    private AnimeBOConverter mockAnimeBOConverter;

    @InjectMocks
    private AnimeApplication sut;

    @Test
    void searchAnime_whenGatewaySucceeds_shouldUpsertAndReturnBOList() {
        Anime searchResult = Anime.fromBangumi(100L, "中文名", "Original Title", "http://cover.jpg", 12,
            LocalDate.of(2024, 1, 1), "简介");
        Anime persisted = Anime.reconstitute(1L, 100L, "中文名", "Original Title", "http://cover.jpg", 12,
            LocalDate.of(2024, 1, 1), "简介");
        AnimeBO bo = AnimeBO.builder().id(1L).titleCn("中文名").build();
        when(mockBangumiGateway.search("关键字")).thenReturn(List.of(searchResult));
        when(mockAnimeRepo.upsert(searchResult)).thenReturn(persisted);
        when(mockAnimeBOConverter.anime2BO(persisted)).thenReturn(bo);

        List<AnimeBO> result = sut.searchAnime("关键字");

        assertThat(result).containsExactly(bo);
        verify(mockAnimeRepo, times(1)).upsert(searchResult);
    }

    @Test
    void searchAnime_whenGatewayThrowsBangumiApiException_shouldThrowAppException() {
        when(mockBangumiGateway.search("关键字"))
            .thenThrow(new BangumiApiException("调用失败", new RuntimeException("timeout")));

        assertThatThrownBy(() -> sut.searchAnime("关键字"))
            .isInstanceOf(AnitrackAppException.class)
            .hasMessageContaining("番剧信息服务暂时不可用");

        verify(mockAnimeRepo, never()).upsert(any());
    }

    @Test
    void getAnimeDetail_whenAnimeExists_shouldReturnBO() {
        Anime anime = Anime.reconstitute(1L, 100L, "中文名", "Original Title", "http://cover.jpg", 12,
            LocalDate.of(2024, 1, 1), "简介");
        AnimeBO bo = AnimeBO.builder().id(1L).titleOriginal("Original Title").build();
        when(mockAnimeRepo.getById(1L)).thenReturn(anime);
        when(mockAnimeBOConverter.anime2BO(anime)).thenReturn(bo);

        AnimeBO result = sut.getAnimeDetail(1L);

        assertThat(result).isEqualTo(bo);
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getTitleOriginal()).isEqualTo("Original Title");
    }

    @Test
    void getAnimeDetail_whenAnimeNotFound_shouldThrowException() {
        when(mockAnimeRepo.getById(999L)).thenReturn(null);

        assertThatThrownBy(() -> sut.getAnimeDetail(999L))
            .isInstanceOf(AnitrackAppException.class)
            .hasMessageContaining("番剧不存在");
    }
}
