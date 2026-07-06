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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/watchlist")
@RequiredArgsConstructor
public class WatchlistController {

    private final WatchlistApplication watchlistApplication;
    private final HttpConverter httpConverter;

    @PostMapping("/add")
    public ResponseResult<WatchlistItemResponse> add(@Valid @RequestBody WatchlistAddReq req) {
        WatchlistItemBO result = watchlistApplication.addToWatchlist(UserContextHolder.getUserId(), req.getAnimeId());
        return ResponseResult.success(httpConverter.watchlistItemBO2Response(result));
    }

    @PostMapping("/change_status")
    public ResponseResult<WatchlistItemResponse> changeStatus(@Valid @RequestBody WatchlistChangeStatusReq req) {
        WatchlistItemBO result = watchlistApplication.changeStatus(
            UserContextHolder.getUserId(), req.getAnimeId(), req.getStatus());
        return ResponseResult.success(httpConverter.watchlistItemBO2Response(result));
    }

    @PostMapping("/update_progress")
    public ResponseResult<WatchlistItemResponse> updateProgress(@Valid @RequestBody WatchlistUpdateProgressReq req) {
        WatchlistItemBO result = watchlistApplication.updateProgress(
            UserContextHolder.getUserId(), req.getAnimeId(), req.getEpisode());
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
