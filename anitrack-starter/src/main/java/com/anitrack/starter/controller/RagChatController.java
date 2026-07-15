package com.anitrack.starter.controller;

import com.anitrack.application.service.RagApplication;
import com.anitrack.domain.rag.model.RagQuery;
import com.anitrack.starter.request.RagChatReq;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Slf4j
@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagChatController {

    private final RagApplication ragApplication;

    @PostMapping(value = "/chat", produces = "text/plain")
    public StreamingResponseBody chat(@Valid @RequestBody RagChatReq req) {
        return out -> {
            try {
                ragApplication.streamChat(RagQuery.of(req.getMessage(), null))
                        .forEach(token -> {
                            try {
                                out.write(token.getBytes(StandardCharsets.UTF_8));
                                out.flush();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
            } catch (Exception e) {
                log.error("RAG 流式透传失败", e);
            }
        };
    }
}
