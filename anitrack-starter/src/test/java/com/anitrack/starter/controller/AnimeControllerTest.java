package com.anitrack.starter.controller;

import com.anitrack.application.model.AnimeBO;
import com.anitrack.application.service.AnimeApplication;
import com.anitrack.infra.auth.JwtTokenProvider;
import com.anitrack.starter.converter.HttpConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnimeController.class)
class AnimeControllerTest {

    private static final String AUTH_HEADER_VALUE = "Bearer test-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AnimeApplication mockAnimeApplication;

    @MockBean
    private HttpConverter mockHttpConverter;

    @MockBean
    private JwtTokenProvider mockJwtTokenProvider;

    private void stubValidToken() {
        when(mockJwtTokenProvider.validateToken(anyString())).thenReturn(true);
        when(mockJwtTokenProvider.getUserId(anyString())).thenReturn(1L);
    }

    private AnimeBO createTestAnimeBO() {
        return AnimeBO.builder()
            .id(1L)
            .bangumiId(100L)
            .titleCn("中文名")
            .titleOriginal("Original Title")
            .coverUrl("http://cover.jpg")
            .totalEpisodes(12)
            .airDate(LocalDate.of(2024, 1, 1))
            .summary("简介")
            .build();
    }

    @Test
    void postSearch_whenNoAuthorizationHeader_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/anime/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("keyword", "关键字"))))
            .andExpect(status().isUnauthorized());

        verify(mockAnimeApplication, never()).searchAnime(any());
    }

    @Test
    void postSearch_whenRequestBodyIsEmptyObject_shouldReturnBadRequest() throws Exception {
        stubValidToken();

        mockMvc.perform(post("/api/anime/search")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void postSearch_whenRequestIsValid_shouldReturnAnimeList() throws Exception {
        // given
        stubValidToken();
        AnimeBO animeBO = createTestAnimeBO();
        when(mockAnimeApplication.searchAnime("关键字")).thenReturn(List.of(animeBO));
        when(mockHttpConverter.animeBOList2Response(List.of(animeBO))).thenCallRealMethod();
        when(mockHttpConverter.animeBO2Response(animeBO)).thenCallRealMethod();

        // when & then
        mockMvc.perform(post("/api/anime/search")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("keyword", "关键字"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(1))
            .andExpect(jsonPath("$.data[0].titleCn").value("中文名"));

        verify(mockAnimeApplication, times(1)).searchAnime("关键字");
    }

    @Test
    void postDetail_whenNoAuthorizationHeader_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/anime/detail")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 1L))))
            .andExpect(status().isUnauthorized());

        verify(mockAnimeApplication, never()).getAnimeDetail(any());
    }

    @Test
    void postDetail_whenRequestBodyIsEmptyObject_shouldReturnBadRequest() throws Exception {
        stubValidToken();

        mockMvc.perform(post("/api/anime/detail")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void postDetail_whenRequestIsValid_shouldReturnAnimeDetail() throws Exception {
        // given
        stubValidToken();
        AnimeBO animeBO = createTestAnimeBO();
        when(mockAnimeApplication.getAnimeDetail(1L)).thenReturn(animeBO);
        when(mockHttpConverter.animeBO2Response(animeBO)).thenCallRealMethod();

        // when & then
        mockMvc.perform(post("/api/anime/detail")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 1L))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(1))
            .andExpect(jsonPath("$.data.titleOriginal").value("Original Title"));

        verify(mockAnimeApplication, times(1)).getAnimeDetail(1L);
    }

    @Test
    void postDetail_whenAnimeNotFound_shouldReturnBusinessError() throws Exception {
        // given
        stubValidToken();
        doThrow(com.anitrack.application.exception.AnitrackAppException.build(
                com.anitrack.application.exception.AppExceptionEnum.ANIME_NOT_FOUND))
            .when(mockAnimeApplication).getAnimeDetail(999L);

        // when & then
        mockMvc.perform(post("/api/anime/detail")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 999L))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(0));
    }
}
