package com.anitrack.infra.dal.mapper;

import com.anitrack.infra.dal.po.AnimePO;
import org.apache.ibatis.annotations.Param;

public interface AnimeMapper {

    AnimePO selectById(@Param("id") Long id);

    AnimePO selectByBangumiId(@Param("bangumiId") Long bangumiId);

    int insert(AnimePO po);

    int updateById(AnimePO po);
}
