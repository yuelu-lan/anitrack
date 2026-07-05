package com.anitrack.application.service;

import com.anitrack.application.exception.AnitrackAppException;
import com.anitrack.application.exception.AppExceptionEnum;
import com.anitrack.application.model.AnimeBO;
import com.anitrack.domain.anime.exception.BangumiApiException;
import com.anitrack.domain.anime.gateway.BangumiGateway;
import com.anitrack.domain.anime.model.Anime;
import com.anitrack.domain.anime.repo.AnimeRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnimeApplication {

    private final BangumiGateway bangumiGateway;
    private final AnimeRepo animeRepo;

    public List<AnimeBO> searchAnime(String keyword) {
        List<Anime> searchResults;
        try {
            searchResults = bangumiGateway.search(keyword);
        } catch (BangumiApiException e) {
            log.error("调用Bangumi搜索接口失败, keyword={}", keyword, e);
            throw new AnitrackAppException(AppExceptionEnum.BANGUMI_SERVICE_UNAVAILABLE);
        }
        return searchResults.stream()
            .map(animeRepo::upsert)
            .map(this::toBO)
            .toList();
    }

    public AnimeBO getAnimeDetail(Long animeId) {
        Anime anime = animeRepo.getById(animeId);
        if (anime == null) {
            throw new AnitrackAppException(AppExceptionEnum.ANIME_NOT_FOUND);
        }
        return toBO(anime);
    }

    private AnimeBO toBO(Anime anime) {
        return AnimeBO.builder()
            .id(anime.getId())
            .bangumiId(anime.getBangumiId())
            .titleCn(anime.getTitleCn())
            .titleOriginal(anime.getTitleOriginal())
            .coverUrl(anime.getCoverUrl())
            .totalEpisodes(anime.getTotalEpisodes())
            .airDate(anime.getAirDate())
            .summary(anime.getSummary())
            .build();
    }
}
