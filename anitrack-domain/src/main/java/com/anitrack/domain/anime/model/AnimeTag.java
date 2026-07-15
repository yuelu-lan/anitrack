package com.anitrack.domain.anime.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AnimeTag {
    private final String name;
    private final Integer count;

    public static AnimeTag of(String name, Integer count) {
        return new AnimeTag(name, count);
    }
}
