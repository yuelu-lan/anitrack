package com.anitrack.infra.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "anitrack.rag")
public class RagProperties {
    private String baseUrl;
    private String internalToken;
    private Integer defaultTopK = 4;
}
