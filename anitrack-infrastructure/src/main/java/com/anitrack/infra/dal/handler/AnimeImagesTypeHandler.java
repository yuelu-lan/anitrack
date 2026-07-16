package com.anitrack.infra.dal.handler;

import com.anitrack.domain.anime.model.AnimeImages;
import org.apache.ibatis.type.MappedTypes;

@MappedTypes(AnimeImages.class)
public class AnimeImagesTypeHandler extends JsonTypeHandler<AnimeImages> {
    public AnimeImagesTypeHandler() { super(new com.fasterxml.jackson.core.type.TypeReference<>() {}); }
}
