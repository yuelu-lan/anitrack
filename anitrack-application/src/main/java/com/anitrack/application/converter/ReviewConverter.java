package com.anitrack.application.converter;

import com.anitrack.application.model.ReviewBO;
import com.anitrack.domain.review.model.Review;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ReviewConverter {

    ReviewBO review2BO(Review review);
}
