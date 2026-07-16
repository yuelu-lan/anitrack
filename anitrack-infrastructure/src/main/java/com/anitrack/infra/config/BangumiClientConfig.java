package com.anitrack.infra.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.net.Proxy;

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
        if (StringUtils.hasText(bangumiProperties.getProxyHost()) && bangumiProperties.getProxyPort() != null) {
            requestFactory.setProxy(new Proxy(Proxy.Type.HTTP,
                    new InetSocketAddress(bangumiProperties.getProxyHost(), bangumiProperties.getProxyPort())));
        }

        return RestClient.builder()
            .baseUrl(bangumiProperties.getBaseUrl())
            .defaultHeader(HttpHeaders.USER_AGENT, bangumiProperties.getUserAgent())
            .requestFactory(requestFactory)
            .build();
    }
}
