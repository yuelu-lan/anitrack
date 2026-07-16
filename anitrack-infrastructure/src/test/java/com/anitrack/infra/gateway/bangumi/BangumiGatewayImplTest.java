package com.anitrack.infra.gateway.bangumi;

import com.anitrack.domain.anime.gateway.BangumiGateway;
import com.anitrack.domain.anime.model.Anime;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.springframework.web.client.RestClient;
import java.io.IOException;
import static org.assertj.core.api.Assertions.assertThat;

class BangumiGatewayImplTest {

    private MockWebServer server;
    private BangumiGateway gateway;

    @BeforeEach
    void setup() throws IOException {
        server = new MockWebServer();
        server.start();
        RestClient restClient = RestClient.builder()
                .baseUrl(server.url("/").toString())
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory())
                .build();
        gateway = new BangumiGatewayImpl(restClient, new BangumiConverter());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void getById_returns_full_anime() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                    {"id":1,"type":2,"name":"原名","name_cn":"中文名","summary":"简介",
                     "date":"2024-04-01","platform":"TV","eps":12,"total_episodes":12,
                     "images":{"large":"L","common":"C","medium":"M","small":"S","grid":"G"},
                     "tags":[{"name":"科幻","count":10}],
                     "rating":{"score":8.5,"rank":12,"total":102,"count":{"10":100}},
                     "collection":{"wish":1,"collect":2,"doing":3,"on_hold":4,"dropped":5},
                     "infobox":[{"key":"中文名","value":"代号"}]}
                    """));

        Anime anime = gateway.getById(1L);

        assertThat(anime.getBangumiId()).isEqualTo(1L);
        assertThat(anime.getRating().getScore()).isEqualTo(8.5);
        assertThat(anime.getTags()).hasSize(1);
        assertThat(server.takeRequest().getPath()).isEqualTo("/v0/subjects/1");
    }

    @Test
    void getById_null_when_404() {
        server.enqueue(new MockResponse().setResponseCode(404));
        Anime anime = gateway.getById(999L);
        assertThat(anime).isNull();
    }
}
