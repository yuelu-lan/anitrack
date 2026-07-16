package com.anitrack.domain.anime.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Anime {

    private Long id;
    private Long bangumiId;
    private String titleCn;
    private String titleOriginal;
    private String coverUrl;
    private Integer totalEpisodes;
    private LocalDate airDate;
    private String summary;
    private final Integer type;
    private final Boolean nsfw;
    private final Boolean locked;
    private final Boolean series;
    private final String platform;
    private final AnimeImages images;
    private final Integer eps;
    private final Integer volumes;
    private final List<String> metaTags;
    private final List<AnimeTag> tags;
    private final Rating rating;
    private final Collection collection;
    private final List<Infobox> infobox;

    public static Anime fromBangumi(Long bangumiId, String titleCn, String titleOriginal, String coverUrl,
                                     Integer totalEpisodes, LocalDate airDate, String summary) {
        return Anime.builder()
                .bangumiId(bangumiId).titleOriginal(titleOriginal).titleCn(titleCn)
                .summary(summary).airDate(airDate).coverUrl(coverUrl)
                .totalEpisodes(totalEpisodes).eps(totalEpisodes).volumes(0)
                .nsfw(false).locked(false).series(false)
                .metaTags(List.of()).tags(List.of())
                .rating(Rating.empty()).collection(Collection.empty()).infobox(List.of())
                .build();
    }

    public static Anime fromBangumi(Long bangumiId, Integer type, String titleOriginal, String titleCn,
                                    String summary, Boolean nsfw, Boolean locked, Boolean series,
                                    LocalDate airDate, String platform, AnimeImages images,
                                    Integer eps, Integer totalEpisodes, Integer volumes,
                                    List<String> metaTags, List<AnimeTag> tags,
                                    Rating rating, Collection collection, List<Infobox> infobox) {
        return Anime.builder()
                .bangumiId(bangumiId).type(type).titleOriginal(titleOriginal).titleCn(titleCn)
                .summary(summary).nsfw(nsfw).locked(locked).series(series).airDate(airDate)
                .platform(platform).images(images).coverUrl(images == null ? null : images.getLarge())
                .eps(eps).totalEpisodes(totalEpisodes).volumes(volumes)
                .metaTags(metaTags).tags(tags).rating(rating).collection(collection).infobox(infobox)
                .build();
    }

    public static Anime reconstitute(Long id, Long bangumiId, String titleCn, String titleOriginal, String coverUrl,
                                      Integer totalEpisodes, LocalDate airDate, String summary) {
        return Anime.builder()
                .id(id).bangumiId(bangumiId).titleOriginal(titleOriginal).titleCn(titleCn)
                .summary(summary).airDate(airDate).coverUrl(coverUrl)
                .totalEpisodes(totalEpisodes).eps(totalEpisodes).volumes(0)
                .nsfw(false).locked(false).series(false)
                .metaTags(List.of()).tags(List.of())
                .rating(Rating.empty()).collection(Collection.empty()).infobox(List.of())
                .build();
    }

    public static Anime reconstitute(Long id, Long bangumiId, Integer type, String titleOriginal, String titleCn,
                                     String summary, Boolean nsfw, Boolean locked, Boolean series,
                                     LocalDate airDate, String platform, AnimeImages images,
                                     Integer eps, Integer totalEpisodes, Integer volumes,
                                     List<String> metaTags, List<AnimeTag> tags,
                                     Rating rating, Collection collection, List<Infobox> infobox) {
        return Anime.builder()
                .id(id).bangumiId(bangumiId).type(type).titleOriginal(titleOriginal).titleCn(titleCn)
                .summary(summary).nsfw(nsfw).locked(locked).series(series).airDate(airDate)
                .platform(platform).images(images).coverUrl(images == null ? null : images.getLarge())
                .eps(eps).totalEpisodes(totalEpisodes).volumes(volumes)
                .metaTags(metaTags).tags(tags).rating(rating).collection(collection).infobox(infobox)
                .build();
    }
}
