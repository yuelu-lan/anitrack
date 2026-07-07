package com.anitrack.domain.anime.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnimeTotalEpisodesInvalidExceptionTest {

    @Test
    void constructor_shouldCarryAnimeIdInMessage() {
        AnimeTotalEpisodesInvalidException ex = new AnimeTotalEpisodesInvalidException(100L);
        assertThat(ex.getMessage()).isEqualTo("番剧总集数无效: 100");
    }
}
