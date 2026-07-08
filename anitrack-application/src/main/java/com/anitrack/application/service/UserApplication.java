package com.anitrack.application.service;

import com.anitrack.application.converter.UserBOConverter;
import com.anitrack.application.exception.AnitrackAppException;
import com.anitrack.application.exception.AppExceptionEnum;
import com.anitrack.application.model.LoginBO;
import com.anitrack.application.model.UserBO;
import com.anitrack.application.model.UserLoginBO;
import com.anitrack.application.model.UserRegisterBO;
import com.anitrack.domain.user.exception.UsernameAlreadyExistsException;
import com.anitrack.domain.user.model.User;
import com.anitrack.domain.user.repo.UserRepo;
import com.anitrack.domain.user.service.TokenProvider;
import com.anitrack.domain.user.service.UserDomainService;
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
    private final UserDomainService userDomainService;

    @Transactional
    public UserBO register(UserRegisterBO registerBO) {
        String passwordHash = passwordEncoder.encode(registerBO.getPassword());
        try {
            User savedUser = userDomainService.register(
                registerBO.getUsername(), passwordHash, registerBO.getNickname());
            return userBOConverter.user2BO(savedUser);
        } catch (UsernameAlreadyExistsException e) {
            throw AnitrackAppException.build(AppExceptionEnum.USERNAME_ALREADY_EXISTS);
        }
    }

    public LoginBO login(UserLoginBO loginBO) {
        User user = userRepo.getByUsername(loginBO.getUsername());
        if (user == null || !passwordEncoder.matches(loginBO.getPassword(), user.getPasswordHash())) {
            throw AnitrackAppException.build(AppExceptionEnum.LOGIN_FAILED);
        }
        UserBO userBO = userBOConverter.user2BO(user);
        String token = tokenProvider.generateToken(user.getId());
        return LoginBO.builder().user(userBO).token(token).build();
    }
}
