package com.anitrack.domain.anime.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AnimeImages {
    private final String large;
    private final String common;
    private final String medium;
    private final String small;
    private final String grid;

    public static AnimeImages of(String large, String common, String medium, String small, String grid) {
        return new AnimeImages(large, common, medium, small, grid);
    }
}
