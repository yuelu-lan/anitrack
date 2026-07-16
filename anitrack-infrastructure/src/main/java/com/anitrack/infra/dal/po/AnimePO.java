package com.anitrack.infra.dal.po;

import com.anitrack.domain.anime.model.AnimeImages;
import com.anitrack.domain.anime.model.AnimeTag;
import com.anitrack.domain.anime.model.Collection;
import com.anitrack.domain.anime.model.Infobox;
import com.anitrack.domain.anime.model.Rating;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class AnimePO {

    private Long id;
    private Long bangumiId;
    private Integer type;
    private String titleCn;
    private String titleOriginal;
    private String summary;
    private Boolean nsfw;
    private Boolean locked;
    private Boolean series;
    private LocalDate airDate;
    private String platform;
    private String coverUrl;
    private AnimeImages images;
    private Integer eps;
    private Integer totalEpisodes;
    private Integer volumes;
    private List<String> metaTags;
    private List<AnimeTag> tags;
    private Rating rating;
    private Collection collection;
    private List<Infobox> infobox;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
