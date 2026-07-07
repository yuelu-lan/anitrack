package com.anitrack.application.service;

import com.anitrack.application.assembler.ReviewAssembler;
import com.anitrack.application.converter.ReviewBOConverter;
import com.anitrack.application.exception.AnitrackAppException;
import com.anitrack.application.model.ReviewBO;
import com.anitrack.application.model.ReviewPageBO;
import com.anitrack.application.model.ReviewWithAnimeViewBO;
import com.anitrack.application.model.ReviewWithUserViewBO;
import com.anitrack.domain.anime.model.Anime;
import com.anitrack.domain.anime.repo.AnimeRepo;
import com.anitrack.domain.review.exception.IllegalReviewScoreException;
import com.anitrack.domain.review.exception.ReviewAlreadyExistsException;
import com.anitrack.domain.review.exception.ReviewNotAllowedException;
import com.anitrack.domain.review.model.Review;
import com.anitrack.domain.review.repo.ReviewRepo;
import com.anitrack.domain.review.service.ReviewDomainService;
import com.anitrack.domain.user.enums.UserRole;
import com.anitrack.domain.user.model.User;
import com.anitrack.domain.user.repo.UserRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewApplicationTest {

    @Mock
    private ReviewDomainService mockReviewDomainService;

    @Mock
    private ReviewRepo mockReviewRepo;

    @Mock
    private UserRepo mockUserRepo;

    @Mock
    private AnimeRepo mockAnimeRepo;

    @Mock
    private ReviewAssembler mockReviewAssembler;

    @Mock
    private ReviewBOConverter mockReviewBOConverter;

    @InjectMocks
    private ReviewApplication sut;

    @Test
    void addReview_whenSucceeds_shouldReturnBO() {
        // given
        Review review = Review.reconstitute(20L, 1L, 100L, 8, "很好看", null);
        when(mockReviewDomainService.addReview(1L, 100L, 8, "很好看")).thenReturn(review);
        ReviewBO expectedBO = ReviewBO.builder().id(20L).score(8).build();
        when(mockReviewBOConverter.review2BO(review)).thenReturn(expectedBO);

        // when
        ReviewBO result = sut.addReview(1L, 100L, 8, "很好看");

        // then
        assertThat(result.getId()).isEqualTo(20L);
        assertThat(result.getScore()).isEqualTo(8);
    }

    @Test
    void addReview_whenNotAllowed_shouldThrowAppException() {
        // given
        when(mockReviewDomainService.addReview(1L, 100L, 8, "很好看"))
            .thenThrow(new ReviewNotAllowedException(1L, 100L));

        // when & then
        assertThatThrownBy(() -> sut.addReview(1L, 100L, 8, "很好看"))
            .isInstanceOf(AnitrackAppException.class)
            .hasMessageContaining("只有看完的番剧才能评价");
    }

    @Test
    void addReview_whenAlreadyExists_shouldThrowAppException() {
        // given
        when(mockReviewDomainService.addReview(1L, 100L, 8, "很好看"))
            .thenThrow(new ReviewAlreadyExistsException(1L, 100L));

        // when & then
        assertThatThrownBy(() -> sut.addReview(1L, 100L, 8, "很好看"))
            .isInstanceOf(AnitrackAppException.class)
            .hasMessageContaining("该番剧已评价");
    }

    @Test
    void addReview_whenScoreIllegal_shouldThrowAppException() {
        // given
        when(mockReviewDomainService.addReview(1L, 100L, 11, "很好看"))
            .thenThrow(new IllegalReviewScoreException("评分必须在1-10之间"));

        // when & then
        assertThatThrownBy(() -> sut.addReview(1L, 100L, 11, "很好看"))
            .isInstanceOf(AnitrackAppException.class)
            .hasMessageContaining("评分必须在1-10之间");
    }

    @Test
    void updateReview_whenExists_shouldUpdateAndReturnBO() {
        // given
        Review review = Review.reconstitute(20L, 1L, 100L, 8, "很好看", null);
        when(mockReviewRepo.getByUserAndAnime(1L, 100L)).thenReturn(review);
        ReviewBO expectedBO = ReviewBO.builder().score(5).content("重新打分").build();
        when(mockReviewBOConverter.review2BO(review)).thenReturn(expectedBO);

        // when
        ReviewBO result = sut.updateReview(1L, 100L, 5, "重新打分");

        // then
        assertThat(result.getScore()).isEqualTo(5);
        assertThat(result.getContent()).isEqualTo("重新打分");
    }

    @Test
    void updateReview_whenNotFound_shouldThrowAppException() {
        // given
        when(mockReviewRepo.getByUserAndAnime(1L, 100L)).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> sut.updateReview(1L, 100L, 5, "重新打分"))
            .isInstanceOf(AnitrackAppException.class)
            .hasMessageContaining("评价记录不存在");
    }

    @Test
    void updateReview_whenScoreIllegal_shouldThrowAppException() {
        // given
        Review review = Review.reconstitute(20L, 1L, 100L, 8, "很好看", null);
        when(mockReviewRepo.getByUserAndAnime(1L, 100L)).thenReturn(review);

        // when & then
        assertThatThrownBy(() -> sut.updateReview(1L, 100L, 11, "重新打分"))
            .isInstanceOf(AnitrackAppException.class)
            .hasMessageContaining("评分必须在1-10之间");
    }

    @Test
    void getMyReview_whenExists_shouldReturnBO() {
        // given
        Review review = Review.reconstitute(20L, 1L, 100L, 8, "很好看", null);
        when(mockReviewRepo.getByUserAndAnime(1L, 100L)).thenReturn(review);
        ReviewBO expectedBO = ReviewBO.builder().id(20L).build();
        when(mockReviewBOConverter.review2BO(review)).thenReturn(expectedBO);

        // when
        ReviewBO result = sut.getMyReview(1L, 100L);

        // then
        assertThat(result.getId()).isEqualTo(20L);
    }

    @Test
    void getMyReview_whenNotFound_shouldThrowAppException() {
        // given
        when(mockReviewRepo.getByUserAndAnime(1L, 100L)).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> sut.getMyReview(1L, 100L))
            .isInstanceOf(AnitrackAppException.class)
            .hasMessageContaining("评价记录不存在");
    }

    @Test
    void listByAnime_whenCalled_shouldFetchUsersAndAssembleWithPaging() {
        // given
        Review review = Review.reconstitute(20L, 1L, 100L, 8, "很好看", null);
        User user = User.reconstitute(1L, "bob", "hash", "Bob", null, UserRole.USER);
        ReviewWithUserViewBO viewBO = ReviewWithUserViewBO.builder().id(20L).userId(1L).build();
        when(mockReviewRepo.listByAnime(100L, 0, 10)).thenReturn(List.of(review));
        when(mockReviewRepo.countByAnime(100L)).thenReturn(1L);
        when(mockUserRepo.listByIds(List.of(1L))).thenReturn(List.of(user));
        when(mockReviewAssembler.assembleWithUser(List.of(review), List.of(user))).thenReturn(List.of(viewBO));

        // when
        ReviewPageBO<ReviewWithUserViewBO> result = sut.listByAnime(100L, 1, 10);

        // then
        assertThat(result.getList()).containsExactly(viewBO);
        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getPageSize()).isEqualTo(10);
    }

    @Test
    void listByAnime_whenPageIsTwo_shouldComputeOffset() {
        // given
        when(mockReviewRepo.listByAnime(100L, 10, 10)).thenReturn(List.of());
        when(mockReviewRepo.countByAnime(100L)).thenReturn(0L);
        when(mockUserRepo.listByIds(List.of())).thenReturn(List.of());
        when(mockReviewAssembler.assembleWithUser(List.of(), List.of())).thenReturn(List.of());

        // when
        sut.listByAnime(100L, 2, 10);

        // then（验证 offset = (page-1)*pageSize = 10）
        org.mockito.Mockito.verify(mockReviewRepo, org.mockito.Mockito.times(1)).listByAnime(100L, 10, 10);
    }

    @Test
    void listMyReviews_whenCalled_shouldFetchAnimesAndAssemble() {
        // given
        Review review = Review.reconstitute(20L, 1L, 100L, 8, "很好看", null);
        Anime anime = Anime.reconstitute(100L, 999L, "中文名", "Original Title", "http://cover.jpg", 12,
            LocalDate.of(2024, 1, 1), "简介");
        ReviewWithAnimeViewBO viewBO = ReviewWithAnimeViewBO.builder().id(20L).animeId(100L).build();
        when(mockReviewRepo.listByUser(1L)).thenReturn(List.of(review));
        when(mockAnimeRepo.listByIds(List.of(100L))).thenReturn(List.of(anime));
        when(mockReviewAssembler.assembleWithAnime(List.of(review), List.of(anime))).thenReturn(List.of(viewBO));

        // when
        List<ReviewWithAnimeViewBO> result = sut.listMyReviews(1L);

        // then
        assertThat(result).containsExactly(viewBO);
    }
}
