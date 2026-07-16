package com.anitrack.infra.dal.handler;

import com.anitrack.domain.anime.model.Infobox;
import java.util.List;
import org.apache.ibatis.type.MappedTypes;

@MappedTypes(Infobox.class)
public class InfoboxListTypeHandler extends JsonTypeHandler<List<Infobox>> {
    public InfoboxListTypeHandler() { super(new com.fasterxml.jackson.core.type.TypeReference<>() {}); }
}
