package com.anitrack.application.assembler;

import com.anitrack.application.model.WatchlistItemViewBO;
import com.anitrack.domain.anime.model.Anime;
import com.anitrack.domain.watchlist.model.WatchlistItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class WatchlistAssembler {

    public List<WatchlistItemViewBO> assemble(List<WatchlistItem> items, List<Anime> animes) {
        Map<Long, Anime> animeById = animes.stream()
            .collect(Collectors.toMap(Anime::getId, Function.identity()));
        return items.stream()
            .map(item -> toViewBO(item, animeById.get(item.getAnimeId())))
            .filter(Objects::nonNull)
            .toList();
    }

    private WatchlistItemViewBO toViewBO(WatchlistItem item, Anime anime) {
        if (anime == null) {
            log.warn("追番记录关联的番剧不存在, animeId={}", item.getAnimeId());
            return null;
        }
        return WatchlistItemViewBO.builder()
            .id(item.getId())
            .animeId(item.getAnimeId())
            .animeTitleCn(anime.getTitleCn())
            .animeTitleOriginal(anime.getTitleOriginal())
            .animeCoverUrl(anime.getCoverUrl())
            .status(item.getStatus())
            .currentEpisode(item.getCurrentEpisode())
            .updateTime(item.getUpdateTime())
            .build();
    }
}
