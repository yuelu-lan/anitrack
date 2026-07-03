package com.anitrack.common.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserContextHolderTest {

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void getUserId_whenUserIdIsSet_shouldReturnIt() {
        // given
        UserContextHolder.setUserId(1L);

        // when & then
        assertThat(UserContextHolder.getUserId()).isEqualTo(1L);
    }

    @Test
    void getUserId_whenNotLoggedIn_shouldThrowException() {
        assertThatThrownBy(UserContextHolder::getUserId)
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void clear_whenCalled_shouldRemoveUserId() {
        // given
        UserContextHolder.setUserId(1L);

        // when
        UserContextHolder.clear();

        // then
        assertThatThrownBy(UserContextHolder::getUserId)
            .isInstanceOf(IllegalStateException.class);
    }
}
