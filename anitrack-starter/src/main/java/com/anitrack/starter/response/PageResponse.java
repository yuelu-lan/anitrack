package com.anitrack.starter.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PageResponse<T> {

    private Integer pageNum;
    private Integer pageSize;
    private Long total;
    private List<T> list;
}
