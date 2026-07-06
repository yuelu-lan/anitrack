package com.anitrack.domain.anime.repo;

import com.anitrack.domain.anime.model.Anime;

import java.util.List;

public interface AnimeRepo {

    Anime getById(Long id);

    Anime getByBangumiId(Long bangumiId);

    List<Anime> listByIds(List<Long> ids);

    Anime upsert(Anime anime);
}
