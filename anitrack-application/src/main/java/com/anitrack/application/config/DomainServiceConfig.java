package com.anitrack.application.config;

import com.anitrack.domain.anime.repo.AnimeRepo;
import com.anitrack.domain.review.repo.ReviewRepo;
import com.anitrack.domain.review.service.ReviewDomainService;
import com.anitrack.domain.user.repo.UserRepo;
import com.anitrack.domain.user.service.UserDomainService;
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

    @Bean
    public ReviewDomainService reviewDomainService(ReviewRepo reviewRepo, WatchlistRepo watchlistRepo) {
        return new ReviewDomainService(reviewRepo, watchlistRepo);
    }

    @Bean
    public UserDomainService userDomainService(UserRepo userRepo) {
        return new UserDomainService(userRepo);
    }
}
