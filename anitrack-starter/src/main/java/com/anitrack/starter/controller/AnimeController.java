package com.anitrack.starter.controller;

import com.anitrack.application.model.AnimeBO;
import com.anitrack.application.service.AnimeApplication;
import com.anitrack.starter.converter.HttpConverter;
import com.anitrack.starter.request.AnimeDetailReq;
import com.anitrack.starter.request.AnimeSearchReq;
import com.anitrack.starter.response.AnimeResponse;
import com.anitrack.starter.response.ResponseResult;
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
@RequestMapping("/api/anime")
@RequiredArgsConstructor
public class AnimeController {

    private final AnimeApplication animeApplication;
    private final HttpConverter httpConverter;

    @PostMapping("/search")
    public ResponseResult<List<AnimeResponse>> search(@Valid @RequestBody AnimeSearchReq req) {
        log.info("番剧搜索请求, keyword={}", req.getKeyword());
        List<AnimeBO> result = animeApplication.searchAnime(req.getKeyword());
        return ResponseResult.success(httpConverter.animeBOList2Response(result));
    }

    @PostMapping("/detail")
    public ResponseResult<AnimeResponse> detail(@Valid @RequestBody AnimeDetailReq req) {
        AnimeBO result = animeApplication.getAnimeDetail(req.getAnimeId());
        return ResponseResult.success(httpConverter.animeBO2Response(result));
    }
}
