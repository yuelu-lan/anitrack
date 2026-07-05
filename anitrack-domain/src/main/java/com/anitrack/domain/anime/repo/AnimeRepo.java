package com.anitrack.domain.anime.repo;

import com.anitrack.domain.anime.model.Anime;

public interface AnimeRepo {

    Anime getById(Long id);

    Anime getByBangumiId(Long bangumiId);

    Anime upsert(Anime anime);
}
