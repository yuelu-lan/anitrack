package com.anitrack.infra.dal.mapper;

import com.anitrack.infra.dal.po.AnimePO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AnimeMapper {

    AnimePO selectById(@Param("id") Long id);

    AnimePO selectByBangumiId(@Param("bangumiId") Long bangumiId);

    List<AnimePO> selectByIds(@Param("ids") List<Long> ids);

    int insert(AnimePO po);

    int updateById(AnimePO po);
}
