package com.anitrack.domain.anime.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.Getter;

@Getter
public class Rating {
    private final Double score;
    private final Integer rank;
    private final Integer total;
    private final Map<String, Integer> count;

    private Rating(Double score, Integer rank, Integer total, Map<String, Integer> count) {
        this.score = score;
        this.rank = rank;
        this.total = total;
        this.count = count;
    }

    @JsonCreator
    public static Rating of(
            @JsonProperty("score") Double score,
            @JsonProperty("rank") Integer rank,
            @JsonProperty("total") Integer total,
            @JsonProperty("count") Map<String, Integer> count) {
        return new Rating(score, rank, total, count);
    }

    public static Rating empty() {
        return new Rating(null, null, 0, Map.of());
    }
}
