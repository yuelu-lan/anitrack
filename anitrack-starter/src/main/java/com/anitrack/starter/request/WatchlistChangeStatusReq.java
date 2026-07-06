package com.anitrack.starter.request;

import com.anitrack.domain.watchlist.enums.WatchStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class WatchlistChangeStatusReq {

    @NotNull(message = "番剧ID不能为空")
    private Long animeId;

    @NotNull(message = "追番状态不能为空")
    private WatchStatus status;
}
