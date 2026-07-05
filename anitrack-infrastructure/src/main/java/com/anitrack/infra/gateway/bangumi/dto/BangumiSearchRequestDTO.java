package com.anitrack.infra.gateway.bangumi.dto;

import lombok.Data;

import java.util.List;

@Data
public class BangumiSearchRequestDTO {

    private String keyword;
    private Filter filter;

    @Data
    public static class Filter {
        private List<Integer> type;
    }

    public static BangumiSearchRequestDTO forAnimeKeyword(String keyword) {
        BangumiSearchRequestDTO request = new BangumiSearchRequestDTO();
        request.setKeyword(keyword);
        Filter filter = new Filter();
        filter.setType(List.of(2));
        request.setFilter(filter);
        return request;
    }
}
