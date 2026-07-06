package com.anitrack.domain.user.repo;

import com.anitrack.domain.user.model.User;

import java.util.List;

public interface UserRepo {

    User getByUsername(String username);

    User save(User user);

    boolean existsByUsername(String username);

    List<User> listByIds(List<Long> ids);
}
