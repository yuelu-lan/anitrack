package com.anitrack.domain.review.service;

import com.anitrack.domain.review.exception.ReviewAlreadyExistsException;
import com.anitrack.domain.review.exception.ReviewNotAllowedException;
import com.anitrack.domain.review.model.Review;
import com.anitrack.domain.review.repo.ReviewRepo;
import com.anitrack.domain.watchlist.enums.WatchStatus;
import com.anitrack.domain.watchlist.model.WatchlistItem;
import com.anitrack.domain.watchlist.repo.WatchlistRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewDomainServiceTest {

    @Mock
    private ReviewRepo mockReviewRepo;

    @Mock
    private WatchlistRepo mockWatchlistRepo;

    @InjectMocks
    private ReviewDomainService sut;

    @Test
    void addReview_whenWatchedAndNotDuplicate_shouldCreateAndAdd() {
        // given
        WatchlistItem item = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WATCHED, 12, null);
        when(mockWatchlistRepo.getByUserAndAnime(1L, 100L)).thenReturn(item);
        when(mockReviewRepo.getByUserAndAnime(1L, 100L)).thenReturn(null);
        Review saved = Review.reconstitute(20L, 1L, 100L, 8, "很好看", null);
        when(mockReviewRepo.add(any(Review.class))).thenReturn(saved);

        // when
        Review result = sut.addReview(1L, 100L, 8, "很好看");

        // then
        assertThat(result).isEqualTo(saved);
        verify(mockReviewRepo, times(1)).add(any(Review.class));
    }

    @Test
    void addReview_whenNotWatched_shouldThrowException() {
        // given
        WatchlistItem item = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WATCHING, 5, null);
        when(mockWatchlistRepo.getByUserAndAnime(1L, 100L)).thenReturn(item);

        // when & then
        assertThatThrownBy(() -> sut.addReview(1L, 100L, 8, "很好看"))
            .isInstanceOf(ReviewNotAllowedException.class);

        verify(mockReviewRepo, never()).add(any());
    }

    @Test
    void addReview_whenNoWatchlistItem_shouldThrowException() {
        // given
        when(mockWatchlistRepo.getByUserAndAnime(1L, 100L)).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> sut.addReview(1L, 100L, 8, "很好看"))
            .isInstanceOf(ReviewNotAllowedException.class);

        verify(mockReviewRepo, never()).add(any());
    }

    @Test
    void addReview_whenAlreadyExists_shouldThrowException() {
        // given
        WatchlistItem item = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WATCHED, 12, null);
        when(mockWatchlistRepo.getByUserAndAnime(1L, 100L)).thenReturn(item);
        Review existing = Review.reconstitute(20L, 1L, 100L, 7, null, null);
        when(mockReviewRepo.getByUserAndAnime(1L, 100L)).thenReturn(existing);

        // when & then
        assertThatThrownBy(() -> sut.addReview(1L, 100L, 8, "很好看"))
            .isInstanceOf(ReviewAlreadyExistsException.class);

        verify(mockReviewRepo, never()).add(any());
    }
}
