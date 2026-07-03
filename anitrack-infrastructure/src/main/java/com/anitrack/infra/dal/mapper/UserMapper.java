package com.anitrack.infra.dal.mapper;

import com.anitrack.infra.dal.po.UserPO;
import org.apache.ibatis.annotations.Param;

public interface UserMapper {

    int insert(UserPO po);

    UserPO selectByUsername(@Param("username") String username);

    boolean existsByUsername(@Param("username") String username);
}
