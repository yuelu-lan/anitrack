package com.anitrack.domain.rag.gateway;

import com.anitrack.domain.rag.model.RagDocument;
import com.anitrack.domain.rag.model.RagQuery;
import java.util.List;
import java.util.stream.Stream;

public interface RagGateway {
    int ingest(List<RagDocument> documents);
    Stream<String> streamQuery(RagQuery query);
}
