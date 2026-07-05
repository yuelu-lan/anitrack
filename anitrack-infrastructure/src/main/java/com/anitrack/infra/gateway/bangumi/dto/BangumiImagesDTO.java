package com.anitrack.infra.gateway.bangumi.dto;

import lombok.Data;

@Data
public class BangumiImagesDTO {

    private String large;
    private String common;
    private String medium;
    private String small;
    private String grid;
}
