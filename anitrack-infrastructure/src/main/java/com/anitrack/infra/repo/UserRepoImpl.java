package com.anitrack.infra.repo;

import com.anitrack.domain.user.model.User;
import com.anitrack.domain.user.repo.UserRepo;
import com.anitrack.infra.converter.UserConverter;
import com.anitrack.infra.dal.mapper.UserMapper;
import com.anitrack.infra.dal.po.UserPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserRepoImpl implements UserRepo {

    private final UserMapper userMapper;
    private final UserConverter userConverter;

    @Override
    public User getByUsername(String username) {
        UserPO po = userMapper.selectByUsername(username);
        return po == null ? null : userConverter.toDomain(po);
    }

    @Override
    public User save(User user) {
        UserPO po = userConverter.toPO(user);
        userMapper.insert(po);
        return userConverter.toDomain(po);
    }

    @Override
    public boolean existsByUsername(String username) {
        return userMapper.existsByUsername(username);
    }
}
