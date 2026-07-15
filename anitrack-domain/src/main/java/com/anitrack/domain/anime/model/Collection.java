package com.anitrack.domain.anime.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class Collection {
    private final Integer wish;
    private final Integer collect;
    private final Integer doing;
    private final Integer onHold;
    private final Integer dropped;

    public static Collection of(Integer wish, Integer collect, Integer doing, Integer onHold, Integer dropped) {
        return new Collection(wish, collect, doing, onHold, dropped);
    }

    public static Collection empty() {
        return new Collection(0, 0, 0, 0, 0);
    }
}
