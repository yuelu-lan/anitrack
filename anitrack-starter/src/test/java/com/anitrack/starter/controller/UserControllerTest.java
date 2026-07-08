package com.anitrack.starter.controller;

import com.anitrack.application.exception.AnitrackAppException;
import com.anitrack.application.exception.AppExceptionEnum;
import com.anitrack.application.model.LoginBO;
import com.anitrack.application.model.UserBO;
import com.anitrack.application.service.UserApplication;
import com.anitrack.domain.user.enums.UserRole;
import com.anitrack.infra.auth.JwtTokenProvider;
import com.anitrack.starter.converter.HttpConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserApplication mockUserApplication;

    @MockBean
    private JwtTokenProvider mockJwtTokenProvider;

    @MockBean
    private HttpConverter mockHttpConverter;

    @Test
    void postRegister_whenRequestBodyIsEmptyObject_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/user/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @ParameterizedTest(name = "username={0} should return 400")
    @CsvSource({"'', password, nickname", "username, '', nickname", "username, password, ''"})
    void postRegister_whenFieldIsBlank_shouldReturnBadRequest(String username, String password, String nickname) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("username", username);
        request.put("password", password);
        request.put("nickname", nickname);

        mockMvc.perform(post("/api/user/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void postRegister_whenRequestIsValid_shouldReturnOk() throws Exception {
        // given
        Map<String, Object> request = Map.of("username", "alice", "password", "raw-password", "nickname", "Alice");
        UserBO userBO = UserBO.builder().id(1L).username("alice").nickname("Alice").role(UserRole.USER).build();
        when(mockHttpConverter.userRegisterReq2BO(any(com.anitrack.starter.request.UserRegisterReq.class))).thenCallRealMethod();
        when(mockUserApplication.register(any())).thenReturn(userBO);

        // when & then
        mockMvc.perform(post("/api/user/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(1));

        verify(mockUserApplication, times(1)).register(any());
    }

    @Test
    void postRegister_whenUsernameAlreadyExists_shouldReturnBusinessError() throws Exception {
        // given
        Map<String, Object> request = Map.of("username", "alice", "password", "raw-password", "nickname", "Alice");
        when(mockHttpConverter.userRegisterReq2BO(any(com.anitrack.starter.request.UserRegisterReq.class))).thenCallRealMethod();
        doThrow(AnitrackAppException.build(AppExceptionEnum.USERNAME_ALREADY_EXISTS))
            .when(mockUserApplication).register(any());

        // when & then
        mockMvc.perform(post("/api/user/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(0));
    }

    @Test
    void postLogin_whenRequestIsValid_shouldReturnTokenAndUserInfo() throws Exception {
        // given
        Map<String, Object> request = Map.of("username", "alice", "password", "raw-password");
        UserBO userBO = UserBO.builder().id(1L).username("alice").nickname("Alice").role(UserRole.USER).build();
        LoginBO loginBO = LoginBO.builder().user(userBO).token("mock-token").build();
        when(mockHttpConverter.userLoginReq2BO(any(com.anitrack.starter.request.UserLoginReq.class))).thenCallRealMethod();
        when(mockUserApplication.login(any())).thenReturn(loginBO);
        when(mockHttpConverter.bo2Response(userBO)).thenCallRealMethod();
        when(mockHttpConverter.toLoginResponse(loginBO)).thenCallRealMethod();

        // when & then
        mockMvc.perform(post("/api/user/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(1))
            .andExpect(jsonPath("$.data.token").value("mock-token"))
            .andExpect(jsonPath("$.data.userInfo.username").value("alice"));

        verify(mockUserApplication, times(1)).login(any());
    }

    @Test
    void postLogin_whenCredentialsInvalid_shouldReturnBusinessError() throws Exception {
        // given
        Map<String, Object> request = Map.of("username", "alice", "password", "wrong-password");
        when(mockHttpConverter.userLoginReq2BO(any(com.anitrack.starter.request.UserLoginReq.class))).thenCallRealMethod();
        doThrow(AnitrackAppException.build(AppExceptionEnum.LOGIN_FAILED))
            .when(mockUserApplication).login(any());

        // when & then
        mockMvc.perform(post("/api/user/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(0));

        verify(mockUserApplication, times(1)).login(any());
    }
}
