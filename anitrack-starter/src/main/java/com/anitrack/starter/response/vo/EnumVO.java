package com.anitrack.starter.response.vo;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EnumVO {
    private final Integer code;
    private final String name;
}
