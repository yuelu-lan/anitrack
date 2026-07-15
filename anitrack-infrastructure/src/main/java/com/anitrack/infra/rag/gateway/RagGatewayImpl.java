package com.anitrack.infra.rag.gateway;

import com.anitrack.domain.rag.gateway.RagGateway;
import com.anitrack.domain.rag.model.RagDocument;
import com.anitrack.domain.rag.model.RagQuery;
import com.anitrack.infra.config.RagProperties;
import java.io.IOException;
import java.io.InputStream;
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
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class RagGatewayImpl implements RagGateway {

    private final RagProperties properties;
    private final RestClient.Builder restClientBuilder;

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
        HttpClient http = HttpClient.newHttpClient();
        String json = "{\"question\":\"" + escape(query.getQuestion()) + "\","
                + "\"topK\":" + (query.getTopK() == null ? 4 : query.getTopK()) + "}";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(properties.getBaseUrl() + "/query"))
                .header("Content-Type", "application/json")
                .header("X-Internal-Token", properties.getInternalToken())
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<InputStream> resp = http.send(req,
                    HttpResponse.BodyHandlers.ofInputStream());
            return StreamSupport.stream(
                    Spliterators.spliteratorUnknownSize(new BufferedReaderLines(resp.body()), 0),
                    false);
        } catch (Exception e) {
            return Stream.of("[ERROR] rag-service 不可达: " + e.getMessage());
        }
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private record IngestResponse(int ingested) {}

    private static class BufferedReaderLines implements Iterator<String> {
        private final java.io.BufferedReader reader;
        private String next;
        BufferedReaderLines(InputStream in) {
            reader = new java.io.BufferedReader(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        }
        @Override
        public boolean hasNext() {
            try {
                next = reader.readLine();
            } catch (IOException e) {
                next = null;
            }
            return next != null;
        }
        @Override
        public String next() {
            return next + "\n";
        }
    }
}
