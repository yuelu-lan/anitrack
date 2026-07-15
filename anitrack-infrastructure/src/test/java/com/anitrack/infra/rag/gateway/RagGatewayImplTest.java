package com.anitrack.infra.rag.gateway;

import com.anitrack.domain.rag.model.RagDocument;
import com.anitrack.domain.rag.model.RagQuery;
import com.anitrack.infra.config.RagProperties;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.springframework.web.client.RestClient;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import static org.assertj.core.api.Assertions.assertThat;

class RagGatewayImplTest {

    private MockWebServer server;
    private RagGatewayImpl gateway;

    @BeforeEach
    void setup() throws IOException {
        server = new MockWebServer();
        server.start();
        RagProperties props = new RagProperties();
        props.setBaseUrl(server.url("/").toString());
        props.setInternalToken("token");
        gateway = new RagGatewayImpl(props, RestClient.builder());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void ingest_posts_documents() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"ingested\":2}"));
        int n = gateway.ingest(List.of(
                RagDocument.of("内容A", "1", "标题A"),
                RagDocument.of("内容B", "2", "标题B")));
        assertThat(n).isEqualTo(2);
        assertThat(server.takeRequest().getHeader("X-Internal-Token")).isEqualTo("token");
    }

    @Test
    void streamQuery_returns_chunks() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("你好世界"));
        String result = gateway.streamQuery(RagQuery.of("你好", null))
                .collect(Collectors.joining());
        assertThat(result).isEqualTo("你好世界\n");
    }
}
