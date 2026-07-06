package com.anitrack.application.assembler;

import com.anitrack.application.model.ReviewWithAnimeViewBO;
import com.anitrack.application.model.ReviewWithUserViewBO;
import com.anitrack.domain.anime.model.Anime;
import com.anitrack.domain.review.model.Review;
import com.anitrack.domain.user.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ReviewAssembler {

    public List<ReviewWithUserViewBO> assembleWithUser(List<Review> reviews, List<User> users) {
        Map<Long, User> userById = users.stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));
        return reviews.stream()
            .map(review -> toUserViewBO(review, userById.get(review.getUserId())))
            .filter(Objects::nonNull)
            .toList();
    }

    public List<ReviewWithAnimeViewBO> assembleWithAnime(List<Review> reviews, List<Anime> animes) {
        Map<Long, Anime> animeById = animes.stream()
            .collect(Collectors.toMap(Anime::getId, Function.identity()));
        return reviews.stream()
            .map(review -> toAnimeViewBO(review, animeById.get(review.getAnimeId())))
            .filter(Objects::nonNull)
            .toList();
    }

    private ReviewWithUserViewBO toUserViewBO(Review review, User user) {
        if (user == null) {
            log.warn("评价关联的用户不存在, userId={}", review.getUserId());
            return null;
        }
        return ReviewWithUserViewBO.builder()
            .id(review.getId())
            .userId(review.getUserId())
            .userNickname(user.getNickname())
            .userAvatarUrl(user.getAvatarUrl())
            .score(review.getScore())
            .content(review.getContent())
            .createTime(review.getCreateTime())
            .build();
    }

    private ReviewWithAnimeViewBO toAnimeViewBO(Review review, Anime anime) {
        if (anime == null) {
            log.warn("评价关联的番剧不存在, animeId={}", review.getAnimeId());
            return null;
        }
        return ReviewWithAnimeViewBO.builder()
            .id(review.getId())
            .animeId(review.getAnimeId())
            .animeTitleCn(anime.getTitleCn())
            .animeTitleOriginal(anime.getTitleOriginal())
            .animeCoverUrl(anime.getCoverUrl())
            .score(review.getScore())
            .content(review.getContent())
            .createTime(review.getCreateTime())
            .build();
    }
}
