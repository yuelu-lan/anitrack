package com.anitrack.domain.anime.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RatingTest {

    @Test
    void of_creates_rating_with_fields() {
        var count = new java.util.HashMap<String, Integer>();
        count.put("10", 100);
        count.put("1", 2);
        Rating rating = Rating.of(8.5, 12, 102, count);
        assertThat(rating.getScore()).isEqualTo(8.5);
        assertThat(rating.getRank()).isEqualTo(12);
        assertThat(rating.getTotal()).isEqualTo(102);
        assertThat(rating.getCount()).containsEntry("10", 100);
    }
}
