package com.anitrack.domain.anime.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class AnimeImages {
    private final String large;
    private final String common;
    private final String medium;
    private final String small;
    private final String grid;

    private AnimeImages(String large, String common, String medium, String small, String grid) {
        this.large = large;
        this.common = common;
        this.medium = medium;
        this.small = small;
        this.grid = grid;
    }

    @JsonCreator
    public static AnimeImages of(
            @JsonProperty("large") String large,
            @JsonProperty("common") String common,
            @JsonProperty("medium") String medium,
            @JsonProperty("small") String small,
            @JsonProperty("grid") String grid) {
        return new AnimeImages(large, common, medium, small, grid);
    }
}
