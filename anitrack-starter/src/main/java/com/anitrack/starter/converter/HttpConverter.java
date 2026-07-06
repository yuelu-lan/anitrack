package com.anitrack.starter.converter;

import com.anitrack.application.model.AnimeBO;
import com.anitrack.application.model.ReviewBO;
import com.anitrack.application.model.ReviewPageBO;
import com.anitrack.application.model.ReviewWithAnimeViewBO;
import com.anitrack.application.model.ReviewWithUserViewBO;
import com.anitrack.application.model.UserBO;
import com.anitrack.application.model.UserLoginBO;
import com.anitrack.application.model.UserRegisterBO;
import com.anitrack.application.model.WatchlistItemBO;
import com.anitrack.application.model.WatchlistItemViewBO;
import com.anitrack.starter.request.UserLoginReq;
import com.anitrack.starter.request.UserRegisterReq;
import com.anitrack.starter.response.AnimeResponse;
import com.anitrack.starter.response.LoginResponse;
import com.anitrack.starter.response.PageResponse;
import com.anitrack.starter.response.ReviewResponse;
import com.anitrack.starter.response.ReviewWithAnimeResponse;
import com.anitrack.starter.response.ReviewWithUserResponse;
import com.anitrack.starter.response.UserInfoResponse;
import com.anitrack.starter.response.WatchlistItemResponse;
import com.anitrack.starter.response.WatchlistItemViewResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class HttpConverter {

    public UserRegisterBO req2BO(UserRegisterReq req) {
        return UserRegisterBO.builder()
            .username(req.getUsername())
            .password(req.getPassword())
            .nickname(req.getNickname())
            .build();
    }

    public UserLoginBO req2BO(UserLoginReq req) {
        return UserLoginBO.builder()
            .username(req.getUsername())
            .password(req.getPassword())
            .build();
    }

    public UserInfoResponse bo2Response(UserBO bo) {
        return UserInfoResponse.builder()
            .id(bo.getId())
            .username(bo.getUsername())
            .nickname(bo.getNickname())
            .avatarUrl(bo.getAvatarUrl())
            .build();
    }

    public LoginResponse toLoginResponse(String token, UserBO bo) {
        return LoginResponse.builder()
            .token(token)
            .userInfo(bo2Response(bo))
            .build();
    }

    public AnimeResponse animeBO2Response(AnimeBO bo) {
        return AnimeResponse.builder()
            .id(bo.getId())
            .bangumiId(bo.getBangumiId())
            .titleCn(bo.getTitleCn())
            .titleOriginal(bo.getTitleOriginal())
            .coverUrl(bo.getCoverUrl())
            .totalEpisodes(bo.getTotalEpisodes())
            .airDate(bo.getAirDate())
            .summary(bo.getSummary())
            .build();
    }

    public List<AnimeResponse> animeBOList2Response(List<AnimeBO> boList) {
        return boList.stream().map(this::animeBO2Response).toList();
    }

    public WatchlistItemResponse watchlistItemBO2Response(WatchlistItemBO bo) {
        return WatchlistItemResponse.builder()
            .id(bo.getId())
            .animeId(bo.getAnimeId())
            .status(bo.getStatus())
            .currentEpisode(bo.getCurrentEpisode())
            .updateTime(bo.getUpdateTime())
            .build();
    }

    public WatchlistItemViewResponse watchlistItemViewBO2Response(WatchlistItemViewBO bo) {
        return WatchlistItemViewResponse.builder()
            .id(bo.getId())
            .animeId(bo.getAnimeId())
            .animeTitleCn(bo.getAnimeTitleCn())
            .animeTitleOriginal(bo.getAnimeTitleOriginal())
            .animeCoverUrl(bo.getAnimeCoverUrl())
            .status(bo.getStatus())
            .currentEpisode(bo.getCurrentEpisode())
            .updateTime(bo.getUpdateTime())
            .build();
    }

    public List<WatchlistItemViewResponse> watchlistItemViewBOList2Response(List<WatchlistItemViewBO> boList) {
        return boList.stream().map(this::watchlistItemViewBO2Response).toList();
    }

    public ReviewResponse reviewBO2Response(ReviewBO bo) {
        return ReviewResponse.builder()
            .id(bo.getId())
            .animeId(bo.getAnimeId())
            .score(bo.getScore())
            .content(bo.getContent())
            .createTime(bo.getCreateTime())
            .build();
    }

    public ReviewWithUserResponse reviewWithUserViewBO2Response(ReviewWithUserViewBO bo) {
        return ReviewWithUserResponse.builder()
            .id(bo.getId())
            .userId(bo.getUserId())
            .userNickname(bo.getUserNickname())
            .userAvatarUrl(bo.getUserAvatarUrl())
            .score(bo.getScore())
            .content(bo.getContent())
            .createTime(bo.getCreateTime())
            .build();
    }

    public ReviewWithAnimeResponse reviewWithAnimeViewBO2Response(ReviewWithAnimeViewBO bo) {
        return ReviewWithAnimeResponse.builder()
            .id(bo.getId())
            .animeId(bo.getAnimeId())
            .animeTitleCn(bo.getAnimeTitleCn())
            .animeTitleOriginal(bo.getAnimeTitleOriginal())
            .animeCoverUrl(bo.getAnimeCoverUrl())
            .score(bo.getScore())
            .content(bo.getContent())
            .createTime(bo.getCreateTime())
            .build();
    }

    public List<ReviewWithAnimeResponse> reviewWithAnimeViewBOList2Response(List<ReviewWithAnimeViewBO> boList) {
        return boList.stream().map(this::reviewWithAnimeViewBO2Response).toList();
    }

    public PageResponse<ReviewWithUserResponse> reviewPageBO2Response(ReviewPageBO<ReviewWithUserViewBO> pageBO) {
        List<ReviewWithUserResponse> list = pageBO.getList().stream()
            .map(this::reviewWithUserViewBO2Response)
            .toList();
        return PageResponse.<ReviewWithUserResponse>builder()
            .pageNum(pageBO.getPage())
            .pageSize(pageBO.getPageSize())
            .total(pageBO.getTotal())
            .list(list)
            .build();
    }
}
