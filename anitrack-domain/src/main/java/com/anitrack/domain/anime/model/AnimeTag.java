package com.anitrack.domain.anime.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class AnimeTag {
    private final String name;
    private final Integer count;

    private AnimeTag(String name, Integer count) {
        this.name = name;
        this.count = count;
    }

    @JsonCreator
    public static AnimeTag of(
            @JsonProperty("name") String name,
            @JsonProperty("count") Integer count) {
        return new AnimeTag(name, count);
    }
}
