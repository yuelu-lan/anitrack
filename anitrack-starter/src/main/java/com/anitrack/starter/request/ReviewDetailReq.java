package com.anitrack.starter.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class ReviewDetailReq {

    @NotNull(message = "番剧ID不能为空")
    private Long animeId;
}
