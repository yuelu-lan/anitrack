package com.anitrack.infra.dal.po;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class AnimePO {

    private Long id;
    private Long bangumiId;
    private String titleCn;
    private String titleOriginal;
    private String coverUrl;
    private Integer totalEpisodes;
    private LocalDate airDate;
    private String summary;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
