package com.anitrack.starter.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class ReviewAddReq {

    @NotNull(message = "番剧ID不能为空")
    private Long animeId;

    @NotNull(message = "评分不能为空")
    private Integer score;

    private String content;
}
