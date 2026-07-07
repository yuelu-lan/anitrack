package com.anitrack.starter.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonConfigTest {

    @Test
    void localDateTime_shouldSerializeAsIso8601String() throws Exception {
        Jackson2ObjectMapperBuilderCustomizer customizer = new JacksonConfig().jacksonCustomizer();
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        customizer.customize(builder);
        ObjectMapper mapper = builder.build();

        String json = mapper.writeValueAsString(LocalDateTime.of(2026, 7, 7, 12, 34, 56));
        assertThat(json).isEqualTo("\"2026-07-07T12:34:56\"");
    }
}
