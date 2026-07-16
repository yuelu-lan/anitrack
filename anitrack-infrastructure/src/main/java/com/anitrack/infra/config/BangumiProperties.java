package com.anitrack.infra.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "anitrack.bangumi")
public class BangumiProperties {

    private String baseUrl;
    private String userAgent;
    private Integer connectTimeoutMs;
    private Integer readTimeoutMs;
    private String proxyHost;
    private Integer proxyPort;
}
