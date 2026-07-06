package com.anitrack.infra.repo;

import com.anitrack.domain.watchlist.enums.WatchStatus;
import com.anitrack.domain.watchlist.model.WatchlistItem;
import com.anitrack.infra.converter.WatchlistItemConverter;
import com.anitrack.infra.dal.mapper.WatchlistItemMapper;
import com.anitrack.infra.dal.po.WatchlistItemPO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WatchlistRepoImplTest {

    @Mock
    private WatchlistItemMapper mockWatchlistItemMapper;

    @Mock
    private WatchlistItemConverter mockWatchlistItemConverter;

    @InjectMocks
    private WatchlistRepoImpl sut;

    @Test
    void getByUserAndAnime_whenNotFound_shouldReturnNull() {
        // given
        when(mockWatchlistItemMapper.selectByUserAndAnime(1L, 100L)).thenReturn(null);

        // when
        WatchlistItem result = sut.getByUserAndAnime(1L, 100L);

        // then
        assertThat(result).isNull();
    }

    @Test
    void add_whenCalled_shouldInsertAndReturnConvertedItem() {
        // given
        WatchlistItem item = WatchlistItem.create(1L, 100L);
        WatchlistItemPO po = new WatchlistItemPO();
        WatchlistItem persisted = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WANT_TO_WATCH, 0, null);
        when(mockWatchlistItemConverter.toPO(item)).thenReturn(po);
        when(mockWatchlistItemConverter.toDomain(po)).thenReturn(persisted);

        // when
        WatchlistItem result = sut.add(item);

        // then
        verify(mockWatchlistItemMapper, times(1)).insert(po);
        assertThat(result).isEqualTo(persisted);
    }

    @Test
    void update_whenCalled_shouldUpdateById() {
        // given
        WatchlistItem item = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WATCHING, 5, null);
        WatchlistItemPO po = new WatchlistItemPO();
        when(mockWatchlistItemConverter.toPO(item)).thenReturn(po);

        // when
        sut.update(item);

        // then
        verify(mockWatchlistItemMapper, times(1)).updateById(po);
    }

    @Test
    void listByUser_whenStatusIsNull_shouldQueryWithNullStatus() {
        // given
        WatchlistItemPO po = new WatchlistItemPO();
        WatchlistItem converted = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WANT_TO_WATCH, 0, null);
        when(mockWatchlistItemMapper.selectByUser(1L, null)).thenReturn(List.of(po));
        when(mockWatchlistItemConverter.toDomain(po)).thenReturn(converted);

        // when
        List<WatchlistItem> result = sut.listByUser(1L, null);

        // then
        assertThat(result).containsExactly(converted);
        verify(mockWatchlistItemMapper, times(1)).selectByUser(1L, null);
    }

    @Test
    void listByUser_whenStatusIsProvided_shouldQueryWithStatusName() {
        // given
        WatchlistItemPO po = new WatchlistItemPO();
        WatchlistItem converted = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WATCHING, 5, null);
        when(mockWatchlistItemMapper.selectByUser(1L, "WATCHING")).thenReturn(List.of(po));
        when(mockWatchlistItemConverter.toDomain(po)).thenReturn(converted);

        // when
        List<WatchlistItem> result = sut.listByUser(1L, WatchStatus.WATCHING);

        // then
        assertThat(result).containsExactly(converted);
        verify(mockWatchlistItemMapper, times(1)).selectByUser(1L, "WATCHING");
    }
}
