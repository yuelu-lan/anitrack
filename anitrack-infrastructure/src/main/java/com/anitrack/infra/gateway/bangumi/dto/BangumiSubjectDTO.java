package com.anitrack.infra.gateway.bangumi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class BangumiSubjectDTO {

    private Long id;
    private String name;

    @JsonProperty("name_cn")
    private String nameCn;

    private String summary;
    private String date;
    private Integer eps;

    @JsonProperty("total_episodes")
    private Integer totalEpisodes;

    private BangumiImagesDTO images;
}
