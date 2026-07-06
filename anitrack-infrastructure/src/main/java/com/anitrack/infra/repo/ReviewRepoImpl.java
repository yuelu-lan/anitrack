package com.anitrack.infra.repo;

import com.anitrack.domain.review.model.Review;
import com.anitrack.domain.review.repo.ReviewRepo;
import com.anitrack.infra.converter.ReviewConverter;
import com.anitrack.infra.dal.mapper.ReviewMapper;
import com.anitrack.infra.dal.po.ReviewPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ReviewRepoImpl implements ReviewRepo {

    private final ReviewMapper reviewMapper;
    private final ReviewConverter reviewConverter;

    @Override
    public Review getByUserAndAnime(Long userId, Long animeId) {
        ReviewPO po = reviewMapper.selectByUserAndAnime(userId, animeId);
        return po == null ? null : reviewConverter.toDomain(po);
    }

    @Override
    public List<Review> listByAnime(Long animeId, int offset, int limit) {
        return reviewMapper.selectByAnime(animeId, offset, limit).stream()
            .map(reviewConverter::toDomain)
            .toList();
    }

    @Override
    public long countByAnime(Long animeId) {
        return reviewMapper.countByAnime(animeId);
    }

    @Override
    public List<Review> listByUser(Long userId) {
        return reviewMapper.selectByUserId(userId).stream()
            .map(reviewConverter::toDomain)
            .toList();
    }

    @Override
    public Review add(Review review) {
        ReviewPO po = reviewConverter.toPO(review);
        reviewMapper.insert(po);
        return reviewConverter.toDomain(po);
    }

    @Override
    public void update(Review review) {
        reviewMapper.updateByUserAndAnime(reviewConverter.toPO(review));
    }
}
