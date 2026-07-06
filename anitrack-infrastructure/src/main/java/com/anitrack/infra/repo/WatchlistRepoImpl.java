package com.anitrack.infra.repo;

import com.anitrack.domain.watchlist.enums.WatchStatus;
import com.anitrack.domain.watchlist.model.WatchlistItem;
import com.anitrack.domain.watchlist.repo.WatchlistRepo;
import com.anitrack.infra.converter.WatchlistItemConverter;
import com.anitrack.infra.dal.mapper.WatchlistItemMapper;
import com.anitrack.infra.dal.po.WatchlistItemPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class WatchlistRepoImpl implements WatchlistRepo {

    private final WatchlistItemMapper watchlistItemMapper;
    private final WatchlistItemConverter watchlistItemConverter;

    @Override
    public WatchlistItem getByUserAndAnime(Long userId, Long animeId) {
        WatchlistItemPO po = watchlistItemMapper.selectByUserAndAnime(userId, animeId);
        return po == null ? null : watchlistItemConverter.toDomain(po);
    }

    @Override
    public List<WatchlistItem> listByUser(Long userId, WatchStatus status) {
        String statusValue = status == null ? null : status.name();
        return watchlistItemMapper.selectByUser(userId, statusValue).stream()
            .map(watchlistItemConverter::toDomain)
            .toList();
    }

    @Override
    public WatchlistItem add(WatchlistItem item) {
        WatchlistItemPO po = watchlistItemConverter.toPO(item);
        watchlistItemMapper.insert(po);
        return watchlistItemConverter.toDomain(po);
    }

    @Override
    public void update(WatchlistItem item) {
        watchlistItemMapper.updateById(watchlistItemConverter.toPO(item));
    }
}
