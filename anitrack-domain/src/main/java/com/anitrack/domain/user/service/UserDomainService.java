package com.anitrack.domain.user.service;

import com.anitrack.domain.user.exception.UsernameAlreadyExistsException;
import com.anitrack.domain.user.model.User;
import com.anitrack.domain.user.repo.UserRepo;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class UserDomainService {

    private final UserRepo userRepo;

    public User register(String username, String passwordHash, String nickname) {
        if (userRepo.existsByUsername(username)) {
            throw new UsernameAlreadyExistsException(username);
        }
        User user = User.register(username, passwordHash, nickname);
        return userRepo.save(user);
    }
}
