package com.anitrack.infra.repo;

import com.anitrack.domain.review.model.Review;
import com.anitrack.infra.converter.ReviewConverter;
import com.anitrack.infra.dal.mapper.ReviewMapper;
import com.anitrack.infra.dal.po.ReviewPO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewRepoImplTest {

    @Mock
    private ReviewMapper mockReviewMapper;

    @Mock
    private ReviewConverter mockReviewConverter;

    @InjectMocks
    private ReviewRepoImpl sut;

    @Test
    void getByUserAndAnime_whenNotFound_shouldReturnNull() {
        // given
        when(mockReviewMapper.selectByUserAndAnime(1L, 100L)).thenReturn(null);

        // when
        Review result = sut.getByUserAndAnime(1L, 100L);

        // then
        assertThat(result).isNull();
    }

    @Test
    void add_whenCalled_shouldInsertAndReturnConvertedReview() {
        // given
        Review review = Review.create(1L, 100L, 8, "很好看");
        ReviewPO po = new ReviewPO();
        po.setUserId(1L);
        po.setAnimeId(100L);
        ReviewPO persistedPo = new ReviewPO();
        persistedPo.setId(20L);
        persistedPo.setUserId(1L);
        persistedPo.setAnimeId(100L);
        persistedPo.setScore(8);
        persistedPo.setContent("很好看");
        persistedPo.setCreateTime(LocalDateTime.of(2026, 7, 6, 12, 0));
        Review persisted = Review.reconstitute(20L, 1L, 100L, 8, "很好看", persistedPo.getCreateTime());
        when(mockReviewConverter.toPO(review)).thenReturn(po);
        when(mockReviewMapper.selectByUserAndAnime(1L, 100L)).thenReturn(persistedPo);
        when(mockReviewConverter.toDomain(persistedPo)).thenReturn(persisted);

        // when
        Review result = sut.add(review);

        // then
        verify(mockReviewMapper, times(1)).insert(po);
        verify(mockReviewMapper, times(1)).selectByUserAndAnime(1L, 100L);
        verify(mockReviewConverter, never()).toDomain(po);
        assertThat(result).isEqualTo(persisted);
    }

    @Test
    void update_whenCalled_shouldUpdateByUserAndAnime() {
        // given
        Review review = Review.reconstitute(20L, 1L, 100L, 5, "重新打分", null);
        ReviewPO po = new ReviewPO();
        when(mockReviewConverter.toPO(review)).thenReturn(po);

        // when
        sut.update(review);

        // then
        verify(mockReviewMapper, times(1)).updateByUserAndAnime(po);
    }

    @Test
    void listByAnime_whenCalled_shouldQueryWithOffsetAndLimit() {
        // given
        ReviewPO po = new ReviewPO();
        Review converted = Review.reconstitute(20L, 1L, 100L, 8, "很好看", null);
        when(mockReviewMapper.selectByAnime(100L, 0, 10)).thenReturn(List.of(po));
        when(mockReviewConverter.toDomain(po)).thenReturn(converted);

        // when
        List<Review> result = sut.listByAnime(100L, 0, 10);

        // then
        assertThat(result).containsExactly(converted);
    }

    @Test
    void countByAnime_whenCalled_shouldReturnCount() {
        // given
        when(mockReviewMapper.countByAnime(100L)).thenReturn(3L);

        // when
        long result = sut.countByAnime(100L);

        // then
        assertThat(result).isEqualTo(3L);
    }

    @Test
    void listByUser_whenCalled_shouldReturnConvertedList() {
        // given
        ReviewPO po = new ReviewPO();
        Review converted = Review.reconstitute(20L, 1L, 100L, 8, "很好看", null);
        when(mockReviewMapper.selectByUserId(1L)).thenReturn(List.of(po));
        when(mockReviewConverter.toDomain(po)).thenReturn(converted);

        // when
        List<Review> result = sut.listByUser(1L);

        // then
        assertThat(result).containsExactly(converted);
    }
}
