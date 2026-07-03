package com.anitrack.starter.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class UserRegisterReq {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    @NotBlank(message = "昵称不能为空")
    private String nickname;
}
