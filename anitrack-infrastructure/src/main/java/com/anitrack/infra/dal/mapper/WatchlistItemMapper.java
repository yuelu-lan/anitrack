package com.anitrack.infra.dal.mapper;

import com.anitrack.infra.dal.po.WatchlistItemPO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface WatchlistItemMapper {

    WatchlistItemPO selectByUserAndAnime(@Param("userId") Long userId, @Param("animeId") Long animeId);

    List<WatchlistItemPO> selectByUser(@Param("userId") Long userId, @Param("status") String status);

    int insert(WatchlistItemPO po);

    int updateById(WatchlistItemPO po);
}
