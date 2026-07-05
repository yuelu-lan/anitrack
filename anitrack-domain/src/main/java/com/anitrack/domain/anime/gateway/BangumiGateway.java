package com.anitrack.domain.anime.gateway;

import com.anitrack.domain.anime.model.Anime;

import java.util.List;

public interface BangumiGateway {

    List<Anime> search(String keyword);
}
