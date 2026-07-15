package com.anitrack.infra.dal.handler;

import com.anitrack.domain.anime.model.Rating;
import org.apache.ibatis.type.MappedTypes;

@MappedTypes(Rating.class)
public class RatingTypeHandler extends JsonTypeHandler<Rating> {
    public RatingTypeHandler() { super(new com.fasterxml.jackson.core.type.TypeReference<>() {}); }
}
