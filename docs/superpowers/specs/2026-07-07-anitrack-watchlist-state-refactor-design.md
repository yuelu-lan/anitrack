# anitrack：追番状态机与进度逻辑重构设计

## 背景与范围

Phase 1b 已实现的追番状态机存在若干语义问题：想看阶段无法直接标记看完/弃番、在看阶段进度不能倒退、追番入口不校验番剧总集数、转完看时进度未自动置满等。本次重构在不改变模块划分与持久化方案的前提下，重新定义状态机边、进度校验规则与 `changeStatus` 的进度联动行为，并顺手修复 `updateProgress` 不刷新 `updateTime` 的遗留问题。

仅覆盖 `Watchlist` 上下文聚合根行为与相关编排层调整，不涉及 `Review`、`Anime` 上下文内部逻辑变更，不新增 API 端点。

## 状态机

```
WANT_TO_WATCH → WATCHING / WATCHED / DROPPED
WATCHING      → WATCHED / DROPPED
WATCHED       → （终态）
DROPPED       → WATCHING / WANT_TO_WATCH
```

与现状差异：

| 边 | 现状 | 本次 |
| --- | --- | --- |
| `WANT_TO_WATCH → WATCHED` | 不允许 | 新增允许 |
| `WANT_TO_WATCH → DROPPED` | 不允许 | 新增允许 |
| `DROPPED → WATCHING` | 允许 | 不变 |
| `DROPPED → WANT_TO_WATCH` | 不允许 | 新增允许 |
| `WATCHED → 任意` | 不允许 | 不变（终态） |

`WATCHED` 仍为终态。`DROPPED → WATCHING / WANT_TO_WATCH`（弃番恢复或重置为想看）保留弃番时的 `currentEpisode`，不清零、不重置。

## 各状态行为规则

### 想看（WANT_TO_WATCH）

- 进度 = 0，不可调用 `updateProgress`
- 可转：在看（进度保持 0）/ 看完（进度置为 `totalEpisodes`）/ 弃番（进度保持）

### 在看（WATCHING）

- 进入时进度保持当前值（不强制置 1，不重置）
- 可转：看完（进度置为 `totalEpisodes`）/ 弃番（进度保持）
- 可改进度：允许任意值，含倒退（如 4→2）
- **取消**"进度达到最后一集自动转看完"逻辑（不在 `updateProgress` 内触发状态变更）

### 看完（WATCHED）

- 终态，不可转其他状态，不可改进度

### 弃番（DROPPED）

- 可恢复到在看或想看，恢复时进度保持当前值不清零
- 不可直接转看完，不可改进度

## 进度校验规则

`WatchlistItem.updateProgress(episode, totalEpisodes)` 校验顺序：

1. 当前状态必须是 `WATCHING`，否则抛 `IllegalWatchProgressException`
2. `episode` 必须 `>= 0`（允许 0，兼容"在看但进度 0"状态）
3. `totalEpisodes != null && totalEpisodes > 0` 时，`episode` 不能超过它；为 `null` 或 `<= 0` 视为总集数未知，不做上限校验
4. 校验通过后更新 `currentEpisode` 并刷新 `updateTime`

与现状差异：

- 校验 2 下限由 `> 0` 改为 `>= 0`
- 删除"不能倒退"校验（原校验 3）
- 保留总集数上限校验，但"总集数未知"的判定由 `== 0` 放宽为 `null 或 <= 0`
- 新增刷新 `updateTime`

> 进度校验放宽后，`WatchlistItemTest` 中 `updateProgress_whenEpisodeIsZero_shouldThrowException`、`updateProgress_whenEpisodeRegresses_shouldThrowException` 两条测试的语义反转，需改为断言成功。

## changeStatus 的进度联动

`WatchlistItem.changeStatus` 现有无参签名仅做状态转移，不处理进度。本次需求要求"转看完时进度置为 `totalEpisodes`"，因此聚合根需要拿到 `totalEpisodes`。

方案：`changeStatus` 方法签名扩展为 `changeStatus(WatchStatus newStatus, Integer totalEpisodes)`，由调用方（DomainService）从 `AnimeRepo` 取 `totalEpisodes` 传入。

行为：

| 目标状态 | 进度处理 |
| --- | --- |
| `WATCHED` | `currentEpisode = totalEpisodes`（需 `totalEpisodes` 有效，否则抛 `AnimeTotalEpisodesInvalidException`——见下） |
| `WATCHING` | 保持不变 |
| `DROPPED` | 保持不变 |
| `WANT_TO_WATCH` | 保持不变 |

转 `WATCHED` 时若 `totalEpisodes` 为 `null` 或 `<= 0`：抛 `AnimeTotalEpisodesInvalidException`（与追番入口校验复用同一异常，位于 `domain/anime/exception/`），消息为"番剧总集数无效，无法追番"。应用层统一翻译为 `ANIME_TOTAL_EPISODES_INVALID`。

> 此场景理论上不会出现——追番入口已校验总集数有效。但聚合根作为不变量守护者，仍需对此自我保护，不依赖调用方契约。`AnimeTotalEpisodesInvalidException` 构造参数为 `animeId`，聚合根内持有 `animeId`，可正常抛出。

事件发布不变：`changeStatus` 返回 `WatchStatusChangedEvent`，由应用层 `ApplicationEventPublisher` 发布。

## 追番入口校验

`WatchlistDomainService.addToWatchlist` 在确认 anime 存在后，校验 `anime.getTotalEpisodes()`：

- 为 `null` 或 `<= 0` 抛新异常 `AnimeTotalEpisodesInvalidException(animeId)`（继承 `AnitrackDomainException`）
- 应用层翻译为 `AppExceptionEnum.ANIME_TOTAL_EPISODES_INVALID`（新增枚举，code `40103`，消息"番剧总集数无效，无法追番"）

校验仅在 `addToWatchlist` 入口进行；后续 `updateProgress`、`changeStatus` 不重复校验（依赖追番时已保证有效），但聚合根 `changeStatus(WATCHED)` 仍自我保护。

## 应用层编排调整

### changeStatus 改走 DomainService

现状 `WatchlistApplication.changeStatus` 直接操作 `watchlistRepo`，与 `addToWatchlist`/`updateProgress` 走 `WatchlistDomainService` 的风格不一致。本次因 `changeStatus` 需要取 `totalEpisodes`，改为统一走 DomainService：

- `WatchlistDomainService` 新增 `changeStatus(Long userId, Long animeId, WatchStatus newStatus)` 方法：取 item、取 anime、调聚合根 `changeStatus(newStatus, anime.getTotalEpisodes())`、`watchlistRepo.update(item)`、返回 `WatchlistItem`
- `WatchlistApplication.changeStatus` 改为委托 DomainService，捕获 `IllegalWatchStatusTransitionException` 翻译为 `ILLEGAL_WATCH_STATUS_TRANSITION`，捕获 `AnimeTotalEpisodesInvalidException` 翻译为 `ANIME_TOTAL_EPISODES_INVALID`，捕获 `WatchlistItemNotFoundException`/`AnimeNotFoundException` 翻译为对应枚举，发布 `WatchStatusChangedEvent`
- 应用层不再直接依赖 `watchlistRepo` 执行 `changeStatus`（`updateProgress`/`getWatchlistItem`/`listMyWatchlist` 等其他方法的 repo 依赖暂不动，保持最小改动）

### DomainServiceConfig 调整

`WatchlistDomainService` 是手动 `@Bean` 注册（见 `DomainServiceConfig`），构造参数不变（仍为 `WatchlistRepo` + `AnimeRepo`），无需调整配置。

## 异常翻译表

| Domain 异常 | AppExceptionEnum | code | 消息 |
| --- | --- | --- | --- |
| `AnimeNotFoundException` | `ANIME_NOT_FOUND` | 40102 | 番剧不存在 |
| `AnimeTotalEpisodesInvalidException`（新增） | `ANIME_TOTAL_EPISODES_INVALID`（新增） | 40103 | 番剧总集数无效，无法追番 |
| `WatchlistItemNotFoundException` | `WATCHLIST_ITEM_NOT_FOUND` | 40202 | 追番记录不存在 |
| `WatchlistItemAlreadyExistsException` | `WATCHLIST_ITEM_ALREADY_EXISTS` | 40201 | 该番剧已在追番列表中 |
| `IllegalWatchStatusTransitionException` | `ILLEGAL_WATCH_STATUS_TRANSITION` | 40203 | 非法的追番状态转移 |
| `IllegalWatchProgressException` | `ILLEGAL_WATCH_PROGRESS` | 40204 | 追番进度更新不合法 |

## 涉及修改的文件

### domain 层

- `WatchlistItem.java` —— `TRANSITIONS` 表扩展；`updateProgress` 校验调整（下限 `>=0`、删倒退、刷新 `updateTime`）；`changeStatus` 签名扩展为 `(newStatus, totalEpisodes)` 并实现转 `WATCHED` 时进度置满
- `AnimeTotalEpisodesInvalidException.java`（新增）—— `domain/anime/exception/` 下

### application 层

- `WatchlistDomainService.java` —— `addToWatchlist` 加总集数校验；新增 `changeStatus(userId, animeId, newStatus)` 方法
- `WatchlistApplication.java` —— `changeStatus` 改为委托 DomainService，调整异常捕获
- `AppExceptionEnum.java` —— 新增 `ANIME_TOTAL_EPISODES_INVALID`

### 测试

- `WatchlistItemTest.java` —— 调整状态机相关测试（新增 `WANT_TO_WATCH → WATCHED/DROPPED`、`DROPPED → WANT_TO_WATCH`；删除"想看→看完非法"断言）；调整进度测试（episode=0 改为成功、倒退改为成功、新增 `updateTime` 刷新断言）；新增 `changeStatus(WATCHED)` 进度置满测试
- `WatchlistDomainServiceTest.java` —— `addToWatchlist` 加总集数异常用例；新增 `changeStatus` 编排用例
- `WatchlistApplicationTest.java` —— `changeStatus` 测试改为 mock `WatchlistDomainService.changeStatus`（不再 mock `watchlistRepo`）；调整"想看→看完非法"用例为成功路径；新增 `ANIME_TOTAL_EPISODES_INVALID` 翻译用例
- `WatchlistControllerTest.java` —— 无需新增（接口未变），但 `changeStatus_whenIllegalTransition_shouldReturnBusinessError` 用例的语义需调整（原用 `WATCHED` 作为非法目标，现 `WANT_TO_WATCH → WATCHED` 合法，改用 `WATCHED → WATCHING` 作为非法转移样例）

## 不在本次范围

- 不新增/不修改 API 端点、请求响应结构
- 不调整持久化层（PO/Mapper/XML/RepoImpl）—— `update` 已更新所有可变字段（status/currentEpisode/updateTime），进度联动写入无需改 Mapper
- 不引入"进度自动转看完"
- 不处理 anime 集数后续变更的边界（追番后 anime 总集数变化不做补偿）
- 前端调整另行处理，不在本 spec
