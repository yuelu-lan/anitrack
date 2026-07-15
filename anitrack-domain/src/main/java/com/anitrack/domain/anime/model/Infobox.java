package com.anitrack.domain.anime.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class Infobox {
    private final String key;
    private final String valueText;
    private final List<InfoboxItem> valueItems;

    public static Infobox ofText(String key, String valueText) {
        return new Infobox(key, valueText, List.of());
    }

    public static Infobox ofItems(String key, List<InfoboxItem> valueItems) {
        return new Infobox(key, null, valueItems);
    }
}
