package com.anitrack.infra.gateway.bangumi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
public class BangumiInfoboxDTO {
    private String key;
    private String valueText;
    private List<InfoboxValueDTO> valueItems;

    @JsonProperty("value")
    public void setValue(Object value) {
        if (value instanceof String s) {
            this.valueText = s;
        } else if (value instanceof List<?> list) {
            this.valueItems = list.stream()
                    .filter(o -> o instanceof java.util.Map<?, ?>)
                    .map(o -> {
                        @SuppressWarnings("unchecked")
                        var m = (java.util.Map<String, Object>) o;
                        return InfoboxValueDTO.of((String) m.get("k"), (String) m.get("v"));
                    })
                    .toList();
        }
    }

    @Data
    public static class InfoboxValueDTO {
        private String k;
        private String v;

        public static InfoboxValueDTO of(String k, String v) {
            InfoboxValueDTO v1 = new InfoboxValueDTO();
            v1.k = k;
            v1.v = v;
            return v1;
        }
    }
}
