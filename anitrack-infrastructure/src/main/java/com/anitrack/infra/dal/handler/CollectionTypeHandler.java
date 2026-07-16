package com.anitrack.infra.dal.handler;

import com.anitrack.domain.anime.model.Collection;
import org.apache.ibatis.type.MappedTypes;

@MappedTypes(Collection.class)
public class CollectionTypeHandler extends JsonTypeHandler<Collection> {
    public CollectionTypeHandler() { super(new com.fasterxml.jackson.core.type.TypeReference<>() {}); }
}
