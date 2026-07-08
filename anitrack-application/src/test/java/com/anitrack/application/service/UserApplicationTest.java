package com.anitrack.application.service;

import com.anitrack.application.converter.UserBOConverter;
import com.anitrack.application.exception.AnitrackAppException;
import com.anitrack.application.model.LoginBO;
import com.anitrack.application.model.UserBO;
import com.anitrack.application.model.UserLoginBO;
import com.anitrack.application.model.UserRegisterBO;
import com.anitrack.domain.user.enums.UserRole;
import com.anitrack.domain.user.exception.UsernameAlreadyExistsException;
import com.anitrack.domain.user.model.User;
import com.anitrack.domain.user.repo.UserRepo;
import com.anitrack.domain.user.service.TokenProvider;
import com.anitrack.domain.user.service.UserDomainService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserApplicationTest {

    @Mock
    private UserRepo mockUserRepo;

    @Mock
    private PasswordEncoder mockPasswordEncoder;

    @Mock
    private UserBOConverter mockUserBOConverter;

    @Mock
    private TokenProvider mockTokenProvider;

    @Mock
    private UserDomainService mockUserDomainService;

    private UserApplication sut;

    @Test
    void register_whenUsernameNotExists_shouldDelegateAndReturnUserBO() {
        sut = new UserApplication(mockUserRepo, mockPasswordEncoder, mockUserBOConverter,
            mockTokenProvider, mockUserDomainService);
        UserRegisterBO registerBO = UserRegisterBO.builder()
            .username("alice")
            .password("raw-password")
            .nickname("Alice")
            .build();
        when(mockPasswordEncoder.encode("raw-password")).thenReturn("hashed-password");
        User savedUser = User.reconstitute(1L, "alice", "hashed-password", "Alice", null, UserRole.USER);
        when(mockUserDomainService.register("alice", "hashed-password", "Alice")).thenReturn(savedUser);
        UserBO expectedBO = UserBO.builder().id(1L).username("alice").nickname("Alice").role(UserRole.USER).build();
        when(mockUserBOConverter.user2BO(savedUser)).thenReturn(expectedBO);

        UserBO result = sut.register(registerBO);

        assertThat(result).isEqualTo(expectedBO);
        verify(mockUserDomainService, times(1)).register("alice", "hashed-password", "Alice");
    }

    @Test
    void register_whenUsernameExists_shouldThrowException() {
        sut = new UserApplication(mockUserRepo, mockPasswordEncoder, mockUserBOConverter,
            mockTokenProvider, mockUserDomainService);
        UserRegisterBO registerBO = UserRegisterBO.builder()
            .username("alice")
            .password("raw-password")
            .nickname("Alice")
            .build();
        when(mockPasswordEncoder.encode("raw-password")).thenReturn("hashed-password");
        when(mockUserDomainService.register(eq("alice"), eq("hashed-password"), eq("Alice")))
            .thenThrow(new UsernameAlreadyExistsException("alice"));

        assertThatThrownBy(() -> sut.register(registerBO))
            .isInstanceOf(AnitrackAppException.class)
            .hasMessageContaining("用户名已存在");

        verify(mockUserBOConverter, never()).user2BO(any());
    }

    @Test
    void login_whenCredentialsAreValid_shouldReturnLoginBO() {
        sut = new UserApplication(mockUserRepo, mockPasswordEncoder, mockUserBOConverter,
            mockTokenProvider, mockUserDomainService);
        UserLoginBO loginBO = UserLoginBO.builder().username("alice").password("raw-password").build();
        User existingUser = User.reconstitute(1L, "alice", "hashed-password", "Alice", null, UserRole.USER);
        when(mockUserRepo.getByUsername("alice")).thenReturn(existingUser);
        when(mockPasswordEncoder.matches("raw-password", "hashed-password")).thenReturn(true);
        UserBO expectedUserBO = UserBO.builder().id(1L).username("alice").build();
        when(mockUserBOConverter.user2BO(existingUser)).thenReturn(expectedUserBO);
        when(mockTokenProvider.generateToken(1L)).thenReturn("mock-token");

        LoginBO result = sut.login(loginBO);

        assertThat(result.getUser()).isEqualTo(expectedUserBO);
        assertThat(result.getToken()).isEqualTo("mock-token");
    }

    @Test
    void login_whenUserNotFound_shouldThrowException() {
        sut = new UserApplication(mockUserRepo, mockPasswordEncoder, mockUserBOConverter,
            mockTokenProvider, mockUserDomainService);
        UserLoginBO loginBO = UserLoginBO.builder().username("unknown").password("raw-password").build();
        when(mockUserRepo.getByUsername("unknown")).thenReturn(null);

        assertThatThrownBy(() -> sut.login(loginBO))
            .isInstanceOf(AnitrackAppException.class)
            .hasMessageContaining("用户名或密码错误");
    }

    @Test
    void login_whenPasswordIncorrect_shouldThrowException() {
        sut = new UserApplication(mockUserRepo, mockPasswordEncoder, mockUserBOConverter,
            mockTokenProvider, mockUserDomainService);
        UserLoginBO loginBO = UserLoginBO.builder().username("alice").password("wrong-password").build();
        User existingUser = User.reconstitute(1L, "alice", "hashed-password", null, null, UserRole.USER);
        when(mockUserRepo.getByUsername("alice")).thenReturn(existingUser);
        when(mockPasswordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);

        assertThatThrownBy(() -> sut.login(loginBO))
            .isInstanceOf(AnitrackAppException.class)
            .hasMessageContaining("用户名或密码错误");
    }
}
