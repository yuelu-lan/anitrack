package com.anitrack.domain.anime.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class InfoboxItem {
    private final String k;
    private final String v;

    private InfoboxItem(String k, String v) {
        this.k = k;
        this.v = v;
    }

    @JsonCreator
    public static InfoboxItem of(
            @JsonProperty("k") String k,
            @JsonProperty("v") String v) {
        return new InfoboxItem(k, v);
    }
}
