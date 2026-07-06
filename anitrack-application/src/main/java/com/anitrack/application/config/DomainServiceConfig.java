package com.anitrack.application.config;

import com.anitrack.domain.anime.repo.AnimeRepo;
import com.anitrack.domain.watchlist.repo.WatchlistRepo;
import com.anitrack.domain.watchlist.service.WatchlistDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainServiceConfig {

    @Bean
    public WatchlistDomainService watchlistDomainService(WatchlistRepo watchlistRepo, AnimeRepo animeRepo) {
        return new WatchlistDomainService(watchlistRepo, animeRepo);
    }
}
