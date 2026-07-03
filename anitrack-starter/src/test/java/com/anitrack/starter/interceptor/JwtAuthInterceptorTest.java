package com.anitrack.starter.interceptor;

import com.anitrack.common.utils.UserContextHolder;
import com.anitrack.infra.auth.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthInterceptorTest {

    @Mock
    private JwtTokenProvider mockJwtTokenProvider;

    @Mock
    private HttpServletRequest mockRequest;

    @Mock
    private HttpServletResponse mockResponse;

    @InjectMocks
    private JwtAuthInterceptor sut;

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void preHandle_whenAuthHeaderIsMissing_shouldReturn401AndReject() {
        // given
        when(mockRequest.getHeader("Authorization")).thenReturn(null);

        // when
        boolean result = sut.preHandle(mockRequest, mockResponse, new Object());

        // then
        assertThat(result).isFalse();
        verify(mockResponse).setStatus(401);
    }

    @Test
    void preHandle_whenAuthHeaderHasNoBearerPrefix_shouldReturn401AndReject() {
        // given
        when(mockRequest.getHeader("Authorization")).thenReturn("token-without-prefix");

        // when
        boolean result = sut.preHandle(mockRequest, mockResponse, new Object());

        // then
        assertThat(result).isFalse();
        verify(mockResponse).setStatus(401);
    }

    @Test
    void preHandle_whenTokenIsInvalid_shouldReturn401AndReject() {
        // given
        when(mockRequest.getHeader("Authorization")).thenReturn("Bearer invalid-token");
        when(mockJwtTokenProvider.validateToken("invalid-token")).thenReturn(false);

        // when
        boolean result = sut.preHandle(mockRequest, mockResponse, new Object());

        // then
        assertThat(result).isFalse();
        verify(mockResponse).setStatus(401);
    }

    @Test
    void preHandle_whenTokenIsValid_shouldSetUserContextAndAllow() {
        // given
        when(mockRequest.getHeader("Authorization")).thenReturn("Bearer valid-token");
        when(mockJwtTokenProvider.validateToken("valid-token")).thenReturn(true);
        when(mockJwtTokenProvider.getUserId("valid-token")).thenReturn(42L);

        // when
        boolean result = sut.preHandle(mockRequest, mockResponse, new Object());

        // then
        assertThat(result).isTrue();
        assertThat(UserContextHolder.getUserId()).isEqualTo(42L);
    }

    @Test
    void afterCompletion_whenCalled_shouldClearUserContext() {
        // given
        UserContextHolder.setUserId(1L);

        // when
        sut.afterCompletion(mockRequest, mockResponse, new Object(), null);

        // then
        assertThatThrownBy(UserContextHolder::getUserId)
            .isInstanceOf(IllegalStateException.class);
    }
}
