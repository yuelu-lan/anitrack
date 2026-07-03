package com.anitrack.infra.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private JwtTokenProvider sut;

    @BeforeEach
    void setUp() {
        sut = new JwtTokenProvider(
            "test-secret-key-must-be-at-least-32-bytes-long",
            86400000L
        );
    }

    @Test
    void generateToken_thenValidateToken_shouldReturnTrue() {
        // when
        String token = sut.generateToken(1L);

        // then
        assertThat(sut.validateToken(token)).isTrue();
    }

    @Test
    void generateToken_thenGetUserId_shouldReturnOriginalUserId() {
        // when
        String token = sut.generateToken(42L);

        // then
        assertThat(sut.getUserId(token)).isEqualTo(42L);
    }

    @Test
    void validateToken_whenTokenIsMalformed_shouldReturnFalse() {
        // when & then
        assertThat(sut.validateToken("not-a-valid-token")).isFalse();
    }
}
