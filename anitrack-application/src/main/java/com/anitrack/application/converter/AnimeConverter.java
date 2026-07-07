package com.anitrack.application.converter;

import com.anitrack.application.model.AnimeBO;
import com.anitrack.domain.anime.model.Anime;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AnimeConverter {

    AnimeBO anime2BO(Anime anime);
}
