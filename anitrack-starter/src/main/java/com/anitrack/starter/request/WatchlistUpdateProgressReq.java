package com.anitrack.starter.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class WatchlistUpdateProgressReq {

    @NotNull(message = "番剧ID不能为空")
    private Long animeId;

    @NotNull(message = "观看进度不能为空")
    private Integer episode;
}
