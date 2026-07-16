package com.anitrack.domain.anime.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;

@Getter
public class Infobox {
    private final String key;
    private final String valueText;
    private final List<InfoboxItem> valueItems;

    @JsonCreator
    private Infobox(
            @JsonProperty("key") String key,
            @JsonProperty("valueText") String valueText,
            @JsonProperty("valueItems") List<InfoboxItem> valueItems) {
        this.key = key;
        this.valueText = valueText;
        this.valueItems = valueItems;
    }

    public static Infobox ofText(String key, String valueText) {
        return new Infobox(key, valueText, List.of());
    }

    public static Infobox ofItems(String key, List<InfoboxItem> valueItems) {
        return new Infobox(key, null, valueItems);
    }
}
