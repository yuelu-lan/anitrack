package com.anitrack.infra.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(BangumiProperties.class)
@RequiredArgsConstructor
public class BangumiClientConfig {

    private final BangumiProperties bangumiProperties;

    @Bean
    public RestClient bangumiRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(bangumiProperties.getConnectTimeoutMs());
        requestFactory.setReadTimeout(bangumiProperties.getReadTimeoutMs());

        return RestClient.builder()
            .baseUrl(bangumiProperties.getBaseUrl())
            .defaultHeader(HttpHeaders.USER_AGENT, bangumiProperties.getUserAgent())
            .requestFactory(requestFactory)
            .build();
    }
}
