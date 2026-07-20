package com.anitrack.application.service;

import com.anitrack.domain.anime.gateway.BangumiGateway;
import com.anitrack.domain.anime.model.Anime;
import com.anitrack.domain.rag.gateway.RagGateway;
import com.anitrack.domain.rag.model.RagDocument;
import com.anitrack.domain.rag.model.RagDocumentSummary;
import com.anitrack.domain.rag.model.RagQuery;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagApplication {

    private final BangumiGateway bangumiGateway;
    private final RagGateway ragGateway;

    public int ingestAnimeWiki(List<Long> bangumiIds) {
        List<Anime> animes = bangumiIds.stream()
                .map(this::fetchSafely)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (animes.isEmpty()) {
            log.warn("无可用番剧数据入库");
            return 0;
        }
        List<RagDocument> docs = animes.stream()
                .map(this::toDocument)
                .toList();
        return ragGateway.ingest(docs);
    }

    public int ingestByCriteria(int year, double minRating) {
        List<Anime> animes = bangumiGateway.listAnimeByYearRating(year, minRating);
        if (animes.isEmpty()) {
            log.warn("未找到符合条件番媒 year={} minRating={}", year, minRating);
            return 0;
        }
        List<RagDocument> docs = animes.stream().map(this::toDocument).toList();
        return ragGateway.ingest(docs);
    }

    public List<RagDocumentSummary> listDocuments() {
        return ragGateway.listDocuments();
    }

    public Stream<String> streamChat(RagQuery query) {
        return ragGateway.streamQuery(query);
    }

    private Anime fetchSafely(Long id) {
        try {
            return bangumiGateway.getById(id);
        } catch (Exception e) {
            log.error("拉取 Bangumi 条目失败 animeId={}", id, e);
            return null;
        }
    }

    private RagDocument toDocument(Anime anime) {
        StringBuilder sb = new StringBuilder();
        sb.append("标题：").append(str(anime.getTitleCn())).append(" / ").append(str(anime.getTitleOriginal())).append("\n");
        sb.append("简介：").append(str(anime.getSummary())).append("\n");
        sb.append("放送日期：").append(anime.getAirDate() == null ? "未知" : anime.getAirDate()).append("\n");
        sb.append("集数：").append(anime.getTotalEpisodes() == null ? "未知" : anime.getTotalEpisodes()).append("\n");
        sb.append("平台：").append(str(anime.getPlatform())).append("\n");
        if (anime.getTags() != null && !anime.getTags().isEmpty()) {
            sb.append("标签：").append(anime.getTags().stream()
                    .map(t -> t.getName() + "(" + t.getCount() + ")")
                    .collect(Collectors.joining(","))).append("\n");
        }
        if (anime.getRating() != null && anime.getRating().getScore() != null && anime.getRating().getTotal() != null) {
            sb.append("评分：").append(anime.getRating().getScore())
              .append("（").append(anime.getRating().getTotal()).append("人）\n");
        }
        return RagDocument.of(sb.toString(), String.valueOf(anime.getBangumiId()),
                str(anime.getTitleCn()), str(anime.getTitleOriginal()),
                anime.getAirDate() == null ? null : anime.getAirDate().toString(),
                scoreOrNull(anime), totalOrNull(anime));
    }

    private String str(String s) { return s == null ? "" : s; }

    private Double scoreOrNull(Anime anime) {
        return anime.getRating() == null ? null : anime.getRating().getScore();
    }

    private Integer totalOrNull(Anime anime) {
        return anime.getRating() == null ? null : anime.getRating().getTotal();
    }
}
