package com.anitrack.domain.review.model;

import com.anitrack.domain.review.exception.IllegalReviewScoreException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewTest {

    @Test
    void create_whenScoreIsValid_shouldInitializeFields() {
        // when
        Review review = Review.create(1L, 100L, 8, "很好看");

        // then
        assertThat(review.getId()).isNull();
        assertThat(review.getUserId()).isEqualTo(1L);
        assertThat(review.getAnimeId()).isEqualTo(100L);
        assertThat(review.getScore()).isEqualTo(8);
        assertThat(review.getContent()).isEqualTo("很好看");
    }

    @Test
    void create_whenContentIsNull_shouldAllow() {
        // when
        Review review = Review.create(1L, 100L, 10, null);

        // then
        assertThat(review.getContent()).isNull();
    }

    @Test
    void create_whenScoreIsNull_shouldThrowException() {
        // when & then
        assertThatThrownBy(() -> Review.create(1L, 100L, null, "content"))
            .isInstanceOf(IllegalReviewScoreException.class);
    }

    @Test
    void create_whenScoreIsZero_shouldThrowException() {
        // when & then
        assertThatThrownBy(() -> Review.create(1L, 100L, 0, "content"))
            .isInstanceOf(IllegalReviewScoreException.class);
    }

    @Test
    void create_whenScoreIsEleven_shouldThrowException() {
        // when & then
        assertThatThrownBy(() -> Review.create(1L, 100L, 11, "content"))
            .isInstanceOf(IllegalReviewScoreException.class);
    }

    @Test
    void create_whenScoreIsOne_shouldSucceed() {
        // when
        Review review = Review.create(1L, 100L, 1, null);

        // then
        assertThat(review.getScore()).isEqualTo(1);
    }

    @Test
    void create_whenScoreIsTen_shouldSucceed() {
        // when
        Review review = Review.create(1L, 100L, 10, null);

        // then
        assertThat(review.getScore()).isEqualTo(10);
    }

    @Test
    void reconstitute_whenCalled_shouldRestoreAllFields() {
        // given
        LocalDateTime createTime = LocalDateTime.of(2026, 1, 1, 0, 0);

        // when
        Review review = Review.reconstitute(10L, 1L, 100L, 8, "很好看", createTime);

        // then
        assertThat(review.getId()).isEqualTo(10L);
        assertThat(review.getScore()).isEqualTo(8);
        assertThat(review.getContent()).isEqualTo("很好看");
        assertThat(review.getCreateTime()).isEqualTo(createTime);
    }

    @Test
    void update_whenScoreIsValid_shouldReplaceScoreAndContent() {
        // given
        Review review = Review.create(1L, 100L, 8, "很好看");

        // when
        review.update(5, "重新打分");

        // then
        assertThat(review.getScore()).isEqualTo(5);
        assertThat(review.getContent()).isEqualTo("重新打分");
    }

    @Test
    void update_whenScoreIsInvalid_shouldThrowExceptionAndKeepOriginal() {
        // given
        Review review = Review.create(1L, 100L, 8, "很好看");

        // when & then
        assertThatThrownBy(() -> review.update(11, "重新打分"))
            .isInstanceOf(IllegalReviewScoreException.class);
        assertThat(review.getScore()).isEqualTo(8);
        assertThat(review.getContent()).isEqualTo("很好看");
    }
}
