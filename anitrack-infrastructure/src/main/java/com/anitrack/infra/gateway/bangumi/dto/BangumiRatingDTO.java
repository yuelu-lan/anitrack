package com.anitrack.infra.gateway.bangumi.dto;

import java.util.Map;
import lombok.Data;

@Data
public class BangumiRatingDTO {
    private Double score;
    private Integer rank;
    private Integer total;
    private Map<String, Integer> count;
}
