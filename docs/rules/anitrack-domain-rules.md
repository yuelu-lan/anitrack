# anitrack 追番领域知识

## 基本概念

anitrack 是一个追番管理系统，围绕用户对番剧的追看行为组织领域模型。核心限界上下文划分为五个：

| 上下文 | 职责边界 | 所属Phase |
| --- | --- | --- |
| Anime（番剧目录） | 番剧基础信息的本地只读缓存，数据来源于 Bangumi 外部 API | Phase 1 |
| Watchlist（追番） | 用户追番状态与进度管理，状态机核心 | Phase 1 |
| Review（评价） | 用户对已看完番剧的评分与评论 | Phase 2 |
| Community（社区互动） | 帖子、评论、点赞、关注、举报审核 | Phase 4（可选） |
| User（用户） | 账号、认证、基础身份信息 | Phase 0 |

## Anime 上下文：番剧目录

```
Anime { id, bangumiId(外部ID), title, coverUrl, totalEpisodes, airDate, summary }
```

- 数据来自 Bangumi API，通过防腐层（ACL）转换为领域模型后本地只读缓存
- 业务代码**不允许**直接修改这些字段，只能通过 ACL 同步更新——"真相"在外部系统
- `AnimeRepo` 负责本地缓存的查询与落库
- `AnimeApplication.searchAnime()` 优先查本地缓存，未命中则调用 `BangumiGateway` 拉取并落库

## Watchlist 上下文：追番状态机

```
WatchlistItem { id, userId, animeId, status, currentEpisode, updateTime }
enum WatchStatus { WANT_TO_WATCH, WATCHING, WATCHED, DROPPED }
```

### 唯一性约束

一个用户对同一番剧只能有一条 `WatchlistItem`（`userId + animeId` 唯一）。评分统一由 Review 上下文承载，不在 `WatchlistItem` 中冗余存储。

### 合法状态转移表

合法状态转移表（`Map<WatchStatus, Set<WatchStatus>>`）内置在聚合根 `WatchlistItem` 内部：

| 当前状态 | 可转移至 |
| --- | --- |
| WANT_TO_WATCH | WATCHING、DROPPED |
| WATCHING | WATCHED、DROPPED |
| WATCHED | DROPPED |
| DROPPED | WANT_TO_WATCH、WATCHING |

例如 `WATCHED` 不能直接回退到 `WANT_TO_WATCH`。`changeStatus(newStatus)` 方法查转移表校验，不合法则抛 `IllegalWatchStatusTransitionException`。

### 跨上下文校验规则

- 更新观看进度：仅 `WATCHING` 状态允许调用，且不能超过对应 `Anime.totalEpisodes`。这是跨上下文校验，由 `WatchlistDomainService.updateProgress(userId, animeId, episode)` 查询 Anime 上下文的仓储取到 `totalEpisodes` 后，委托给聚合根方法 `WatchlistItem.updateProgress(episode, totalEpisodes)` 执行校验与赋值，不允许 `WatchlistItem` 聚合根直接依赖 `AnimeRepo`
- 领域事件：`WatchStatusChangedEvent(userId, animeId, oldStatus, newStatus)`，聚合内只产生事件对象，由 `WatchlistApplication` 通过 Spring `ApplicationEventPublisher` 发布，监听器使用 `@TransactionalEventListener` 确保在事务提交后才执行，避免 domain 层直接依赖 Spring 类型

## Review 上下文：评价

```
Review { id, userId, animeId, score(1-10), content, createTime }
```

### 跨上下文规则

"只有 `WATCHED` 状态才能评价"是跨上下文规则，放在 `ReviewDomainService` 里：查询 `WatchlistRepo` 确认状态后再委托创建，不合法抛 `ReviewNotAllowedException`。

一个用户对同一番剧只能有一条 Review。

## Community 上下文：社区互动（Phase 4 可选）

```
Post { id, userId, animeId(可选), title, content, status, commentCount }
enum PostStatus { DRAFT, PUBLISHED, UNDER_REVIEW, REMOVED }
```

- `Comment` 是独立聚合根（持有 `postId`），支持 `parentCommentId` 楼中楼；评论数量无上限，塞进 `Post` 聚合会导致聚合过大，因此不作为内部实体
- `Post.commentCount` 是跨聚合的最终一致计数，由 `CommentCreatedEvent` / `CommentRemovedEvent` 异步维护，不要求与实际评论数强一致
- `Like` / `Follow` 用简单关联表建模（`userId+postId` / `userId+followedUserId`），不单独做聚合根
- `Post.publish()` / `Post.remove()` 沿用枚举+方法的状态转换模式；被举报后转入 `UNDER_REVIEW`，人工审核后转 `PUBLISHED` 或 `REMOVED`
- 领域事件：`PostPublishedEvent`、`PostReportedEvent`（举报后走人工后台审核+状态字段，不引入工作流引擎）

## User 上下文：用户

```
User { id, username, passwordHash, nickname, avatarUrl, role }
```

密码用 BCrypt 加密；JWT 签发/校验在 `infrastructure.auth`；`UserContextHolder`（ThreadLocal）作为"当前登录用户"的统一获取入口。

## ACL 防腐层隔离原则

`BangumiGateway` 是 anitrack 唯一的外部系统防腐层：

- 接口定义在 `domain.anime.gateway`，`infrastructure` 实现该接口
- 调用 Bangumi API → 得到外部 DTO → 转换为 `Anime` 领域模型
- 隔离外部数据结构变化：Bangumi API 字段调整时，只需修改 `infrastructure` 层的转换逻辑，`domain` 层的 `Anime` 模型和依赖它的业务代码不受影响
- 领域层与应用层任何时候都不允许直接引用 Bangumi 外部 DTO 类型
