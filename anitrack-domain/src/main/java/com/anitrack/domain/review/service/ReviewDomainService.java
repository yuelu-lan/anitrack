package com.anitrack.domain.review.service;

import com.anitrack.domain.review.exception.ReviewAlreadyExistsException;
import com.anitrack.domain.review.exception.ReviewNotAllowedException;
import com.anitrack.domain.review.model.Review;
import com.anitrack.domain.review.repo.ReviewRepo;
import com.anitrack.domain.watchlist.enums.WatchStatus;
import com.anitrack.domain.watchlist.model.WatchlistItem;
import com.anitrack.domain.watchlist.repo.WatchlistRepo;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ReviewDomainService {

    private final ReviewRepo reviewRepo;
    private final WatchlistRepo watchlistRepo;

    public Review addReview(Long userId, Long animeId, Integer score, String content) {
        WatchlistItem item = watchlistRepo.getByUserAndAnime(userId, animeId);
        if (item == null || item.getStatus() != WatchStatus.WATCHED) {
            throw new ReviewNotAllowedException(userId, animeId);
        }
        if (reviewRepo.getByUserAndAnime(userId, animeId) != null) {
            throw new ReviewAlreadyExistsException(userId, animeId);
        }
        Review review = Review.create(userId, animeId, score, content);
        return reviewRepo.add(review);
    }
}
