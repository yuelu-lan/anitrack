package com.anitrack.application.service;

import com.anitrack.application.assembler.ReviewAssembler;
import com.anitrack.application.converter.ReviewBOConverter;
import com.anitrack.application.exception.AnitrackAppException;
import com.anitrack.application.exception.AppExceptionEnum;
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
import com.anitrack.domain.user.model.User;
import com.anitrack.domain.user.repo.UserRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewApplication {

    private final ReviewDomainService reviewDomainService;
    private final ReviewRepo reviewRepo;
    private final UserRepo userRepo;
    private final AnimeRepo animeRepo;
    private final ReviewAssembler reviewAssembler;
    private final ReviewBOConverter reviewBOConverter;

    @Transactional
    public ReviewBO addReview(Long userId, Long animeId, Integer score, String content) {
        Review review;
        try {
            review = reviewDomainService.addReview(userId, animeId, score, content);
        } catch (ReviewNotAllowedException e) {
            throw AnitrackAppException.build(AppExceptionEnum.REVIEW_NOT_ALLOWED);
        } catch (ReviewAlreadyExistsException e) {
            throw AnitrackAppException.build(AppExceptionEnum.REVIEW_ALREADY_EXISTS);
        } catch (IllegalReviewScoreException e) {
            throw AnitrackAppException.build(AppExceptionEnum.ILLEGAL_REVIEW_SCORE);
        }
        log.info("评价新增, userId={}, animeId={}", userId, animeId);
        return reviewBOConverter.review2BO(review);
    }

    @Transactional
    public ReviewBO updateReview(Long userId, Long animeId, Integer score, String content) {
        Review review = reviewRepo.getByUserAndAnime(userId, animeId);
        if (review == null) {
            throw AnitrackAppException.build(AppExceptionEnum.REVIEW_NOT_FOUND);
        }
        try {
            review.update(score, content);
        } catch (IllegalReviewScoreException e) {
            throw AnitrackAppException.build(AppExceptionEnum.ILLEGAL_REVIEW_SCORE);
        }
        reviewRepo.update(review);
        log.info("评价修改, userId={}, animeId={}", userId, animeId);
        return reviewBOConverter.review2BO(review);
    }

    public ReviewBO getMyReview(Long userId, Long animeId) {
        Review review = reviewRepo.getByUserAndAnime(userId, animeId);
        if (review == null) {
            throw AnitrackAppException.build(AppExceptionEnum.REVIEW_NOT_FOUND);
        }
        return reviewBOConverter.review2BO(review);
    }

    public ReviewPageBO<ReviewWithUserViewBO> listByAnime(Long animeId, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<Review> reviews = reviewRepo.listByAnime(animeId, offset, pageSize);
        long total = reviewRepo.countByAnime(animeId);
        List<Long> userIds = reviews.stream().map(Review::getUserId).toList();
        List<User> users = userRepo.listByIds(userIds);
        List<ReviewWithUserViewBO> list = reviewAssembler.assembleWithUser(reviews, users);
        return ReviewPageBO.<ReviewWithUserViewBO>builder()
            .list(list)
            .total(total)
            .page(page)
            .pageSize(pageSize)
            .build();
    }

    public List<ReviewWithAnimeViewBO> listMyReviews(Long userId) {
        List<Review> reviews = reviewRepo.listByUser(userId);
        List<Long> animeIds = reviews.stream().map(Review::getAnimeId).toList();
        List<Anime> animes = animeRepo.listByIds(animeIds);
        return reviewAssembler.assembleWithAnime(reviews, animes);
    }
}
