package com.anitrack.domain.review.repo;

import com.anitrack.domain.review.model.Review;

import java.util.List;

public interface ReviewRepo {

    Review getByUserAndAnime(Long userId, Long animeId);

    List<Review> listByAnime(Long animeId, int offset, int limit);

    long countByAnime(Long animeId);

    List<Review> listByUser(Long userId);

    Review add(Review review);

    void update(Review review);
}
