package com.anitrack.infra.dal.handler;

import com.anitrack.domain.anime.model.AnimeTag;
import java.util.List;
import org.apache.ibatis.type.MappedTypes;

@MappedTypes(List.class)
public class AnimeTagListTypeHandler extends JsonTypeHandler<List<AnimeTag>> {
    public AnimeTagListTypeHandler() { super(new com.fasterxml.jackson.core.type.TypeReference<>() {}); }
}
