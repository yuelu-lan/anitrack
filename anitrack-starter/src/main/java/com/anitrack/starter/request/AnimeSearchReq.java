package com.anitrack.starter.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class AnimeSearchReq {

    @NotBlank(message = "搜索关键字不能为空")
    private String keyword;
}
