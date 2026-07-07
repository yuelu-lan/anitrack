package com.anitrack.domain.watchlist.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WatchStatusTest {

    @Test
    void code_shouldBeStable() {
        assertThat(WatchStatus.WANT_TO_WATCH.getCode()).isEqualTo(1);
        assertThat(WatchStatus.WATCHING.getCode()).isEqualTo(2);
        assertThat(WatchStatus.WATCHED.getCode()).isEqualTo(3);
        assertThat(WatchStatus.DROPPED.getCode()).isEqualTo(4);
    }
}
