package com.anitrack.starter.controller;

import com.anitrack.application.service.RagApplication;
import com.anitrack.infra.auth.JwtTokenProvider;
import com.anitrack.starter.converter.HttpConverter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.stream.Stream;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RagChatController.class)
class RagChatControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean RagApplication ragApplication;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean HttpConverter httpConverter;

    @Test
    void chat_streams_text() throws Exception {
        when(jwtTokenProvider.validateToken(any())).thenReturn(true);
        when(jwtTokenProvider.getUserId(any())).thenReturn(1L);
        when(ragApplication.streamChat(any())).thenReturn(Stream.of("你", "好"));

        mockMvc.perform(post("/api/rag/chat")
                        .header("Authorization", "Bearer test-token")
                        .contentType("application/json")
                        .content("{\"message\":\"你好\"}"))
                .andExpect(status().isOk());
    }
}
