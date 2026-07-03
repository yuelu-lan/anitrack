package com.anitrack.starter.converter;

import com.anitrack.application.model.UserBO;
import com.anitrack.application.model.UserLoginBO;
import com.anitrack.application.model.UserRegisterBO;
import com.anitrack.starter.request.UserLoginReq;
import com.anitrack.starter.request.UserRegisterReq;
import com.anitrack.starter.response.LoginResponse;
import com.anitrack.starter.response.UserInfoResponse;
import org.springframework.stereotype.Component;

@Component
public class HttpConverter {

    public UserRegisterBO req2BO(UserRegisterReq req) {
        return UserRegisterBO.builder()
            .username(req.getUsername())
            .password(req.getPassword())
            .nickname(req.getNickname())
            .build();
    }

    public UserLoginBO req2BO(UserLoginReq req) {
        return UserLoginBO.builder()
            .username(req.getUsername())
            .password(req.getPassword())
            .build();
    }

    public UserInfoResponse bo2Response(UserBO bo) {
        return UserInfoResponse.builder()
            .id(bo.getId())
            .username(bo.getUsername())
            .nickname(bo.getNickname())
            .avatarUrl(bo.getAvatarUrl())
            .build();
    }

    public LoginResponse toLoginResponse(String token, UserBO bo) {
        return LoginResponse.builder()
            .token(token)
            .userInfo(bo2Response(bo))
            .build();
    }
}
