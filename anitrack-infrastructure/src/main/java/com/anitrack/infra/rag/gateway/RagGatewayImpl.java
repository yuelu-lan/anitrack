package com.anitrack.infra.rag.gateway;

import com.anitrack.domain.rag.gateway.RagGateway;
import com.anitrack.domain.rag.model.RagDocument;
import com.anitrack.domain.rag.model.RagDocumentSummary;
import com.anitrack.domain.rag.model.RagQuery;
import com.anitrack.infra.config.RagProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class RagGatewayImpl implements RagGateway {

    private static final Logger log = LoggerFactory.getLogger(RagGatewayImpl.class);

    private final RagProperties properties;
    private final RestClient.Builder restClientBuilder;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public int ingest(List<RagDocument> documents) {
        RestClient client = restClientBuilder.baseUrl(properties.getBaseUrl()).build();
        var body = Map.of("documents", documents.stream()
                .map(d -> Map.of(
                        "pageContent", d.getPageContent(),
                        "metadata", Map.of("animeId", d.getAnimeId(), "title", d.getTitle())))
                .toList());
        var resp = client.post().uri("/ingest")
                .header("X-Internal-Token", properties.getInternalToken())
                .body(body)
                .retrieve()
                .body(IngestResponse.class);
        return resp == null ? 0 : resp.ingested();
    }

    @Override
    public Stream<String> streamQuery(RagQuery query) {
        int topK = query.getTopK() == null ? properties.getDefaultTopK() : query.getTopK();
        String json;
        try {
            json = objectMapper.writeValueAsString(
                    Map.of("question", query.getQuestion(), "topK", topK));
        } catch (JsonProcessingException e) {
            return Stream.of("[ERROR] rag-service 请求体序列化失败: " + e.getMessage());
        }
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(properties.getBaseUrl() + "/query"))
                .header("Content-Type", "application/json")
                .header("X-Internal-Token", properties.getInternalToken())
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<InputStream> resp = httpClient.send(req,
                    HttpResponse.BodyHandlers.ofInputStream());
            CharChunkIterator chunks = new CharChunkIterator(resp.body());
            return StreamSupport.stream(
                    Spliterators.spliteratorUnknownSize(chunks, 0), false)
                    .onClose(chunks::close);
        } catch (Exception e) {
            return Stream.of("[ERROR] rag-service 不可达: " + e.getMessage());
        }
    }

    @Override
    public List<RagDocumentSummary> listDocuments() {
        RestClient client = restClientBuilder.baseUrl(properties.getBaseUrl()).build();
        DocumentsResponse resp = client.get().uri("/documents")
                .header("X-Internal-Token", properties.getInternalToken())
                .retrieve()
                .body(DocumentsResponse.class);
        if (resp == null || resp.documents() == null) {
            return List.of();
        }
        return resp.documents().stream()
                .map(d -> {
                    try {
                        return RagDocumentSummary.of(Long.parseLong(d.animeId()), d.title());
                    } catch (NumberFormatException ex) {
                        log.warn("非法 animeId 跳过: {}", d.animeId());
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private record IngestResponse(int ingested) {}
    private record DocumentsResponse(List<DocumentItem> documents) {}
    private record DocumentItem(String animeId, String title) {}

    private static class CharChunkIterator implements Iterator<String>, AutoCloseable {
        private static final int CHUNK_SIZE = 512;
        private final Reader reader;
        private final char[] buf = new char[CHUNK_SIZE];
        private String next;

        CharChunkIterator(InputStream in) {
            reader = new InputStreamReader(in, StandardCharsets.UTF_8);
        }

        @Override
        public boolean hasNext() {
            try {
                int n = reader.read(buf);
                if (n < 0) {
                    next = null;
                    return false;
                }
                next = new String(buf, 0, n);
            } catch (IOException e) {
                log.error("rag-service 流读取失败", e);
                next = null;
                return false;
            }
            return true;
        }

        @Override
        public String next() {
            return next;
        }

        @Override
        public void close() {
            try {
                reader.close();
            } catch (IOException e) {
                log.error("rag-service 流关闭失败", e);
            }
        }
    }
}
