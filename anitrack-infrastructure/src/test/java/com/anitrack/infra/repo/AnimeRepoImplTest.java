package com.anitrack.infra.repo;

import com.anitrack.domain.anime.model.Anime;
import com.anitrack.infra.converter.AnimeConverter;
import com.anitrack.infra.dal.mapper.AnimeMapper;
import com.anitrack.infra.dal.po.AnimePO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnimeRepoImplTest {

    @Mock
    private AnimeMapper mockAnimeMapper;

    @Mock
    private AnimeConverter mockAnimeConverter;

    @InjectMocks
    private AnimeRepoImpl sut;

    @Test
    void upsert_whenBangumiIdNotExists_shouldInsert() {
        // given
        Anime anime = Anime.fromBangumi(100L, "中文名", "Original Title", "http://cover.jpg", 12,
            LocalDate.of(2024, 1, 1), "简介");
        AnimePO po = new AnimePO();
        po.setBangumiId(100L);
        Anime persisted = Anime.reconstitute(1L, 100L, "中文名", "Original Title", "http://cover.jpg", 12,
            LocalDate.of(2024, 1, 1), "简介");
        when(mockAnimeMapper.selectByBangumiId(100L)).thenReturn(null);
        when(mockAnimeConverter.toPO(anime)).thenReturn(po);
        when(mockAnimeConverter.toDomain(po)).thenReturn(persisted);

        // when
        Anime result = sut.upsert(anime);

        // then
        verify(mockAnimeMapper, times(1)).insert(po);
        verify(mockAnimeMapper, never()).updateById(any());
        assertThat(result).isEqualTo(persisted);
    }

    @Test
    void upsert_whenBangumiIdExists_shouldUpdateWithExistingId() {
        // given
        Anime anime = Anime.fromBangumi(100L, "中文名", "Original Title", "http://cover.jpg", 12,
            LocalDate.of(2024, 1, 1), "简介");
        AnimePO existing = new AnimePO();
        existing.setId(1L);
        existing.setBangumiId(100L);
        AnimePO po = new AnimePO();
        po.setBangumiId(100L);
        when(mockAnimeMapper.selectByBangumiId(100L)).thenReturn(existing);
        when(mockAnimeConverter.toPO(anime)).thenReturn(po);
        when(mockAnimeConverter.toDomain(po)).thenReturn(anime);

        // when
        sut.upsert(anime);

        // then
        ArgumentCaptor<AnimePO> captor = ArgumentCaptor.forClass(AnimePO.class);
        verify(mockAnimeMapper, times(1)).updateById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(1L);
        verify(mockAnimeMapper, never()).insert(any());
    }

    @Test
    void getById_whenNotFound_shouldReturnNull() {
        // given
        when(mockAnimeMapper.selectById(999L)).thenReturn(null);

        // when
        Anime result = sut.getById(999L);

        // then
        assertThat(result).isNull();
    }

    @Test
    void listByIds_whenIdsProvided_shouldReturnConvertedList() {
        // given
        AnimePO po = new AnimePO();
        Anime anime = Anime.reconstitute(1L, 100L, "中文名", "Original Title", "http://cover.jpg", 12,
            LocalDate.of(2024, 1, 1), "简介");
        when(mockAnimeMapper.selectByIds(List.of(1L))).thenReturn(List.of(po));
        when(mockAnimeConverter.toDomain(po)).thenReturn(anime);

        // when
        List<Anime> result = sut.listByIds(List.of(1L));

        // then
        assertThat(result).containsExactly(anime);
    }

    @Test
    void listByIds_whenIdsEmpty_shouldReturnEmptyListWithoutQuerying() {
        // when
        List<Anime> result = sut.listByIds(List.of());

        // then
        assertThat(result).isEmpty();
        verify(mockAnimeMapper, never()).selectByIds(any());
    }
}
