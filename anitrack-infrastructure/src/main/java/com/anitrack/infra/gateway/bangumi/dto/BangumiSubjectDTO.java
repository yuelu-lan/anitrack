package com.anitrack.infra.gateway.bangumi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
public class BangumiSubjectDTO {

    private Long id;
    private Integer type;
    private String name;

    @JsonProperty("name_cn")
    private String nameCn;

    private String summary;
    private String date;
    private Boolean nsfw;
    private Boolean locked;
    private Boolean series;
    private String platform;
    private Integer eps;

    @JsonProperty("total_episodes")
    private Integer totalEpisodes;

    private Integer volumes;

    @JsonProperty("meta_tags")
    private List<String> metaTags;

    private BangumiImagesDTO images;
    private List<BangumiTagDTO> tags;
    private BangumiRatingDTO rating;
    private BangumiCollectionDTO collection;
    private List<BangumiInfoboxDTO> infobox;
}
