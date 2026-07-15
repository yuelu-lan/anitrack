package com.anitrack.domain.anime.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class InfoboxItem {
    private final String k;
    private final String v;

    public static InfoboxItem of(String k, String v) {
        return new InfoboxItem(k, v);
    }
}
