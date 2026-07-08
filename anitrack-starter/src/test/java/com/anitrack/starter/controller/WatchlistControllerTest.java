package com.anitrack.starter.controller;

import com.anitrack.application.model.WatchlistItemBO;
import com.anitrack.application.model.WatchlistItemViewBO;
import com.anitrack.application.service.WatchlistApplication;
import com.anitrack.domain.watchlist.enums.WatchStatus;
import com.anitrack.infra.auth.JwtTokenProvider;
import com.anitrack.starter.converter.HttpConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WatchlistController.class)
class WatchlistControllerTest {

    private static final String AUTH_HEADER_VALUE = "Bearer test-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WatchlistApplication mockWatchlistApplication;

    @MockBean
    private HttpConverter mockHttpConverter;

    @MockBean
    private JwtTokenProvider mockJwtTokenProvider;

    private void stubValidToken() {
        when(mockJwtTokenProvider.validateToken(anyString())).thenReturn(true);
        when(mockJwtTokenProvider.getUserId(anyString())).thenReturn(1L);
    }

    private WatchlistItemBO createTestItemBO() {
        return WatchlistItemBO.builder()
            .id(10L)
            .animeId(100L)
            .status(WatchStatus.WANT_TO_WATCH)
            .currentEpisode(0)
            .build();
    }

    @Test
    void add_whenNoAuthorizationHeader_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/watchlist/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L))))
            .andExpect(status().isUnauthorized());

        verify(mockWatchlistApplication, never()).addToWatchlist(any(), any());
    }

    @Test
    void add_whenAnimeIdMissing_shouldReturnBadRequest() throws Exception {
        stubValidToken();

        mockMvc.perform(post("/api/watchlist/add")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void add_whenRequestIsValid_shouldReturnWatchlistItem() throws Exception {
        // given
        stubValidToken();
        WatchlistItemBO bo = createTestItemBO();
        when(mockWatchlistApplication.addToWatchlist(1L, 100L)).thenReturn(bo);
        when(mockHttpConverter.watchlistItemBO2Response(bo)).thenCallRealMethod();
        when(mockHttpConverter.watchStatus2VO(any(WatchStatus.class))).thenCallRealMethod();

        // when & then
        mockMvc.perform(post("/api/watchlist/add")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(1))
            .andExpect(jsonPath("$.data.status.name").value("WANT_TO_WATCH"))
            .andExpect(jsonPath("$.data.status.code").value(1));

        verify(mockWatchlistApplication, times(1)).addToWatchlist(1L, 100L);
    }

    @Test
    void add_whenAlreadyExists_shouldReturnBusinessError() throws Exception {
        // given
        stubValidToken();
        doThrow(com.anitrack.application.exception.AnitrackAppException.build(
                com.anitrack.application.exception.AppExceptionEnum.WATCHLIST_ITEM_ALREADY_EXISTS))
            .when(mockWatchlistApplication).addToWatchlist(1L, 100L);

        // when & then
        mockMvc.perform(post("/api/watchlist/add")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(0));
    }

    @Test
    void changeStatus_whenNoAuthorizationHeader_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/watchlist/change_status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L, "status", "WATCHING"))))
            .andExpect(status().isUnauthorized());

        verify(mockWatchlistApplication, never()).changeStatus(any(), any(), any());
    }

    @Test
    void changeStatus_whenStatusMissing_shouldReturnBadRequest() throws Exception {
        stubValidToken();

        mockMvc.perform(post("/api/watchlist/change_status")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void changeStatus_whenRequestIsValid_shouldReturnWatchlistItem() throws Exception {
        // given
        stubValidToken();
        WatchlistItemBO bo = WatchlistItemBO.builder()
            .id(10L).animeId(100L).status(WatchStatus.WATCHING).currentEpisode(0).build();
        when(mockWatchlistApplication.changeStatus(1L, 100L, WatchStatus.WATCHING)).thenReturn(bo);
        when(mockHttpConverter.watchlistItemBO2Response(bo)).thenCallRealMethod();
        when(mockHttpConverter.watchStatus2VO(any(WatchStatus.class))).thenCallRealMethod();

        // when & then
        mockMvc.perform(post("/api/watchlist/change_status")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L, "status", "WATCHING"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(1))
            .andExpect(jsonPath("$.data.status.name").value("WATCHING"))
            .andExpect(jsonPath("$.data.status.code").value(2));

        verify(mockWatchlistApplication, times(1)).changeStatus(1L, 100L, WatchStatus.WATCHING);
    }

    @Test
    void changeStatus_whenIllegalTransition_shouldReturnBusinessError() throws Exception {
        // given
        stubValidToken();
        doThrow(com.anitrack.application.exception.AnitrackAppException.build(
                com.anitrack.application.exception.AppExceptionEnum.ILLEGAL_WATCH_STATUS_TRANSITION))
            .when(mockWatchlistApplication).changeStatus(1L, 100L, WatchStatus.WATCHING);

        // when & then
        mockMvc.perform(post("/api/watchlist/change_status")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L, "status", "WATCHING"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(0));
    }

    @Test
    void updateProgress_whenNoAuthorizationHeader_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/watchlist/update_progress")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L, "episode", 5))))
            .andExpect(status().isUnauthorized());

        verify(mockWatchlistApplication, never()).updateProgress(any(), any(), any());
    }

    @Test
    void updateProgress_whenEpisodeMissing_shouldReturnBadRequest() throws Exception {
        stubValidToken();

        mockMvc.perform(post("/api/watchlist/update_progress")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void updateProgress_whenRequestIsValid_shouldReturnWatchlistItem() throws Exception {
        // given
        stubValidToken();
        WatchlistItemBO bo = WatchlistItemBO.builder()
            .id(10L).animeId(100L).status(WatchStatus.WATCHING).currentEpisode(5).build();
        when(mockWatchlistApplication.updateProgress(1L, 100L, 5)).thenReturn(bo);
        when(mockHttpConverter.watchlistItemBO2Response(bo)).thenCallRealMethod();
        when(mockHttpConverter.watchStatus2VO(any(WatchStatus.class))).thenCallRealMethod();

        // when & then
        mockMvc.perform(post("/api/watchlist/update_progress")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L, "episode", 5))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(1))
            .andExpect(jsonPath("$.data.currentEpisode").value(5));

        verify(mockWatchlistApplication, times(1)).updateProgress(1L, 100L, 5);
    }

    @Test
    void updateProgress_whenIllegalProgress_shouldReturnBusinessError() throws Exception {
        // given
        stubValidToken();
        doThrow(com.anitrack.application.exception.AnitrackAppException.build(
                com.anitrack.application.exception.AppExceptionEnum.ILLEGAL_WATCH_PROGRESS))
            .when(mockWatchlistApplication).updateProgress(1L, 100L, -1);

        // when & then
        mockMvc.perform(post("/api/watchlist/update_progress")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L, "episode", -1))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(0));
    }

    @Test
    void detail_whenNoAuthorizationHeader_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/watchlist/detail")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L))))
            .andExpect(status().isUnauthorized());

        verify(mockWatchlistApplication, never()).getWatchlistItem(any(), any());
    }

    @Test
    void detail_whenAnimeIdMissing_shouldReturnBadRequest() throws Exception {
        stubValidToken();

        mockMvc.perform(post("/api/watchlist/detail")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void detail_whenRequestIsValid_shouldReturnWatchlistItem() throws Exception {
        // given
        stubValidToken();
        WatchlistItemBO bo = createTestItemBO();
        when(mockWatchlistApplication.getWatchlistItem(1L, 100L)).thenReturn(bo);
        when(mockHttpConverter.watchlistItemBO2Response(bo)).thenCallRealMethod();
        when(mockHttpConverter.watchStatus2VO(any(WatchStatus.class))).thenCallRealMethod();

        // when & then
        mockMvc.perform(post("/api/watchlist/detail")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(1))
            .andExpect(jsonPath("$.data.animeId").value(100L));

        verify(mockWatchlistApplication, times(1)).getWatchlistItem(1L, 100L);
    }

    @Test
    void detail_whenNotFound_shouldReturnBusinessError() throws Exception {
        // given
        stubValidToken();
        doThrow(com.anitrack.application.exception.AnitrackAppException.build(
                com.anitrack.application.exception.AppExceptionEnum.WATCHLIST_ITEM_NOT_FOUND))
            .when(mockWatchlistApplication).getWatchlistItem(1L, 999L);

        // when & then
        mockMvc.perform(post("/api/watchlist/detail")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 999L))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(0));
    }

    @Test
    void list_whenNoAuthorizationHeader_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/watchlist/list")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());

        verify(mockWatchlistApplication, never()).listMyWatchlist(any(), any());
    }

    @Test
    void list_whenRequestIsValid_shouldReturnWatchlistItemViewList() throws Exception {
        // given
        stubValidToken();
        WatchlistItemViewBO viewBO = WatchlistItemViewBO.builder()
            .id(10L).animeId(100L).animeTitleCn("中文名").status(WatchStatus.WATCHING).currentEpisode(5).build();
        when(mockWatchlistApplication.listMyWatchlist(1L, WatchStatus.WATCHING)).thenReturn(List.of(viewBO));
        when(mockHttpConverter.watchlistItemViewBOList2Response(List.of(viewBO))).thenCallRealMethod();
        when(mockHttpConverter.watchlistItemViewBO2Response(viewBO)).thenCallRealMethod();
        when(mockHttpConverter.watchStatus2VO(any(WatchStatus.class))).thenCallRealMethod();

        // when & then
        mockMvc.perform(post("/api/watchlist/list")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", "WATCHING"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(1))
            .andExpect(jsonPath("$.data[0].animeTitleCn").value("中文名"));

        verify(mockWatchlistApplication, times(1)).listMyWatchlist(1L, WatchStatus.WATCHING);
    }
}
