package com.anitrack.application.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ReviewPageBO<T> {

    private List<T> list;
    private long total;
    private int page;
    private int pageSize;
}
