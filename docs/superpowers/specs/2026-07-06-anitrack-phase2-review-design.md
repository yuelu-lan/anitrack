# anitrack Phase 2：评价（Review）设计

## 背景与范围

Phase 1（番剧目录 + 追番核心）已完成并合并到 main。本文档覆盖 Phase 2：`Review` 上下文——用户对已看完番剧的评分与评论，依赖 `Watchlist` 上下文只读数据做跨上下文校验（"只有 `WATCHED` 状态才能评价"）。

## 领域模型

```
Review { id, userId, animeId, score, content, createTime }
```

- `score`：1-10 整数，必填
- `content`：评论内容，允许为空（只打分不写字是常见场景）
- 不含 `updateTime`：与 `WatchlistItem` 只暴露 `updateTime` 不暴露 `createTime` 是同一种"领域模型只暴露业务需要的字段"的思路；数据库仍保留完整审计字段

### 唯一性约束

一个用户对同一番剧只能有一条 `Review`（`userId + animeId` 唯一）。重复创建需走修改接口，不允许覆盖式再创建。

### 跨上下文规则

"只有 `WATCHED` 状态才能评价"：查询 `WatchlistRepo.getByUserAndAnime` 确认状态为 `WATCHED` 后才允许创建 `Review`。`WATCHED` 是 Watchlist 状态机的终态、不可回退（见 Phase 1b 设计），因此评价一旦创建成功，其"资格"永久有效——修改评价（`update()`）不需要重新校验观看状态。

### score 校验规则

`create()`/`update()` 共用同一套校验：`score` 必须非空且在 `[1, 10]` 区间，否则抛 `IllegalReviewScoreException`。校验放在领域层而非 Web 层的 `@Min/@Max`，与 `WatchlistItem.updateProgress()` 的既有模式一致。

### 包结构 `com.anitrack.domain.review`

```
model/Review.java
  - 聚合根，Builder私有（AccessLevel.PRIVATE）
  - static Review create(userId, animeId, score, content)
      # 校验 score，content 不做校验
  - static Review reconstitute(id, userId, animeId, score, content, createTime)
      # 本地读出重建，带id
  - void update(Integer score, String content)
      # 复用 create() 的 score 校验逻辑，全量替换 score/content
repo/ReviewRepo.java              # 接口
  - Review getByUserAndAnime(Long userId, Long animeId)   # 查不到返回 null
  - List<Review> listByAnime(Long animeId, int offset, int limit)   # 按 createTime DESC
  - long countByAnime(Long animeId)
  - List<Review> listByUser(Long userId)   # 按 createTime DESC，不分页
  - Review add(Review review)     # insert，返回带生成id的对象
  - void update(Review review)    # 按 userId+animeId 更新 score/content
service/ReviewDomainService.java
  - Review addReview(Long userId, Long animeId, Integer score, String content)
      # 查 WatchlistRepo.getByUserAndAnime 确认状态为 WATCHED（否则抛 ReviewNotAllowedException）
      # 查 ReviewRepo.getByUserAndAnime 确认未重复创建（已存在抛 ReviewAlreadyExistsException）
      # Review.create() + ReviewRepo.add()
exception/ReviewNotAllowedException.java extends AnitrackDomainException
exception/ReviewAlreadyExistsException.java extends AnitrackDomainException
exception/ReviewNotFoundException.java extends AnitrackDomainException
exception/IllegalReviewScoreException.java extends AnitrackDomainException
```

`updateReview`/`getMyReview`/`listByAnime`/`listMyReviews` 是单聚合操作或纯读查询，不需要跨上下文校验，直接在 `ReviewApplication` 编排，不经过 `ReviewDomainService`（与 Watchlist 侧 `changeStatus`/`getWatchlistItem`/`listMyWatchlist` 同理）。

## 依赖关系说明

`ReviewDomainService` 依赖 `domain.watchlist.repo.WatchlistRepo`（只读查询），不依赖 `domain.watchlist` 的其他内容，符合"跨上下文规则查询另一上下文只读仓储"的既有约定；不新增反向依赖。

## 持久化设计

### 表结构 `t_review`

| 列名 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `BIGINT` | 主键自增 |
| `user_id` | `BIGINT` | 用户ID |
| `anime_id` | `BIGINT` | 番剧ID |
| `score` | `TINYINT` | 1-10 |
| `content` | `TEXT` | 可为空 |
| `create_time` | `DATETIME` | 审计字段 |
| `update_time` | `DATETIME` | 审计字段 |

`UNIQUE KEY uk_user_anime (user_id, anime_id)`：兜底"一用户一记录"不变量，与领域层的重复校验形成双保险。

Flyway 脚本：`anitrack-starter/src/main/resources/db/migration/V4__create_review_table.sql`（`V3` 已用于 `t_watchlist_item`）。

### Mapper / PO / RepoImpl

- `ReviewPO`：字段与表列一一对应，`@Data`，比领域模型多 `updateTime`（不回填进领域对象，沿用 `WatchlistItemPO`/`WatchlistItem` 的既有模式）
- `ReviewMapper`：
  - `selectByUserAndAnime`
  - `selectByAnime`（`ORDER BY create_time DESC LIMIT #{limit} OFFSET #{offset}`）
  - `countByAnime`
  - `selectByUserId`（`ORDER BY create_time DESC`，不分页）
  - `insert`（`useGeneratedKeys`）
  - `updateByUserAndAnime`（按 `user_id+anime_id` 更新 `score`/`content`；`update_time` 交给 `ON UPDATE CURRENT_TIMESTAMP` 自动维护）
- `ReviewConverter`（infra层，MapStruct）：`toPO(Review)` 自动生成；`toDomain(ReviewPO)` 手写 `default` 方法调用 `Review.reconstitute(...)`（聚合根构造器私有，沿用既有模式）
- `ReviewRepoImpl implements ReviewRepo`：
  - `getByUserAndAnime`：查不到返回 `null`
  - `listByAnime(animeId, offset, limit)` / `countByAnime`：直接透传给 Mapper
  - `listByUser(userId)`：直接透传给 Mapper
  - `add(review)`：`insert` 后用生成的自增id重建并返回新对象（对齐 Phase 0 "生成id必须回写"教训）
  - `update(review)`：按 `userId+animeId` 更新 `score`/`content`

### `UserRepo` 新增方法

```
List<User> listByIds(List<Long> ids)   # IN 查询，供 list_by_anime 拼接昵称/头像；ids为空列表时直接返回空列表，不查库
```

`UserMapper` 新增对应的 `selectByIds`（XML `<foreach>` 拼接 `IN`）。

## 应用层设计

### 新增异常码 `AppExceptionEnum`

| 常量 | code | message |
| --- | --- | --- |
| `REVIEW_NOT_ALLOWED` | 40301 | 只有看完的番剧才能评价 |
| `REVIEW_ALREADY_EXISTS` | 40302 | 该番剧已评价，请使用修改接口 |
| `REVIEW_NOT_FOUND` | 40303 | 评价记录不存在 |
| `ILLEGAL_REVIEW_SCORE` | 40304 | 评分必须在1-10之间 |

### BO 模型

- `ReviewBO { id, animeId, score, content, createTime }`：用于 add/update/detail 三个返回单条记录的用例
- `ReviewWithUserViewBO { id, userId, userNickname, userAvatarUrl, score, content, createTime }`：仅用于 `listByAnime`，由 `ReviewAssembler` 拼接 `Review` + `User` 产出
- `ReviewWithAnimeViewBO { id, animeId, animeTitleCn, animeTitleOriginal, animeCoverUrl, score, content, createTime }`：仅用于 `listMyReviews`，由 `ReviewAssembler` 拼接 `Review` + `Anime` 产出
- `ReviewPageBO<T> { List<T> list; long total; }`：分页结果的通用容器（应用层，不下沉到领域层）

三者职责分离，不合并成一个字段可空的类，与 Watchlist 侧 `WatchlistItemBO`/`WatchlistItemViewBO` 的拆分原则一致。

### `ReviewApplication`

```
@Transactional
addReview(Long userId, Long animeId, Integer score, String content): ReviewBO
  1. reviewDomainService.addReview(userId, animeId, score, content)
  2. catch ReviewNotAllowedException -> AnitrackAppException(REVIEW_NOT_ALLOWED)
     catch ReviewAlreadyExistsException -> AnitrackAppException(REVIEW_ALREADY_EXISTS)
     catch IllegalReviewScoreException -> AnitrackAppException(ILLEGAL_REVIEW_SCORE)
  3. 转换为 ReviewBO 返回

@Transactional
updateReview(Long userId, Long animeId, Integer score, String content): ReviewBO
  1. reviewRepo.getByUserAndAnime(userId, animeId)，查不到抛 AnitrackAppException(REVIEW_NOT_FOUND)
  2. review.update(score, content)，catch IllegalReviewScoreException -> AnitrackAppException(ILLEGAL_REVIEW_SCORE)
  3. reviewRepo.update(review)
  4. 转换为 ReviewBO 返回

getMyReview(Long userId, Long animeId): ReviewBO
  1. reviewRepo.getByUserAndAnime(userId, animeId)，查不到直接抛 AnitrackAppException(REVIEW_NOT_FOUND)
  2. 转换为 ReviewBO 返回（只读，不加 @Transactional）

listByAnime(Long animeId, int page, int pageSize): ReviewPageBO<ReviewWithUserViewBO>
  1. offset = (page - 1) * pageSize
  2. reviewRepo.listByAnime(animeId, offset, pageSize) + reviewRepo.countByAnime(animeId)
  3. 提取 userId 列表 -> userRepo.listByIds(userIds)
  4. reviewAssembler.assembleWithUser(reviews, users)
  （只读，不加 @Transactional）

listMyReviews(Long userId): List<ReviewWithAnimeViewBO>
  1. reviewRepo.listByUser(userId)
  2. 提取 animeId 列表 -> animeRepo.listByIds(animeIds)
  3. reviewAssembler.assembleWithAnime(reviews, animes)
  （只读，不加 @Transactional）
```

### `ReviewAssembler`

纯函数式组装，仿照 `WatchlistAssembler`：

- `assembleWithUser(List<Review>, List<User>)`：按 `userId` 用 `Map<Long, User>` 匹配，匹配不到跳过并记 `WARN` 日志
- `assembleWithAnime(List<Review>, List<Anime>)`：按 `animeId` 用 `Map<Long, Anime>` 匹配，匹配不到跳过并记 `WARN` 日志

## Web层设计

### 路由

全部 POST，全部经过现有 `JwtAuthInterceptor` 鉴权，`userId` 一律从 `UserContextHolder.getUserId()` 获取，不放进请求体。

| 方法 | 路径 | 请求体 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/review/add` | `ReviewAddReq{ animeId: @NotNull, score: @NotNull, content: 可选 }` | 返回 `ReviewResponse` |
| POST | `/api/review/update` | `ReviewUpdateReq{ animeId: @NotNull, score: @NotNull, content: 可选 }` | 返回 `ReviewResponse` |
| POST | `/api/review/detail` | `ReviewDetailReq{ animeId: @NotNull }` | 返回 `ReviewResponse` |
| POST | `/api/review/list_by_anime` | `ReviewListByAnimeReq{ animeId: @NotNull, page: 可选(默认1), pageSize: 可选(默认10) }` | 返回 `PageResponse<ReviewWithUserResponse>` |
| POST | `/api/review/my_list` | 无请求体 | 返回 `List<ReviewWithAnimeResponse>` |

Controller 层只做"是否提供必填字段"的校验（`@NotNull` + `@Valid`），`score` 范围校验交给领域层。`page`/`pageSize` 为 `null` 时 Controller 兜底默认值 `1`/`10`。

### `PageResponse<T>`（`starter.response`，新增通用分页响应壳）

```java
PageResponse<T> { List<T> list; long total; }
```

本次唯一新增的通用抽象——Phase 4 社区帖子/评论列表大概率也要分页，两字段的壳复用成本低于重复定义。

### 响应对象

```java
ReviewResponse { id, animeId, score, content, createTime }
ReviewWithUserResponse { id, userId, userNickname, userAvatarUrl, score, content, createTime }
ReviewWithAnimeResponse { id, animeId, animeTitleCn, animeTitleOriginal, animeCoverUrl, score, content, createTime }
```

`HttpConverter` 新增对应的 BO→Response 转换方法，沿用现有 `watchlistItemBO2Response` 的写法。

## 异常处理链路

```
Review.create() / Review.update() score 校验不通过
  → 抛 IllegalReviewScoreException（domain.review.exception）
  → ReviewApplication 捕获，转译为 AnitrackAppException(ILLEGAL_REVIEW_SCORE)

ReviewDomainService.addReview() 未看完（非WATCHED）/ 重复评价
  → 抛 ReviewNotAllowedException / ReviewAlreadyExistsException
  → ReviewApplication 捕获，转译为 AnitrackAppException(REVIEW_NOT_ALLOWED / REVIEW_ALREADY_EXISTS)
```

`updateReview`/`getMyReview` 查不到记录属于正常业务分支（不是领域规则冲突），由 `ReviewApplication` 直接判断并抛 `AnitrackAppException(REVIEW_NOT_FOUND)`，不经过领域异常，与 Watchlist 侧 `getWatchlistItem` 处理 `WATCHLIST_ITEM_NOT_FOUND` 的方式一致。`GlobalExceptionHandler` 不需要改动，`AnitrackDomainException`/`AnitrackAppException` 的既有兜底分支已覆盖所有新异常类型。

## 测试策略

- **Domain**：
  - `ReviewTest`：`create()`/`update()` 的 score 边界校验（`null`、0、1、10、11 等）
  - `ReviewDomainServiceTest`（Mockito）：mock `WatchlistRepo`+`ReviewRepo`，覆盖 `addReview` 的正常路径 + 未看完 + 重复评价两种失败路径
- **Infrastructure**：`ReviewRepoImplTest`，mock Mapper+Converter 验证 `add`/`update`/`listByAnime`/`listByUser` 的行为
- **Application**：`ReviewApplicationTest`（JUnit5 + Mockito + AssertJ），mock `ReviewDomainService`/`ReviewRepo`/`UserRepo`/`AnimeRepo`/`ReviewAssembler`，覆盖全部异常转译路径 + 分页组装逻辑
- **Web**：`ReviewControllerTest`（`@WebMvcTest` + `MockMvc` + `@MockBean`），覆盖 5 个接口的正常路径、未带 token 的 401、必填字段缺失的参数校验失败场景

## 明确不做的事（YAGNI，避免范围蔓延）

- 不在 `Anime` 上做平均分/评价数聚合展示（属于 Phase 3 "榜单读模型"范畴，需要时再引入）
- 不实现评价的物理删除（设计文档明确"支持修改，不支持删除"）
- `listMyReviews` 不分页（用户自己的评价数量有限，与 `listMyWatchlist` 现状一致）
- 不给 `content` 加长度上限校验（`TEXT` 字段，当前无此类需求）
- 不引入评价相关的领域事件（当前没有下游消费场景，需要时再加，与 `WatchStatusChangedEvent` 当前也无监听器同理）

## 后续（不在本 spec 范围）

Phase 3（可选）若做"看完提醒评分"，是监听 `WatchStatusChangedEvent`（`newStatus == WATCHED`）触发提醒，不依赖本 spec 的任何内容；若做榜单读模型，会读取本 spec 的 `t_review` 表做聚合统计，届时另开 spec。
