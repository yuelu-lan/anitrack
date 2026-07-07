package com.anitrack.application.service;

import com.anitrack.application.converter.UserBOConverter;
import com.anitrack.application.exception.AnitrackAppException;
import com.anitrack.application.exception.AppExceptionEnum;
import com.anitrack.application.model.LoginBO;
import com.anitrack.application.model.UserBO;
import com.anitrack.application.model.UserLoginBO;
import com.anitrack.application.model.UserRegisterBO;
import com.anitrack.domain.user.model.User;
import com.anitrack.domain.user.repo.UserRepo;
import com.anitrack.domain.user.service.TokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserApplication {

    private final UserRepo userRepo;
    private final PasswordEncoder passwordEncoder;
    private final UserBOConverter userBOConverter;
    private final TokenProvider tokenProvider;

    @Transactional
    public UserBO register(UserRegisterBO registerBO) {
        if (userRepo.existsByUsername(registerBO.getUsername())) {
            throw new AnitrackAppException(AppExceptionEnum.USERNAME_ALREADY_EXISTS);
        }
        String passwordHash = passwordEncoder.encode(registerBO.getPassword());
        User user = User.register(registerBO.getUsername(), passwordHash, registerBO.getNickname());
        User savedUser = userRepo.save(user);
        return userBOConverter.user2BO(savedUser);
    }

    public LoginBO login(UserLoginBO loginBO) {
        User user = userRepo.getByUsername(loginBO.getUsername());
        if (user == null || !passwordEncoder.matches(loginBO.getPassword(), user.getPasswordHash())) {
            throw new AnitrackAppException(AppExceptionEnum.LOGIN_FAILED);
        }
        UserBO userBO = userBOConverter.user2BO(user);
        String token = tokenProvider.generateToken(user.getId());
        return LoginBO.builder().user(userBO).token(token).build();
    }
}
