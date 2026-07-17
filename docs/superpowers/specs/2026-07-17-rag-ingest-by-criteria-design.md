# anitrack RAG 按条件批量采集 设计

> 日期：2026-07-17
> 范围：为 RAG 知识库新增「按年份+评分批量采集」能力 + 前端采集管理页
> 依赖：`2026-07-15-rag-design.md`（RAG 基础设施已就绪：rag-service、Chroma collection `anime_wiki`、`BangumiGateway.getById`、`POST /api/rag/ingest`）

## 1. 背景与目标

RAG 基础设施已就绪，但采集只能手动传 `bangumiIds`（`POST /api/rag/ingest`），无 UI 入口。用户希望「一键嵌入 2026 年评分≥7.0 的动画」。

目标：

- 后端新增按年份+评分筛选并批量入库的端点
- 前端新增采集管理页（表单 + 按钮 + 结果展示）
- 不引入新依赖，复用现有 RAG 链路（`RagApplication.toDocument` + `RagGateway.ingest`）

## 2. 关键决策

| 维度 | 决策 |
|---|---|
| Bangumi 列表接口 | 用正式 `GET /v0/subjects`（type+year+sort），**不用**实验性 `POST /v0/search/subjects`（虽支持 rating 过滤，但 OpenAPI 标注「schema 和行为可能随时改动」） |
| 评分过滤位置 | 客户端过滤（`/v0/subjects` 不支持 rating 过滤），响应 `data` 含 `rating.score`，拉完全部后过滤 |
| 分页 | `limit=50`（OpenAPI `maximum=50`、`default=30`），`offset` 递增 50 直到空页 |
| 采集编排 | 新 `ingestByCriteria` 直接用搜索结果 `Anime` 组装 `RagDocument`，**不走 `ingestAnimeWiki`**（省二次 `getById`，因 `/v0/subjects` 响应已是完整 `Subject`） |
| Java 代理 | RestClient 可选 `proxyHost`/`proxyPort`（`SimpleClientHttpRequestFactory` 默认不读系统 http 代理），留空则不走代理 |
| 前端 timeout | service 层 per-request `timeout: 120000` 覆盖全局 `10000`（ingest ~40s） |

## 3. 数据流

```
前端表单(year, minRating)
   ▼ POST /api/rag/ingest_by_criteria?year=&minRating= (Authorization: Bearer <JWT>)
RagIngestController
   ▼
RagApplication.ingestByCriteria(year, minRating)
   ▼
BangumiGateway.listAnimeByYearRating(year, minRating)
   ├─ 循环 GET /v0/subjects?type=2&year&sort=rank&limit=50&offset=N
   ├─ 每页 response.data (List<BangumiSubjectDTO>) → BangumiConverter.toDomain → Anime
   └─ 客户端过滤 rating.score ≥ minRating
   ▼ List<Anime>
RagApplication.toDocument(anime) → List<RagDocument>   (复用现有方法)
   ▼
RagGateway.ingest(docs) ──HTTP POST──▶ rag-service /ingest
   ▼
rag-service: embedding(硅基流动) → 写入 Chroma "anime_wiki"（同 animeId 覆盖）
```

关键：`/v0/subjects` 响应 `data` 是完整 `Subject`（含 rating/tags/summary/infobox），经 `BangumiConverter.toDomain` 转出的 `Anime` 字段完整，可直接 `toDocument` 入库，**无需逐条 `getById`**——区别于 `ingestAnimeWiki`（传 ids 再逐个 `getById`）。

## 4. 接口契约

### `POST /api/rag/ingest_by_criteria`

```
Header: Authorization: Bearer <JWT>
Query: year=<int>, minRating=<double>
Response: ResponseResult<Integer>   // 入库数
  {"status":1,"message":null,"data":54}
```

幂等：同 `animeId` 覆盖（Chroma 按 `metadata.animeId`，沿用现有 `/ingest` 语义）。

## 5. Java 侧落点（按 DDD 分层，不新增 Maven 模块）

| 层 | 改动 | 说明 |
|---|---|---|
| `domain/anime/gateway` | `BangumiGateway` 加 `listAnimeByYearRating(int year, double minRating)` | 返回过滤后 `List<Anime>` |
| `infra/gateway/bangumi` | `BangumiGatewayImpl` 实现：`GET /v0/subjects` 分页 + 客户端过滤 | 复用 `bangumiRestClient` + `BangumiConverter`（`data` 元素是 `BangumiSubjectDTO`） |
| `infra/config` | `BangumiProperties` 加 `proxyHost`/`proxyPort`；`BangumiClientConfig` `setProxy` | 可选代理，非空才生效 |
| `application` | `RagApplication.ingestByCriteria` | 调 `listAnimeByYearRating` + `toDocument` + `ragGateway.ingest` |
| `starter` | `RagIngestController` 加 `POST /ingest_by_criteria` | 复用现有 JWT 鉴权 |

## 6. 前端集成（webui）

- 新增 `src/pages/rag/ingest.tsx`：`year` + `minRating` 两个 `InputNumber` + 「开始采集」`Button`(loading)，结果用 `<Alert type="success">` 展示入库数
- 新增 `src/services/rag.ts`：`ingestByCriteria`，`params`（query，对应后端 `@RequestParam`）+ `timeout: 120000`
- `.umirc.ts` 加 `/rag/ingest` 路由（`wrappers: ['@/wrappers/auth']`）
- `src/layouts/index.tsx` `MENU_ITEMS` 加「RAG 采集」项，**放在「番剧问答」前**（`selectedKey` 用 `pathname.startsWith(item.key)`，更长 key 靠前避免误高亮）

## 7. Bangumi OpenAPI 参考

源文件：`github.com/bangumi/api` 仓库 `open-api/v0.yaml`（文档站 `bangumi.github.io/api` 是 Swagger SPA，WebFetch 抓不到，直接读 yaml 源）。

- `GET /v0/subjects`：正式接口（「第一页 cache 24h」），params `type`/`cat`/`sort`(date|rank)/`year`/`month`/`limit`/`offset`，`limit` `maximum=50` `default=30`
- `POST /v0/search/subjects`：实验性，filter 支持 `type`/`air_date`(`[">=YYYY-MM-DD"]`)/`rating`(`[">=7"]`)/`rating_count`/`rank`，`sort` enum `match`/`heat`/`rank`/`score`
- `Subject` schema 含 `rating`(score/rank/total/count)，列表与详情响应均含

## 8. 验证

- `mvn clean install`：61 tests 全过（含 2 个新单测）
- `POST /api/rag/ingest_by_criteria?year=2026&minRating=7.0` → 入库 54 部（~40s）
- 浏览器 `http://localhost:8000` → 侧栏「RAG 采集」→ 2026/7.0 → 点采集 → success Alert「入库 54 部」

## 9. 后续扩展（不做）

- 增量采集（只采新番）：`metadata.animeId` 去重，目前全量覆盖
- 按 `air_date` 范围（跨年/季度）：改用 `/v0/search/subjects` 的 `air_date` filter（实验性）
- `/api/rag/chat` 端到端：受阻于 rag-service 的 LLM 兼容（DeepSeek `streamUsage`），独立问题
