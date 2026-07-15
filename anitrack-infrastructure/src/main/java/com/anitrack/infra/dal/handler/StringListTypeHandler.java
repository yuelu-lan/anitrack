package com.anitrack.infra.dal.handler;

import java.util.List;
import org.apache.ibatis.type.MappedTypes;

@MappedTypes(String.class)
public class StringListTypeHandler extends JsonTypeHandler<List<String>> {
    public StringListTypeHandler() { super(new com.fasterxml.jackson.core.type.TypeReference<>() {}); }
}
