package com.anitrack.starter.controller;

import com.anitrack.application.model.LoginBO;
import com.anitrack.application.model.UserBO;
import com.anitrack.application.service.UserApplication;
import com.anitrack.starter.converter.HttpConverter;
import com.anitrack.starter.request.UserLoginReq;
import com.anitrack.starter.request.UserRegisterReq;
import com.anitrack.starter.response.LoginResponse;
import com.anitrack.starter.response.ResponseResult;
import com.anitrack.starter.response.UserInfoResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserApplication userApplication;
    private final HttpConverter httpConverter;

    @PostMapping("/register")
    public ResponseResult<UserInfoResponse> register(@Valid @RequestBody UserRegisterReq req) {
        UserBO userBO = userApplication.register(httpConverter.userRegisterReq2BO(req));
        return ResponseResult.success(httpConverter.bo2Response(userBO));
    }

    @PostMapping("/login")
    public ResponseResult<LoginResponse> login(@Valid @RequestBody UserLoginReq req) {
        LoginBO loginBO = userApplication.login(httpConverter.userLoginReq2BO(req));
        return ResponseResult.success(httpConverter.toLoginResponse(loginBO));
    }
}
