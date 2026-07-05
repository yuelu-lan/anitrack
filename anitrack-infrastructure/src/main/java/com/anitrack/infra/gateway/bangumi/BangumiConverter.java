package com.anitrack.infra.gateway.bangumi;

import com.anitrack.domain.anime.model.Anime;
import com.anitrack.infra.gateway.bangumi.dto.BangumiSubjectDTO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

@Component
public class BangumiConverter {

    public Anime toDomain(BangumiSubjectDTO dto) {
        String coverUrl = dto.getImages() == null ? null : dto.getImages().getLarge();
        return Anime.fromBangumi(
            dto.getId(),
            dto.getNameCn(),
            dto.getName(),
            coverUrl,
            dto.getEps(),
            parseAirDate(dto.getDate()),
            dto.getSummary()
        );
    }

    private LocalDate parseAirDate(String date) {
        return StringUtils.hasText(date) ? LocalDate.parse(date) : null;
    }
}
