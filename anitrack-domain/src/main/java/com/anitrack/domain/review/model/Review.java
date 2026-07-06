package com.anitrack.domain.review.model;

import com.anitrack.domain.review.exception.IllegalReviewScoreException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Review {

    private Long id;
    private Long userId;
    private Long animeId;
    private Integer score;
    private String content;
    private LocalDateTime createTime;

    public static Review create(Long userId, Long animeId, Integer score, String content) {
        validateScore(score);
        return Review.builder()
            .userId(userId)
            .animeId(animeId)
            .score(score)
            .content(content)
            .build();
    }

    public static Review reconstitute(Long id, Long userId, Long animeId, Integer score, String content,
                                       LocalDateTime createTime) {
        return Review.builder()
            .id(id)
            .userId(userId)
            .animeId(animeId)
            .score(score)
            .content(content)
            .createTime(createTime)
            .build();
    }

    public void update(Integer score, String content) {
        validateScore(score);
        this.score = score;
        this.content = content;
    }

    private static void validateScore(Integer score) {
        if (score == null || score < 1 || score > 10) {
            throw new IllegalReviewScoreException("评分必须在1-10之间");
        }
    }
}
