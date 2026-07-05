package com.anitrack.infra.repo;

import com.anitrack.domain.anime.model.Anime;
import com.anitrack.domain.anime.repo.AnimeRepo;
import com.anitrack.infra.converter.AnimeConverter;
import com.anitrack.infra.dal.mapper.AnimeMapper;
import com.anitrack.infra.dal.po.AnimePO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AnimeRepoImpl implements AnimeRepo {

    private final AnimeMapper animeMapper;
    private final AnimeConverter animeConverter;

    @Override
    public Anime getById(Long id) {
        AnimePO po = animeMapper.selectById(id);
        return po == null ? null : animeConverter.toDomain(po);
    }

    @Override
    public Anime getByBangumiId(Long bangumiId) {
        AnimePO po = animeMapper.selectByBangumiId(bangumiId);
        return po == null ? null : animeConverter.toDomain(po);
    }

    @Override
    public Anime upsert(Anime anime) {
        AnimePO existing = animeMapper.selectByBangumiId(anime.getBangumiId());
        AnimePO po = animeConverter.toPO(anime);
        if (existing == null) {
            animeMapper.insert(po);
        } else {
            po.setId(existing.getId());
            animeMapper.updateById(po);
        }
        return animeConverter.toDomain(po);
    }
}
