# anitrack Phase 1a：番剧目录（Anime，Bangumi ACL）设计

## 背景与范围

Phase 1 内容为"番剧目录（Bangumi ACL）+ 追番核心（状态机/进度）"，两者拆成两份独立 spec 分别实现。本文档只覆盖番剧目录（`Anime` 上下文）：通过防腐层（ACL）对接 Bangumi 官方 API，将外部条目数据转换为本地只读缓存。追番核心（`Watchlist` 上下文）在本 spec 完成、评审通过后另开一份 spec。

## Bangumi API 确认信息

来源：[bangumi/api](https://github.com/bangumi/api) 仓库 `open-api/v0.yaml`（OpenAPI 规范，非训练知识猜测）。

### 搜索条目

```
POST https://api.bgm.tv/v0/search/subjects?limit=20
Content-Type: application/json
User-Agent: <标识调用方的UA，Bangumi要求>

{
  "keyword": "关键字",
  "filter": { "type": [2] }
}
```

- `type: [2]` 过滤条目类型为动画（`SubjectType`：1书籍、2动画、3音乐、4游戏、6三次元）
- 响应：`{ total, limit, offset, data: Subject[] }`
- 该接口无需鉴权（未声明 `security`）

### 条目详情

```
GET https://api.bgm.tv/v0/subjects/{subject_id}
```

- 响应为单个 `Subject`，服务端缓存 300s
- 无需鉴权

### Subject 关键字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | integer | Bangumi 条目 ID |
| `name` | string | 原名（日文/原语言），几乎总有值 |
| `name_cn` | string | 中文名，允许为空字符串 |
| `summary` | string | 简介 |
| `date` | string | 放送日期 `YYYY-MM-DD`，**非必填字段**，可能不存在 |
| `images.large`/`common`/`medium`/`small`/`grid` | string | 封面图 URL，取 `large` |
| `eps` | integer | wiki 维护的总集数（"话数"），完结番权威，连载中/资料不全时可能为 0 |
| `total_episodes` | integer | 数据库中已收录的分集条目数，随连载推进增长，可能滞后于实际集数 |

**总集数取 `eps`**：对完结番更权威；连载中番剧 `eps` 为 0 时视为"总集数未知"，由 Watchlist 上下文的跨上下文校验逻辑处理（本 spec 不涉及）。

## 领域模型

```
Anime { id, bangumiId(外部ID), titleCn, titleOriginal, coverUrl, totalEpisodes, airDate, summary }
```

- `titleCn` ← `name_cn`（可能为空字符串）；`titleOriginal` ← `name`。两个字段各自存储，不做回退判断，显示逻辑留给调用方（前端/未来的应用层）决定。
- `totalEpisodes` ← `eps`
- `airDate` 可为空（Bangumi 字段非必填）
- 业务代码不允许直接修改这些字段，只能通过 ACL 同步更新——"真相"在外部系统（沿用顶层设计文档原则）

### 包结构 `com.anitrack.domain.anime`

```
model/Anime.java
  - 聚合根，Builder私有（AccessLevel.PRIVATE），字段与上述模型一致
  - static Anime fromBangumi(bangumiId, titleCn, titleOriginal, coverUrl, totalEpisodes, airDate, summary)
      # 无本地id，Bangumi同步产物，供 upsert 前使用
  - static Anime reconstitute(id, bangumiId, titleCn, titleOriginal, coverUrl, totalEpisodes, airDate, summary)
      # 本地读出重建，带id
gateway/BangumiGateway.java   # 接口
  - List<Anime> search(String keyword)
repo/AnimeRepo.java           # 接口
  - Anime getById(Long id)
  - Anime getByBangumiId(Long bangumiId)
  - Anime upsert(Anime anime)   # 按bangumiId是否已存在决定insert或update，返回带id的Anime
exception/BangumiApiException.java extends AnitrackDomainException
  # BangumiGateway 调用失败（超时/4xx/5xx）时抛出
```

## ACL 防腐层设计（基础设施层）

`BangumiGateway` 是 anitrack 唯一的外部系统防腐层，接口定义在 `domain.anime.gateway`，实现在 `infrastructure`。

### 包结构 `com.anitrack.infra.gateway.bangumi`

```
dto/BangumiSearchRequestDTO.java   # keyword, filter.type
dto/BangumiSubjectDTO.java         # id, name, name_cn, summary, date, images, eps, total_episodes
dto/BangumiImagesDTO.java          # large, common, medium, small, grid
dto/BangumiSearchResponseDTO.java  # total, limit, offset, data: BangumiSubjectDTO[]
BangumiGatewayImpl.java implements BangumiGateway
  # 调用 RestClient POST /v0/search/subjects，捕获网络/HTTP异常统一抛 BangumiApiException
BangumiConverter.java
  # BangumiSubjectDTO -> Anime.fromBangumi(...)，字段直接映射，不做业务判断
```

外部 DTO（`Bangumi*DTO`）只在 `infrastructure` 层出现，不泄漏到 `domain`/`application`，与 `BangumiGateway` 接口的返回类型（`Anime`）严格隔离。

### RestClient 配置

`com.anitrack.infra.config.BangumiClientConfig`：

- `RestClient` bean，`baseUrl = https://api.bgm.tv`
- 固定请求头 `User-Agent`（标识 anitrack 项目，Bangumi 要求可识别调用方）
- 设置连接/读取超时（避免外部服务不可用时线程被无限占用）

## 持久化设计

### 表结构 `t_anime`

| 列名 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `BIGINT` | 主键自增 |
| `bangumi_id` | `BIGINT` | Bangumi 外部ID，唯一索引 `uk_bangumi_id` |
| `title_cn` | `VARCHAR(255)` | 中文名，允许空字符串 |
| `title_original` | `VARCHAR(255)` | 原名 |
| `cover_url` | `VARCHAR(512)` | 封面图URL |
| `total_episodes` | `INT` | 总集数，0表示未知 |
| `air_date` | `DATE` | 放送日期，允许 NULL |
| `summary` | `TEXT` | 简介 |
| `create_time` | `DATETIME` | 审计字段 |
| `update_time` | `DATETIME` | 审计字段 |

Flyway 脚本：`anitrack-starter/src/main/resources/db/migration/V2__create_anime_table.sql`（`V1` 已用于 `t_user`）。

### Mapper / PO / RepoImpl

- `AnimePO`：字段与表列一一对应，`@Data`，不写业务逻辑（沿用 `anitrack-persist-rules.md`）
- `AnimeMapper`：`selectById`、`selectByBangumiId`、`insert`（`useGeneratedKeys`）、`updateById`
- `AnimeConverter`（infra层，MapStruct）：`AnimePO <-> Anime`
- `AnimeRepoImpl implements AnimeRepo`：
  - `getById`/`getByBangumiId`：查不到返回 `null`（沿用 `UserRepoImpl` 风格）
  - `upsert(Anime anime)`：先 `selectByBangumiId` 判断，存在则 `updateById` 后返回原id对应的重建对象，不存在则 `insert` 后回写自增id（沿用 `UserRepo.save()` 回写id的方式）。不使用 `ON DUPLICATE KEY UPDATE`，保持与现有 Mapper 规范一致的"先查再写"模式。

## 应用层设计

### `AnimeApplication`

```
searchAnime(String keyword): List<AnimeBO>
  1. BangumiGateway.search(keyword) 获取 List<Anime>（无本地id）
  2. 逐条调用 AnimeRepo.upsert(anime) 落库/刷新，得到带id的Anime
  3. 转换为 AnimeBO 列表返回
  4. 捕获 BangumiApiException，转译为 AnitrackAppException(AppExceptionEnum.BANGUMI_SERVICE_UNAVAILABLE)

getAnimeDetail(Long animeId): AnimeBO
  1. AnimeRepo.getById(animeId)，本地只读，不回退调用 Bangumi
  2. 找不到抛 AnitrackAppException(AppExceptionEnum.ANIME_NOT_FOUND)
  3. 转换为 AnimeBO 返回
```

`AnimeBO { id, bangumiId, titleCn, titleOriginal, coverUrl, totalEpisodes, airDate, summary }`，字段与 `Anime` 对等，物理隔离（沿用 `UserBO` 模式）。`AnimeConverter`（application层，MapStruct）：`Anime -> AnimeBO`。

### 新增异常码 `AppExceptionEnum`

| 常量 | code | message |
| --- | --- | --- |
| `BANGUMI_SERVICE_UNAVAILABLE` | 40101 | 番剧信息服务暂时不可用，请稍后重试 |
| `ANIME_NOT_FOUND` | 40102 | 番剧不存在 |

（编号延续现有 `USERNAME_ALREADY_EXISTS(40001)`/`LOGIN_FAILED(40002)` 段位，Anime 上下文使用 401xx 段）

## Web层设计

### 路由

| 方法 | 路径 | 请求体 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/anime/search` | `AnimeSearchReq{ keyword: @NotBlank }` | 返回 `List<AnimeResponse>` |
| POST | `/api/anime/detail` | `AnimeDetailReq{ animeId: @NotNull }` | 返回 `AnimeResponse` |

两个接口均经过现有 `JwtAuthInterceptor` 鉴权（不加入拦截器排除名单）。

### 响应对象

```java
AnimeResponse { id, bangumiId, titleCn, titleOriginal, coverUrl, totalEpisodes, airDate, summary }
```

`airDate` 按 `anitrack-web-rules.md` 数据类型规范以 ISO-8601 字符串（`YYYY-MM-DD`）返回；为 `null` 时保持 `null`（对象类型字段空值处理规范）。

## 异常处理链路

```
BangumiGatewayImpl 调用失败
  → 抛 BangumiApiException（domain.anime.exception，extends AnitrackDomainException）
  → AnimeApplication 捕获，转译为 AnitrackAppException(BANGUMI_SERVICE_UNAVAILABLE)
  → GlobalExceptionHandler 按"应用层异常"分支处理，HTTP 200 + 业务状态码
```

`AnimeRepo.getById` 查不到本地缓存属于正常业务分支（不是外部异常），由 `AnimeApplication` 直接判断并抛 `AnitrackAppException(ANIME_NOT_FOUND)`，不经过领域异常。

## 测试策略

- **Domain**：`AnimeTest`，覆盖 `fromBangumi`/`reconstitute` 工厂方法字段赋值正确性
- **Infrastructure**：
  - `BangumiConverterTest`：`BangumiSubjectDTO -> Anime` 字段映射（含 `name_cn` 为空字符串、`date` 缺失两种边界情况）
  - `AnimeRepoImplTest` 可选，若覆盖则用真实数据库集成测试验证 `upsert` 的插入/更新分支
- **Application**：`AnimeApplicationTest`（JUnit5 + Mockito + AssertJ），Mock `BangumiGateway`/`AnimeRepo`，覆盖：
  - 搜索成功，落库并返回列表
  - 网关抛 `BangumiApiException` 时正确转译为 `AnitrackAppException`
  - 详情查询未命中本地缓存时抛 `ANIME_NOT_FOUND`
- **Web**：`AnimeControllerTest`（`@WebMvcTest` + `MockMvc` + `@MockBean`），覆盖参数校验（`keyword`/`animeId` 缺失）与正常响应结构

## 文档同步

本 spec 的字段调整（`title` 拆分为 `titleCn`/`titleOriginal`）需要同步更新顶层设计文档中的 `Anime` 模型定义：

- `docs/superpowers/specs/2026-07-02-anitrack-design.md`
- `docs/rules/anitrack-domain-rules.md`

## 后续（不在本 spec 范围）

Watchlist 上下文（追番状态机、跨上下文进度校验、`WatchStatusChangedEvent`）在本 spec 完成后另开一份 spec 处理，届时会依赖本 spec 中的 `AnimeRepo.getById` 与 `Anime.totalEpisodes` 字段。
