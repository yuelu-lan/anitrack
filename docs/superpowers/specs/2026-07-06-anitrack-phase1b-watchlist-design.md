# anitrack Phase 1b：追番核心（Watchlist，状态机）设计

## 背景与范围

Phase 1 内容为"番剧目录（Bangumi ACL）+ 追番核心（状态机/进度）"，两者拆成两份独立 spec 分别实现。番剧目录（`Anime` 上下文）已在 `2026-07-05-anitrack-phase1-anime-catalog-design.md` 完成并合并到 main。本文档覆盖追番核心（`Watchlist` 上下文）：用户对番剧的追看状态机、进度管理，以及依赖 `Anime` 上下文只读数据的跨上下文校验。

## 领域模型

```
WatchlistItem { id, userId, animeId, status, currentEpisode, updateTime }
enum WatchStatus { WANT_TO_WATCH, WATCHING, WATCHED, DROPPED }
```

### 唯一性约束

一个用户对同一番剧只能有一条 `WatchlistItem`（`userId + animeId` 唯一）。评分统一由 Review 上下文承载（Phase 2），不在此聚合冗余存储。

### 合法状态转移表

| 当前状态 | 可转移至 |
| --- | --- |
| WANT_TO_WATCH | WATCHING |
| WATCHING | WATCHED、DROPPED |
| WATCHED | （终态，无可转移状态） |
| DROPPED | WATCHING |

`WATCHED` 为终态；`DROPPED → WATCHING`（重新观看）保留弃番时的 `currentEpisode` 进度，不清零。"从追番列表移除"不是独立操作，等同于 `changeStatus(DROPPED)`，复用同一路由，不做物理删除。

### 进度更新规则

`updateProgress(episode, totalEpisodes)` 校验：

1. 当前状态必须是 `WATCHING`，否则不允许调用
2. `episode` 必须 > 0
3. `episode` 不能小于当前 `currentEpisode`（不允许倒退）
4. `totalEpisodes > 0` 时，`episode` 不能超过它；`totalEpisodes == 0` 视为总集数未知（如 Bangumi 未播出续作），不做上限校验

任一条件不满足抛 `IllegalWatchProgressException`。

### 包结构 `com.anitrack.domain.watchlist`

```
model/WatchlistItem.java
  - 聚合根，Builder私有（AccessLevel.PRIVATE）
  - static WatchlistItem create(userId, animeId)
      # 初始 status=WANT_TO_WATCH，currentEpisode=0
  - static WatchlistItem reconstitute(id, userId, animeId, status, currentEpisode, updateTime)
      # 本地读出重建，带id
  - WatchStatusChangedEvent changeStatus(WatchStatus newStatus)
      # 查内置转移表 TRANSITIONS（Map<WatchStatus, Set<WatchStatus>>），不合法抛 IllegalWatchStatusTransitionException
  - void updateProgress(Integer episode, Integer totalEpisodes)
      # 见上方四条校验规则，不合法抛 IllegalWatchProgressException
model/WatchStatusChangedEvent.java
  - record WatchStatusChangedEvent(userId, animeId, oldStatus, newStatus)
enums/WatchStatus.java
repo/WatchlistRepo.java              # 接口
  - WatchlistItem getByUserAndAnime(Long userId, Long animeId)   # 查不到返回 null
  - List<WatchlistItem> listByUser(Long userId, WatchStatus status)  # status 为 null 时不过滤
  - WatchlistItem add(WatchlistItem item)     # insert，返回带生成id的对象
  - void update(WatchlistItem item)           # 按id更新 status/currentEpisode/updateTime
service/WatchlistDomainService.java
  - WatchlistItem addToWatchlist(Long userId, Long animeId)
      # 查 AnimeRepo.getById 确认番剧存在（不存在抛 AnimeNotFoundException）
      # 查 WatchlistRepo.getByUserAndAnime 确认未重复添加（已存在抛 WatchlistItemAlreadyExistsException）
      # WatchlistItem.create() + WatchlistRepo.add()
  - WatchlistItem updateProgress(Long userId, Long animeId, Integer episode)
      # 查 WatchlistRepo.getByUserAndAnime（不存在抛 WatchlistItemNotFoundException）
      # 查 AnimeRepo.getById 取 totalEpisodes（理论上不该为null，防御性抛 AnimeNotFoundException）
      # 委托 item.updateProgress(episode, totalEpisodes) + WatchlistRepo.update()
exception/IllegalWatchStatusTransitionException.java extends AnitrackDomainException
exception/IllegalWatchProgressException.java extends AnitrackDomainException
exception/WatchlistItemAlreadyExistsException.java extends AnitrackDomainException
exception/WatchlistItemNotFoundException.java extends AnitrackDomainException
```

`changeStatus`/`getWatchlistItem`/`listMyWatchlist` 是单聚合操作或纯读查询，不需要跨上下文校验，直接在 `WatchlistApplication` 编排，不经过 `WatchlistDomainService`。

### `domain.anime.exception` 新增

```
AnimeNotFoundException extends AnitrackDomainException
  # WatchlistDomainService 校验番剧存在性失败时抛出，供 WatchlistApplication 转译为已有的 AppExceptionEnum.ANIME_NOT_FOUND
```

`AnimeApplication.getAnimeDetail()` 已有的直接空值判断（不经过领域异常）保持不变，不做重构——同一个 `ANIME_NOT_FOUND` 错误码允许有两条产生路径（应用层直接判断 / 领域层跨上下文校验抛出再转译），这是刻意的最小改动选择，不是遗漏。

## 依赖关系说明

`WatchlistDomainService` 依赖 `domain.anime.repo.AnimeRepo`（只读查询），不依赖 `domain.anime` 的其他内容，符合"跨上下文规则查询另一上下文只读仓储"的既有约定；不新增反向依赖。

## 持久化设计

### 表结构 `t_watchlist_item`

| 列名 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `BIGINT` | 主键自增 |
| `user_id` | `BIGINT` | 用户ID |
| `anime_id` | `BIGINT` | 番剧ID |
| `status` | `VARCHAR(20)` | 存枚举 `name()` 字符串 |
| `current_episode` | `INT` | 默认0 |
| `create_time` | `DATETIME` | 审计字段 |
| `update_time` | `DATETIME` | 审计字段 |

`UNIQUE KEY uk_user_anime (user_id, anime_id)`：兜底"一用户一记录"不变量，与领域层的重复校验形成双保险。

Flyway 脚本：`anitrack-starter/src/main/resources/db/migration/V3__create_watchlist_item_table.sql`（`V2` 已用于 `t_anime`）。

### Mapper / PO / RepoImpl

- `WatchlistItemPO`：字段与表列一一对应，`@Data`，比领域模型多 `createTime`（不回填进领域对象，沿用 `AnimePO`/`Anime` 的既有模式）
- `WatchlistItemMapper`：`selectByUserAndAnime`、`selectByUser`（`status` 参数可为 null，为 null 时 XML 里用 `<if test="status != null">` 不加该条件）、`insert`（`useGeneratedKeys`）、`updateById`
- `WatchlistItemConverter`（infra层，MapStruct）：`toPO(WatchlistItem)` 自动生成；`toDomain(WatchlistItemPO)` 手写 `default` 方法调用 `WatchlistItem.reconstitute(...)`（聚合根构造器私有，沿用 `AnimeConverter`/`UserConverter` 模式）
- `WatchlistRepoImpl implements WatchlistRepo`：
  - `getByUserAndAnime`：查不到返回 `null`
  - `listByUser(userId, status)`：直接透传给 Mapper 的可选条件查询
  - `add(item)`：`insert` 后用生成的自增id重建并返回新对象（对齐 Phase 0 "生成id必须回写"教训）
  - `update(item)`：按 `id` 更新 `status`/`currentEpisode`/`updateTime`

### `AnimeRepo` 新增方法

```
List<Anime> listByIds(List<Long> ids)   # IN 查询，供 Watchlist 侧批量拼接展示信息；ids为空列表时直接返回空列表，不查库
```

`AnimeMapper` 新增对应的 `selectByIds`（XML `<foreach>` 拼接 `IN`）。

## 应用层设计

### 新增异常码 `AppExceptionEnum`

| 常量 | code | message |
| --- | --- | --- |
| `WATCHLIST_ITEM_ALREADY_EXISTS` | 40201 | 该番剧已在追番列表中 |
| `WATCHLIST_ITEM_NOT_FOUND` | 40202 | 追番记录不存在 |
| `ILLEGAL_WATCH_STATUS_TRANSITION` | 40203 | 非法的追番状态转移 |
| `ILLEGAL_WATCH_PROGRESS` | 40204 | 追番进度更新不合法 |

`ANIME_NOT_FOUND`（已存在的 `40102`）在跨上下文校验失败时直接复用，不新增重复错误码。

### BO 模型

- `WatchlistItemBO { id, animeId, status, currentEpisode, updateTime }`：用于 add/changeStatus/updateProgress/getWatchlistItem 四个返回单条记录的用例
- `WatchlistItemViewBO { id, animeId, animeTitleCn, animeTitleOriginal, animeCoverUrl, status, currentEpisode, updateTime }`：仅用于 listMyWatchlist，由 `WatchlistAssembler` 拼接 `WatchlistItem` + `Anime` 产出

两者职责分离：前者是纯 Watchlist 数据的变更确认返回，后者是跨聚合的展示读模型，不合并成一个字段可空的类。

### `WatchlistApplication`

```
@Transactional
addToWatchlist(Long userId, Long animeId): WatchlistItemBO
  1. watchlistDomainService.addToWatchlist(userId, animeId)
  2. catch AnimeNotFoundException -> AnitrackAppException(ANIME_NOT_FOUND)
     catch WatchlistItemAlreadyExistsException -> AnitrackAppException(WATCHLIST_ITEM_ALREADY_EXISTS)
  3. 转换为 WatchlistItemBO 返回

@Transactional
changeStatus(Long userId, Long animeId, WatchStatus newStatus): WatchlistItemBO
  1. watchlistRepo.getByUserAndAnime(userId, animeId)，查不到抛 AnitrackAppException(WATCHLIST_ITEM_NOT_FOUND)
  2. item.changeStatus(newStatus)，catch IllegalWatchStatusTransitionException -> AnitrackAppException(ILLEGAL_WATCH_STATUS_TRANSITION)
  3. watchlistRepo.update(item)
  4. eventPublisher.publishEvent(event)（Spring 原生 ApplicationEventPublisher，本期无监听器消费）
  5. 转换为 WatchlistItemBO 返回

@Transactional
updateProgress(Long userId, Long animeId, Integer episode): WatchlistItemBO
  1. watchlistDomainService.updateProgress(userId, animeId, episode)
  2. catch WatchlistItemNotFoundException -> AnitrackAppException(WATCHLIST_ITEM_NOT_FOUND)
     catch AnimeNotFoundException -> AnitrackAppException(ANIME_NOT_FOUND)
     catch IllegalWatchProgressException -> AnitrackAppException(ILLEGAL_WATCH_PROGRESS)
  3. 转换为 WatchlistItemBO 返回

getWatchlistItem(Long userId, Long animeId): WatchlistItemBO
  1. watchlistRepo.getByUserAndAnime(userId, animeId)，查不到直接抛 AnitrackAppException(WATCHLIST_ITEM_NOT_FOUND)
  2. 转换为 WatchlistItemBO 返回（只读，不加 @Transactional）

listMyWatchlist(Long userId, WatchStatus status): List<WatchlistItemViewBO>
  1. watchlistRepo.listByUser(userId, status)
  2. 提取 animeId 列表 -> animeRepo.listByIds(animeIds)
  3. watchlistAssembler.assemble(items, animes)
  （只读，不加 @Transactional）
```

### `WatchlistAssembler`

纯函数式组装，`List<WatchlistItem> + List<Anime> -> List<WatchlistItemViewBO>`：按 `animeId` 用 `Map<Long, Anime>` 匹配。理论上不应出现匹配不到的情况（写入前已校验番剧存在），若发生（如数据被外部清理）则跳过该条并记 `WARN` 日志，不抛异常中断整个列表。

## Web层设计

### 路由

全部 POST，全部经过现有 `JwtAuthInterceptor` 鉴权（不加入拦截器排除名单），`userId` 一律从 `UserContextHolder.getUserId()` 获取，不放进请求体（防止越权指定他人 userId）。

| 方法 | 路径 | 请求体 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/watchlist/add` | `WatchlistAddReq{ animeId: @NotNull }` | 返回 `WatchlistItemResponse` |
| POST | `/api/watchlist/change_status` | `WatchlistChangeStatusReq{ animeId: @NotNull, status: @NotNull }` | 返回 `WatchlistItemResponse` |
| POST | `/api/watchlist/update_progress` | `WatchlistUpdateProgressReq{ animeId: @NotNull, episode: @NotNull }` | 返回 `WatchlistItemResponse` |
| POST | `/api/watchlist/detail` | `WatchlistDetailReq{ animeId: @NotNull }` | 返回 `WatchlistItemResponse` |
| POST | `/api/watchlist/list` | `WatchlistListReq{ status: 可选 }` | 返回 `List<WatchlistItemViewResponse>` |

Controller 层只做"是否提供必填字段"的校验（`@NotNull` + `@Valid`），业务规则合法性（状态转移是否允许、进度是否越界等）交给领域层，不在 Web 层重复。`status` 字段用 `WatchStatus` 枚举直接接收（Jackson 按 `name()` 反序列化），非法枚举值会在参数绑定阶段被 Spring 转换失败机制拦截，落入全局异常处理的"参数校验异常"分支。

### 响应对象

```java
WatchlistItemResponse { id, animeId, status, currentEpisode, updateTime }
WatchlistItemViewResponse { id, animeId, animeTitleCn, animeTitleOriginal, animeCoverUrl, status, currentEpisode, updateTime }
```

## 异常处理链路

```
WatchlistItem.changeStatus() 转移不合法
  → 抛 IllegalWatchStatusTransitionException（domain.watchlist.exception）
  → WatchlistApplication 捕获，转译为 AnitrackAppException(ILLEGAL_WATCH_STATUS_TRANSITION)

WatchlistItem.updateProgress() 校验不通过
  → 抛 IllegalWatchProgressException（domain.watchlist.exception）
  → WatchlistApplication 捕获，转译为 AnitrackAppException(ILLEGAL_WATCH_PROGRESS)

WatchlistDomainService.addToWatchlist() 番剧不存在 / 重复添加
  → 抛 AnimeNotFoundException / WatchlistItemAlreadyExistsException
  → WatchlistApplication 捕获，转译为 AnitrackAppException(ANIME_NOT_FOUND / WATCHLIST_ITEM_ALREADY_EXISTS)

WatchlistDomainService.updateProgress() 追番记录不存在
  → 抛 WatchlistItemNotFoundException
  → WatchlistApplication 捕获，转译为 AnitrackAppException(WATCHLIST_ITEM_NOT_FOUND)
```

`getWatchlistItem` 查不到记录属于正常业务分支（不是领域规则冲突），由 `WatchlistApplication` 直接判断并抛 `AnitrackAppException(WATCHLIST_ITEM_NOT_FOUND)`，不经过领域异常，与 `AnimeApplication.getAnimeDetail` 处理 `ANIME_NOT_FOUND` 的方式一致。

## 测试策略

- **Domain**：
  - `WatchlistItemTest`：覆盖状态机全部合法/非法转移组合；`updateProgress` 的四类边界（非 WATCHING 调用、episode≤0、进度倒退、超过 totalEpisodes）；`totalEpisodes=0` 时放开上限的场景
  - `WatchlistDomainServiceTest`（Mockito）：mock `WatchlistRepo`+`AnimeRepo`，覆盖 `addToWatchlist`/`updateProgress` 的正常路径 + 各自的两种失败路径
- **Infrastructure**：`WatchlistRepoImplTest`，mock Mapper+Converter 验证 `add`/`update`/`listByUser`（含 status 为 null 和非 null 两种）的行为
- **Application**：`WatchlistApplicationTest`（JUnit5 + Mockito + AssertJ），mock `WatchlistDomainService`/`WatchlistRepo`/`AnimeRepo`/`WatchlistAssembler`/`ApplicationEventPublisher`，覆盖全部异常转译路径 + 验证事件确实被发布（`verify(eventPublisher, times(1)).publishEvent(...)`）
- **Web**：`WatchlistControllerTest`（`@WebMvcTest` + `MockMvc` + `@MockBean`），覆盖 5 个接口的正常路径、未带 token 的 401、必填字段缺失的参数校验失败场景

## 明确不做的事（YAGNI，避免范围蔓延）

- 不实现 `WatchStatusChangedEvent` 的任何监听器（Phase 3 提醒评分功能需要时再加）
- 不做分页（`listMyWatchlist` 一次性返回该用户全部记录，个人项目数据量小）
- 不做物理删除接口（"移除追番"即 `changeStatus(DROPPED)`，复用现有路由）
- 不给 `WatchlistItem` 加评分字段（评分由 Review 上下文承载，Phase 2）

## 后续（不在本 spec 范围）

Review 上下文（评分/评论，Phase 2）依赖本 spec 的 `WatchlistRepo` 查询 `WATCHED` 状态作为跨上下文校验前提，在本 spec 完成并合并后另开一份 spec 处理。
