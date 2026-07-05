package com.anitrack.infra.gateway.bangumi;

import com.anitrack.domain.anime.exception.BangumiApiException;
import com.anitrack.domain.anime.gateway.BangumiGateway;
import com.anitrack.domain.anime.model.Anime;
import com.anitrack.infra.gateway.bangumi.dto.BangumiSearchRequestDTO;
import com.anitrack.infra.gateway.bangumi.dto.BangumiSearchResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Component
@RequiredArgsConstructor
public class BangumiGatewayImpl implements BangumiGateway {

    private final RestClient bangumiRestClient;
    private final BangumiConverter bangumiConverter;

    @Override
    public List<Anime> search(String keyword) {
        BangumiSearchResponseDTO response;
        try {
            response = bangumiRestClient.post()
                .uri("/v0/search/subjects?limit=20")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BangumiSearchRequestDTO.forAnimeKeyword(keyword))
                .retrieve()
                .body(BangumiSearchResponseDTO.class);
        } catch (RestClientException e) {
            throw new BangumiApiException("调用Bangumi搜索接口失败, keyword=" + keyword, e);
        }

        if (response == null || response.getData() == null) {
            return List.of();
        }
        return response.getData().stream()
            .map(bangumiConverter::toDomain)
            .toList();
    }
}
