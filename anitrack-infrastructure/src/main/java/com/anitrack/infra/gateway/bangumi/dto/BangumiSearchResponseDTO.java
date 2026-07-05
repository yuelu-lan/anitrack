package com.anitrack.infra.gateway.bangumi.dto;

import lombok.Data;

import java.util.List;

@Data
public class BangumiSearchResponseDTO {

    private Integer total;
    private Integer limit;
    private Integer offset;
    private List<BangumiSubjectDTO> data;
}
