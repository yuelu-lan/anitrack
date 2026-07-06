package com.anitrack.infra.converter;

import com.anitrack.domain.review.model.Review;
import com.anitrack.infra.dal.po.ReviewPO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ReviewConverter {

    ReviewPO toPO(Review review);

    default Review toDomain(ReviewPO po) {
        if (po == null) {
            return null;
        }
        return Review.reconstitute(po.getId(), po.getUserId(), po.getAnimeId(),
            po.getScore(), po.getContent(), po.getCreateTime());
    }
}
