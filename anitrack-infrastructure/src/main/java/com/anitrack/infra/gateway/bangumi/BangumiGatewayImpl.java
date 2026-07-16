package com.anitrack.infra.gateway.bangumi;

import com.anitrack.domain.anime.exception.BangumiApiException;
import com.anitrack.domain.anime.gateway.BangumiGateway;
import com.anitrack.domain.anime.model.Anime;
import com.anitrack.infra.gateway.bangumi.dto.BangumiSearchRequestDTO;
import com.anitrack.infra.gateway.bangumi.dto.BangumiSearchResponseDTO;
import com.anitrack.infra.gateway.bangumi.dto.BangumiSubjectDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class BangumiGatewayImpl implements BangumiGateway {

    private final RestClient bangumiRestClient;
    private final BangumiConverter bangumiConverter;

    @Override
    public List<Anime> search(String keyword) {
        try {
            BangumiSearchResponseDTO response = bangumiRestClient.post()
                .uri("/v0/search/subjects?limit=20")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BangumiSearchRequestDTO.forAnimeKeyword(keyword))
                .retrieve()
                .body(BangumiSearchResponseDTO.class);

            if (response == null || response.getData() == null) {
                return List.of();
            }
            return response.getData().stream()
                .map(bangumiConverter::toDomain)
                .toList();
        } catch (RestClientException e) {
            throw new BangumiApiException("调用Bangumi搜索接口失败, keyword=" + keyword, e);
        }
    }

    @Override
    public Anime getById(Long bangumiId) {
        try {
            BangumiSubjectDTO dto = bangumiRestClient.get()
                    .uri("/v0/subjects/{id}", bangumiId)
                    .retrieve()
                    .body(BangumiSubjectDTO.class);
            return dto == null ? null : bangumiConverter.toDomain(dto);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        } catch (RestClientException e) {
            throw new BangumiApiException("获取 Bangumi 条目详情失败: " + bangumiId, e);
        }
    }

    @Override
    public List<Anime> listAnimeByYearRating(int year, double minRating) {
        List<Anime> all = new ArrayList<>();
        int limit = 20;
        int offset = 0;
        try {
            while (true) {
                int currentOffset = offset;
                BangumiSearchResponseDTO resp = bangumiRestClient.get()
                        .uri(uriBuilder -> uriBuilder.path("/v0/subjects")
                                .queryParam("type", 2)
                                .queryParam("year", year)
                                .queryParam("sort", "rank")
                                .queryParam("limit", limit)
                                .queryParam("offset", currentOffset)
                                .build())
                        .retrieve()
                        .body(BangumiSearchResponseDTO.class);
                if (resp == null || resp.getData() == null || resp.getData().isEmpty()) {
                    break;
                }
                all.addAll(resp.getData().stream().map(bangumiConverter::toDomain).toList());
                if (resp.getData().size() < limit) {
                    break;
                }
                offset += limit;
            }
        } catch (RestClientException e) {
            throw new BangumiApiException("调用 Bangumi 浏览接口失败, year=" + year, e);
        }
        return all.stream()
                .filter(a -> a.getRating() != null && a.getRating().getScore() != null && a.getRating().getScore() >= minRating)
                .toList();
    }
}
