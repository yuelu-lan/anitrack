package com.anitrack.starter.controller;

import com.anitrack.application.model.ReviewBO;
import com.anitrack.application.model.ReviewPageBO;
import com.anitrack.application.model.ReviewWithAnimeViewBO;
import com.anitrack.application.model.ReviewWithUserViewBO;
import com.anitrack.application.service.ReviewApplication;
import com.anitrack.common.utils.UserContextHolder;
import com.anitrack.starter.converter.HttpConverter;
import com.anitrack.starter.request.ReviewAddReq;
import com.anitrack.starter.request.ReviewDetailReq;
import com.anitrack.starter.request.ReviewListByAnimeReq;
import com.anitrack.starter.request.ReviewUpdateReq;
import com.anitrack.starter.response.PageResponse;
import com.anitrack.starter.response.ResponseResult;
import com.anitrack.starter.response.ReviewResponse;
import com.anitrack.starter.response.ReviewWithAnimeResponse;
import com.anitrack.starter.response.ReviewWithUserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/review")
@RequiredArgsConstructor
public class ReviewController {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;

    private final ReviewApplication reviewApplication;
    private final HttpConverter httpConverter;

    @PostMapping("/add")
    public ResponseResult<ReviewResponse> add(@Valid @RequestBody ReviewAddReq req) {
        ReviewBO result = reviewApplication.addReview(
            UserContextHolder.getUserId(), req.getAnimeId(), req.getScore(), req.getContent());
        return ResponseResult.success(httpConverter.reviewBO2Response(result));
    }

    @PostMapping("/update")
    public ResponseResult<ReviewResponse> update(@Valid @RequestBody ReviewUpdateReq req) {
        ReviewBO result = reviewApplication.updateReview(
            UserContextHolder.getUserId(), req.getAnimeId(), req.getScore(), req.getContent());
        return ResponseResult.success(httpConverter.reviewBO2Response(result));
    }

    @PostMapping("/detail")
    public ResponseResult<ReviewResponse> detail(@Valid @RequestBody ReviewDetailReq req) {
        ReviewBO result = reviewApplication.getMyReview(UserContextHolder.getUserId(), req.getAnimeId());
        return ResponseResult.success(httpConverter.reviewBO2Response(result));
    }

    @PostMapping("/list_by_anime")
    public ResponseResult<PageResponse<ReviewWithUserResponse>> listByAnime(@Valid @RequestBody ReviewListByAnimeReq req) {
        int page = req.getPage() == null ? DEFAULT_PAGE : req.getPage();
        int pageSize = req.getPageSize() == null ? DEFAULT_PAGE_SIZE : req.getPageSize();
        ReviewPageBO<ReviewWithUserViewBO> result = reviewApplication.listByAnime(req.getAnimeId(), page, pageSize);
        return ResponseResult.success(httpConverter.reviewPageBO2Response(result));
    }

    @PostMapping("/my_list")
    public ResponseResult<List<ReviewWithAnimeResponse>> myList() {
        List<ReviewWithAnimeViewBO> result = reviewApplication.listMyReviews(UserContextHolder.getUserId());
        return ResponseResult.success(httpConverter.reviewWithAnimeViewBOList2Response(result));
    }
}
