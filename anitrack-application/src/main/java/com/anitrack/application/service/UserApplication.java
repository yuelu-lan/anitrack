package com.anitrack.application.service;

import com.anitrack.application.exception.AnitrackAppException;
import com.anitrack.application.exception.AppExceptionEnum;
import com.anitrack.application.model.UserBO;
import com.anitrack.application.model.UserLoginBO;
import com.anitrack.application.model.UserRegisterBO;
import com.anitrack.domain.user.model.User;
import com.anitrack.domain.user.repo.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserApplication {

    private final UserRepo userRepo;
    private final PasswordEncoder passwordEncoder;

    public UserBO register(UserRegisterBO registerBO) {
        if (userRepo.existsByUsername(registerBO.getUsername())) {
            throw new AnitrackAppException(AppExceptionEnum.USERNAME_ALREADY_EXISTS);
        }
        String passwordHash = passwordEncoder.encode(registerBO.getPassword());
        User user = User.register(registerBO.getUsername(), passwordHash, registerBO.getNickname());
        userRepo.save(user);
        return toBO(user);
    }

    public UserBO login(UserLoginBO loginBO) {
        User user = userRepo.getByUsername(loginBO.getUsername());
        if (user == null || !passwordEncoder.matches(loginBO.getPassword(), user.getPasswordHash())) {
            throw new AnitrackAppException(AppExceptionEnum.LOGIN_FAILED);
        }
        return toBO(user);
    }

    private UserBO toBO(User user) {
        return UserBO.builder()
            .id(user.getId())
            .username(user.getUsername())
            .nickname(user.getNickname())
            .avatarUrl(user.getAvatarUrl())
            .role(user.getRole())
            .build();
    }
}
