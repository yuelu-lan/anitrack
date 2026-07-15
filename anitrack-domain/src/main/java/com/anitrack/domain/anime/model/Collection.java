package com.anitrack.domain.anime.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class Collection {
    private final Integer wish;
    private final Integer collect;
    private final Integer doing;
    private final Integer onHold;
    private final Integer dropped;

    private Collection(Integer wish, Integer collect, Integer doing, Integer onHold, Integer dropped) {
        this.wish = wish;
        this.collect = collect;
        this.doing = doing;
        this.onHold = onHold;
        this.dropped = dropped;
    }

    @JsonCreator
    public static Collection of(
            @JsonProperty("wish") Integer wish,
            @JsonProperty("collect") Integer collect,
            @JsonProperty("doing") Integer doing,
            @JsonProperty("onHold") Integer onHold,
            @JsonProperty("dropped") Integer dropped) {
        return new Collection(wish, collect, doing, onHold, dropped);
    }

    public static Collection empty() {
        return new Collection(0, 0, 0, 0, 0);
    }
}
