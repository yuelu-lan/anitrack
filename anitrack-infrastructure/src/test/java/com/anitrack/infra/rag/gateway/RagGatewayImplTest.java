package com.anitrack.infra.rag.gateway;

import com.anitrack.domain.rag.model.RagDocument;
import com.anitrack.domain.rag.model.RagDocumentSummary;
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
        assertThat(result).isEqualTo("你好世界");
    }

    @Test
    void listDocuments_returns_summaries() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"documents\":[{\"animeId\":\"1\",\"title\":\"标题A\"},{\"animeId\":\"2\",\"title\":\"标题B\"}]}"));
        List<RagDocumentSummary> docs = gateway.listDocuments();
        assertThat(docs).hasSize(2);
        assertThat(docs.get(0).getAnimeId()).isEqualTo(1L);
        assertThat(docs.get(0).getTitle()).isEqualTo("标题A");
        assertThat(docs.get(1).getAnimeId()).isEqualTo(2L);
        assertThat(docs.get(1).getTitle()).isEqualTo("标题B");
        var req = server.takeRequest();
        assertThat(req.getMethod()).isEqualTo("GET");
        assertThat(req.getPath()).isEqualTo("/documents");
        assertThat(req.getHeader("X-Internal-Token")).isEqualTo("token");
    }

    @Test
    void listDocuments_handles_null_response() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"documents\":null}"));
        List<RagDocumentSummary> docs = gateway.listDocuments();
        assertThat(docs).isEmpty();
    }

    @Test
    void listDocuments_skips_invalid_animeId() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"documents\":[{\"animeId\":\"1\",\"title\":\"标题A\"},{\"animeId\":\"bad\",\"title\":\"坏条目\"},{\"animeId\":\"3\",\"title\":\"标题C\"}]}"));
        List<RagDocumentSummary> docs = gateway.listDocuments();
        assertThat(docs).hasSize(2);
        assertThat(docs.get(0).getAnimeId()).isEqualTo(1L);
        assertThat(docs.get(0).getTitle()).isEqualTo("标题A");
        assertThat(docs.get(1).getAnimeId()).isEqualTo(3L);
        assertThat(docs.get(1).getTitle()).isEqualTo("标题C");
    }
}
