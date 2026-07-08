package com.anitrack.application.service;

import com.anitrack.application.converter.AnimeBOConverter;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnimeApplication {

    private final BangumiGateway bangumiGateway;
    private final AnimeRepo animeRepo;
    private final AnimeBOConverter animeBOConverter;

    @Transactional
    public List<AnimeBO> searchAnime(String keyword) {
        List<Anime> searchResults;
        try {
            searchResults = bangumiGateway.search(keyword);
        } catch (BangumiApiException e) {
            log.error("调用Bangumi搜索接口失败, keyword={}", keyword, e);
            throw AnitrackAppException.build(AppExceptionEnum.BANGUMI_SERVICE_UNAVAILABLE);
        }
        return searchResults.stream()
            .map(animeRepo::upsert)
            .map(animeBOConverter::anime2BO)
            .toList();
    }

    public AnimeBO getAnimeDetail(Long animeId) {
        Anime anime = animeRepo.getById(animeId);
        if (anime == null) {
            throw AnitrackAppException.build(AppExceptionEnum.ANIME_NOT_FOUND);
        }
        return animeBOConverter.anime2BO(anime);
    }
}
