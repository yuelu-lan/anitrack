package com.anitrack.infra.gateway.bangumi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class BangumiCollectionDTO {
    private Integer wish;
    private Integer collect;
    private Integer doing;
    @JsonProperty("on_hold")
    private Integer onHold;
    private Integer dropped;
}
