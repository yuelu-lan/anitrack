package com.anitrack.starter.controller;

import com.anitrack.application.model.WatchlistItemBO;
import com.anitrack.application.model.WatchlistItemViewBO;
import com.anitrack.application.service.WatchlistApplication;
import com.anitrack.common.utils.UserContextHolder;
import com.anitrack.starter.converter.HttpConverter;
import com.anitrack.starter.request.WatchlistAddReq;
import com.anitrack.starter.request.WatchlistChangeStatusReq;
import com.anitrack.starter.request.WatchlistDetailReq;
import com.anitrack.starter.request.WatchlistListReq;
import com.anitrack.starter.request.WatchlistUpdateProgressReq;
import com.anitrack.starter.response.ResponseResult;
import com.anitrack.starter.response.WatchlistItemResponse;
import com.anitrack.starter.response.WatchlistItemViewResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/watchlist")
@RequiredArgsConstructor
public class WatchlistController {

    private final WatchlistApplication watchlistApplication;
    private final HttpConverter httpConverter;

    @PostMapping("/add")
    public ResponseResult<WatchlistItemResponse> add(@Valid @RequestBody WatchlistAddReq req) {
        Long userId = UserContextHolder.getUserId();
        log.info("加入追番, userId={}, animeId={}", userId, req.getAnimeId());
        WatchlistItemBO result = watchlistApplication.addToWatchlist(userId, req.getAnimeId());
        return ResponseResult.success(httpConverter.watchlistItemBO2Response(result));
    }

    @PostMapping("/change_status")
    public ResponseResult<WatchlistItemResponse> changeStatus(@Valid @RequestBody WatchlistChangeStatusReq req) {
        Long userId = UserContextHolder.getUserId();
        log.info("追番状态变更, userId={}, animeId={}, status={}", userId, req.getAnimeId(), req.getStatus());
        WatchlistItemBO result = watchlistApplication.changeStatus(userId, req.getAnimeId(), req.getStatus());
        return ResponseResult.success(httpConverter.watchlistItemBO2Response(result));
    }

    @PostMapping("/update_progress")
    public ResponseResult<WatchlistItemResponse> updateProgress(@Valid @RequestBody WatchlistUpdateProgressReq req) {
        Long userId = UserContextHolder.getUserId();
        log.info("追番进度更新, userId={}, animeId={}, episode={}", userId, req.getAnimeId(), req.getEpisode());
        WatchlistItemBO result = watchlistApplication.updateProgress(userId, req.getAnimeId(), req.getEpisode());
        return ResponseResult.success(httpConverter.watchlistItemBO2Response(result));
    }

    @PostMapping("/detail")
    public ResponseResult<WatchlistItemResponse> detail(@Valid @RequestBody WatchlistDetailReq req) {
        WatchlistItemBO result = watchlistApplication.getWatchlistItem(UserContextHolder.getUserId(), req.getAnimeId());
        return ResponseResult.success(httpConverter.watchlistItemBO2Response(result));
    }

    @PostMapping("/list")
    public ResponseResult<List<WatchlistItemViewResponse>> list(@RequestBody WatchlistListReq req) {
        List<WatchlistItemViewBO> result = watchlistApplication.listMyWatchlist(
            UserContextHolder.getUserId(), req.getStatus());
        return ResponseResult.success(httpConverter.watchlistItemViewBOList2Response(result));
    }
}
