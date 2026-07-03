package com.anitrack.domain.user.repo;

import com.anitrack.domain.user.model.User;

public interface UserRepo {

    User getByUsername(String username);

    void save(User user);

    boolean existsByUsername(String username);
}
