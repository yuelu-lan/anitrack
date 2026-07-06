package com.anitrack.starter.controller;

import com.anitrack.application.model.ReviewBO;
import com.anitrack.application.model.ReviewPageBO;
import com.anitrack.application.model.ReviewWithAnimeViewBO;
import com.anitrack.application.model.ReviewWithUserViewBO;
import com.anitrack.application.service.ReviewApplication;
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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReviewController.class)
class ReviewControllerTest {

    private static final String AUTH_HEADER_VALUE = "Bearer test-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReviewApplication mockReviewApplication;

    @MockBean
    private HttpConverter mockHttpConverter;

    @MockBean
    private JwtTokenProvider mockJwtTokenProvider;

    private void stubValidToken() {
        when(mockJwtTokenProvider.validateToken(anyString())).thenReturn(true);
        when(mockJwtTokenProvider.getUserId(anyString())).thenReturn(1L);
    }

    private ReviewBO createTestReviewBO() {
        return ReviewBO.builder().id(20L).animeId(100L).score(8).content("很好看").build();
    }

    @Test
    void add_whenNoAuthorizationHeader_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/review/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L, "score", 8))))
            .andExpect(status().isUnauthorized());

        verify(mockReviewApplication, never()).addReview(any(), any(), any(), any());
    }

    @Test
    void add_whenScoreMissing_shouldReturnBadRequest() throws Exception {
        stubValidToken();

        mockMvc.perform(post("/api/review/add")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void add_whenRequestIsValid_shouldReturnReview() throws Exception {
        // given
        stubValidToken();
        ReviewBO bo = createTestReviewBO();
        when(mockReviewApplication.addReview(1L, 100L, 8, "很好看")).thenReturn(bo);
        when(mockHttpConverter.reviewBO2Response(bo)).thenCallRealMethod();

        // when & then
        mockMvc.perform(post("/api/review/add")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L, "score", 8, "content", "很好看"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(1))
            .andExpect(jsonPath("$.data.score").value(8));

        verify(mockReviewApplication, times(1)).addReview(1L, 100L, 8, "很好看");
    }

    @Test
    void add_whenNotAllowed_shouldReturnBusinessError() throws Exception {
        // given
        stubValidToken();
        doThrow(new com.anitrack.application.exception.AnitrackAppException(
                com.anitrack.application.exception.AppExceptionEnum.REVIEW_NOT_ALLOWED))
            .when(mockReviewApplication).addReview(1L, 100L, 8, null);

        // when & then
        mockMvc.perform(post("/api/review/add")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L, "score", 8))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(0));
    }

    @Test
    void update_whenNoAuthorizationHeader_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/review/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L, "score", 5))))
            .andExpect(status().isUnauthorized());

        verify(mockReviewApplication, never()).updateReview(any(), any(), any(), any());
    }

    @Test
    void update_whenRequestIsValid_shouldReturnReview() throws Exception {
        // given
        stubValidToken();
        ReviewBO bo = ReviewBO.builder().id(20L).animeId(100L).score(5).content("重新打分").build();
        when(mockReviewApplication.updateReview(1L, 100L, 5, "重新打分")).thenReturn(bo);
        when(mockHttpConverter.reviewBO2Response(bo)).thenCallRealMethod();

        // when & then
        mockMvc.perform(post("/api/review/update")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L, "score", 5, "content", "重新打分"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(1))
            .andExpect(jsonPath("$.data.score").value(5));

        verify(mockReviewApplication, times(1)).updateReview(1L, 100L, 5, "重新打分");
    }

    @Test
    void update_whenNotFound_shouldReturnBusinessError() throws Exception {
        // given
        stubValidToken();
        doThrow(new com.anitrack.application.exception.AnitrackAppException(
                com.anitrack.application.exception.AppExceptionEnum.REVIEW_NOT_FOUND))
            .when(mockReviewApplication).updateReview(1L, 999L, 5, null);

        // when & then
        mockMvc.perform(post("/api/review/update")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 999L, "score", 5))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(0));
    }

    @Test
    void detail_whenNoAuthorizationHeader_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/review/detail")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L))))
            .andExpect(status().isUnauthorized());

        verify(mockReviewApplication, never()).getMyReview(any(), any());
    }

    @Test
    void detail_whenAnimeIdMissing_shouldReturnBadRequest() throws Exception {
        stubValidToken();

        mockMvc.perform(post("/api/review/detail")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void detail_whenRequestIsValid_shouldReturnReview() throws Exception {
        // given
        stubValidToken();
        ReviewBO bo = createTestReviewBO();
        when(mockReviewApplication.getMyReview(1L, 100L)).thenReturn(bo);
        when(mockHttpConverter.reviewBO2Response(bo)).thenCallRealMethod();

        // when & then
        mockMvc.perform(post("/api/review/detail")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(1))
            .andExpect(jsonPath("$.data.animeId").value(100L));

        verify(mockReviewApplication, times(1)).getMyReview(1L, 100L);
    }

    @Test
    void detail_whenNotFound_shouldReturnBusinessError() throws Exception {
        // given
        stubValidToken();
        doThrow(new com.anitrack.application.exception.AnitrackAppException(
                com.anitrack.application.exception.AppExceptionEnum.REVIEW_NOT_FOUND))
            .when(mockReviewApplication).getMyReview(1L, 999L);

        // when & then
        mockMvc.perform(post("/api/review/detail")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 999L))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(0));
    }

    @Test
    void listByAnime_whenNoAuthorizationHeader_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/review/list_by_anime")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L))))
            .andExpect(status().isUnauthorized());

        verify(mockReviewApplication, never()).listByAnime(any(), anyInt(), anyInt());
    }

    @Test
    void listByAnime_whenAnimeIdMissing_shouldReturnBadRequest() throws Exception {
        stubValidToken();

        mockMvc.perform(post("/api/review/list_by_anime")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void listByAnime_whenPageAndPageSizeOmitted_shouldUseDefaults() throws Exception {
        // given
        stubValidToken();
        ReviewWithUserViewBO viewBO = ReviewWithUserViewBO.builder().id(20L).userId(1L).userNickname("Bob").build();
        ReviewPageBO<ReviewWithUserViewBO> pageBO = ReviewPageBO.<ReviewWithUserViewBO>builder()
            .list(List.of(viewBO)).total(1L).page(1).pageSize(10).build();
        when(mockReviewApplication.listByAnime(100L, 1, 10)).thenReturn(pageBO);
        when(mockHttpConverter.reviewPageBO2Response(pageBO)).thenCallRealMethod();
        when(mockHttpConverter.reviewWithUserViewBO2Response(viewBO)).thenCallRealMethod();

        // when & then
        mockMvc.perform(post("/api/review/list_by_anime")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(1))
            .andExpect(jsonPath("$.data.pageNum").value(1))
            .andExpect(jsonPath("$.data.pageSize").value(10))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.list[0].userNickname").value("Bob"));

        verify(mockReviewApplication, times(1)).listByAnime(100L, 1, 10);
    }

    @Test
    void listByAnime_whenPageAndPageSizeProvided_shouldUseProvidedValues() throws Exception {
        // given
        stubValidToken();
        ReviewPageBO<ReviewWithUserViewBO> pageBO = ReviewPageBO.<ReviewWithUserViewBO>builder()
            .list(List.of()).total(0L).page(2).pageSize(5).build();
        when(mockReviewApplication.listByAnime(100L, 2, 5)).thenReturn(pageBO);
        when(mockHttpConverter.reviewPageBO2Response(pageBO)).thenCallRealMethod();

        // when & then
        mockMvc.perform(post("/api/review/list_by_anime")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L, "page", 2, "pageSize", 5))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(1));

        verify(mockReviewApplication, times(1)).listByAnime(100L, 2, 5);
    }

    @Test
    void listByAnime_whenPageIsZero_shouldReturnBadRequest() throws Exception {
        stubValidToken();

        mockMvc.perform(post("/api/review/list_by_anime")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L, "page", 0))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void listByAnime_whenPageIsNegative_shouldReturnBadRequest() throws Exception {
        stubValidToken();

        mockMvc.perform(post("/api/review/list_by_anime")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L, "page", -1))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void listByAnime_whenPageSizeIsZero_shouldReturnBadRequest() throws Exception {
        stubValidToken();

        mockMvc.perform(post("/api/review/list_by_anime")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L, "pageSize", 0))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void listByAnime_whenPageSizeExceedsMax_shouldReturnBadRequest() throws Exception {
        stubValidToken();

        mockMvc.perform(post("/api/review/list_by_anime")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L, "pageSize", 101))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void myList_whenNoAuthorizationHeader_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/review/my_list")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());

        verify(mockReviewApplication, never()).listMyReviews(any());
    }

    @Test
    void myList_whenRequestIsValid_shouldReturnReviewWithAnimeList() throws Exception {
        // given
        stubValidToken();
        ReviewWithAnimeViewBO viewBO = ReviewWithAnimeViewBO.builder()
            .id(20L).animeId(100L).animeTitleCn("中文名").score(8).build();
        when(mockReviewApplication.listMyReviews(1L)).thenReturn(List.of(viewBO));
        when(mockHttpConverter.reviewWithAnimeViewBOList2Response(List.of(viewBO))).thenCallRealMethod();
        when(mockHttpConverter.reviewWithAnimeViewBO2Response(viewBO)).thenCallRealMethod();

        // when & then
        mockMvc.perform(post("/api/review/my_list")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(1))
            .andExpect(jsonPath("$.data[0].animeTitleCn").value("中文名"));

        verify(mockReviewApplication, times(1)).listMyReviews(1L);
    }
}
