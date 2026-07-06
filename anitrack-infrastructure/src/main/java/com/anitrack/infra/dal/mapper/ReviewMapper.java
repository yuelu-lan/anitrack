package com.anitrack.infra.dal.mapper;

import com.anitrack.infra.dal.po.ReviewPO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ReviewMapper {

    ReviewPO selectByUserAndAnime(@Param("userId") Long userId, @Param("animeId") Long animeId);

    List<ReviewPO> selectByAnime(@Param("animeId") Long animeId, @Param("offset") int offset, @Param("limit") int limit);

    long countByAnime(@Param("animeId") Long animeId);

    List<ReviewPO> selectByUserId(@Param("userId") Long userId);

    int insert(ReviewPO po);

    int updateByUserAndAnime(ReviewPO po);
}
