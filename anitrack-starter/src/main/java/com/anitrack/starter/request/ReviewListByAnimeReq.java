package com.anitrack.starter.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class ReviewListByAnimeReq {

    @NotNull(message = "番剧ID不能为空")
    private Long animeId;

    @Min(value = 1, message = "页码必须大于0")
    private Integer page;

    @Min(value = 1, message = "每页数量必须大于0")
    @Max(value = 100, message = "每页数量不能超过100")
    private Integer pageSize;
}
