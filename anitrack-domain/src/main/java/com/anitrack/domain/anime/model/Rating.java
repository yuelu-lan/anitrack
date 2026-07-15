package com.anitrack.domain.anime.model;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class Rating {
    private final Double score;
    private final Integer rank;
    private final Integer total;
    private final Map<String, Integer> count;

    public static Rating of(Double score, Integer rank, Integer total, Map<String, Integer> count) {
        return new Rating(score, rank, total, count);
    }

    public static Rating empty() {
        return new Rating(null, null, 0, Map.of());
    }
}
