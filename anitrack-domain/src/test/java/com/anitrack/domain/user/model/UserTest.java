package com.anitrack.domain.user.model;

import com.anitrack.domain.user.enums.UserRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    void register_whenCalled_shouldCreateUserWithDefaultRole() {
        // when
        User user = User.register("alice", "hashed-password", "Alice");

        // then
        assertThat(user.getUsername()).isEqualTo("alice");
        assertThat(user.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(user.getNickname()).isEqualTo("Alice");
        assertThat(user.getRole()).isEqualTo(UserRole.USER);
        assertThat(user.getAvatarUrl()).isNull();
    }
}
