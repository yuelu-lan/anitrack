# anitrack 设计文档

## 背景与目标

练习用 Spring Boot 3 + Java 17 落地 DDD 架构，同时作为求职作品集。核心业务方向为追番管理，社区互动作为 Phase 4 的可选扩展（详见分期路线图）。

架构参考一套成熟的 DDD 编码规范：分层结构、命名规范、Web层与持久化层的实践直接沿用，其中的专有技术组件替换为开源/标准方案，选型依据见下节。

## 技术选型说明

### 核心框架

| 分类 | 选型 | 说明 |
| --- | --- | --- |
| 语言/框架 | Java 17 + Spring Boot 3.x | LTS 版本，Spring Boot 3 强制要求 |
| Web | Spring MVC（`spring-boot-starter-web`） | 同步阻塞已足够，无需 WebFlux |
| 参数校验 | Hibernate Validator（`spring-boot-starter-validation`） | Jakarta Bean Validation 标准实现 |
| 构建工具 | Maven | 多 module 结构标配 |

### 持久化

| 分类 | 选型 | 说明 |
| --- | --- | --- |
| 数据库 | MySQL 8.x | 主流开源关系型数据库 |
| ORM | MyBatis（`mybatis-spring-boot-starter`） | 手写 XML Mapper，贴合"仓储手动转换"这个 DDD 练习点 |
| 连接池 | HikariCP | Spring Boot 默认连接池 |
| 数据库版本管理 | Flyway | SQL 脚本管理表结构变更历史 |

### 安全与认证

| 分类 | 选型 | 说明 |
| --- | --- | --- |
| 认证框架 | Spring Security | 生态标准方案 |
| JWT库 | jjwt（`io.jsonwebtoken`） | 主流 Java JWT 库，API 简洁 |
| 密码加密 | `BCryptPasswordEncoder`（Spring Security内置） | 加盐哈希存储密码 |

### 对象映射与工具类

| 分类 | 选型 | 说明 |
| --- | --- | --- |
| Bean拷贝 | MapStruct | 编译期生成映射代码，比 `BeanUtils` 更类型安全 |
| 简化样板代码 | Lombok | `@Data/@Builder/@Slf4j` 减少手写样板代码 |

### 外部集成

| 分类 | 选型 | 说明 |
| --- | --- | --- |
| HTTP客户端（调用Bangumi API） | Spring `RestClient`（Spring Boot 3.2+内置） | 官方新一代同步 HTTP 客户端，比 OpenFeign 更轻量 |
| 分布式ID生成 | 数据库自增主键 | 单体单库场景无需分布式方案 |

### 测试

| 分类 | 选型 | 说明 |
| --- | --- | --- |
| 单元测试 | JUnit5 + Mockito | Java 生态主流组合 |
| 断言库 | AssertJ | 链式断言，可读性更好 |
| Web层集成测试 | `@WebMvcTest` + `MockMvc` + `@MockBean` | Spring Boot Test 标准工具 |

### 日志

| 分类 | 选型 | 说明 |
| --- | --- | --- |
| 日志框架 | SLF4J + Logback | Spring Boot 默认组合 |
| 请求链路追踪 | MDC + traceId拦截器 | 轻量方案，无需引入分布式链路追踪组件 |

### 可选增强

| 分类 | 选型 | 说明 |
| --- | --- | --- |
| API文档 | springdoc-openapi | 自动生成 Swagger UI，便于自测 |
| 对象存储 | MinIO | Phase 3 图片上传功能需要时再引入 |

### 评估后判断当前不需要的能力

个人单体项目规模下没有对应场景，硬套只会造成过度设计，暂不引入：

| 能力 | 处理方式 |
| --- | --- |
| 分布式调度 | 单实例场景用 Spring `@Scheduled` 即可覆盖 |
| 系统通知 | 暂不引入；后续需要通知能力时用站内信表 |
| 组织架构对接 | 不需要，简单 RBAC（user + role）已足够 |
| 多级人工审批 | 不适用；状态机场景用枚举+方法+转移表实现 |

## 总体架构

Maven 多模块，5 个子模块，编译期强制依赖方向（不含 `api` 模块——本项目不考虑其他服务的 RPC 调用）：

```
anitrack/
├── anitrack-starter/                     # Web启动层
│   ├── ApplicationLoader.java
│   └── com.anitrack.starter/
│       ├── controller/
│       ├── converter/
│       ├── request/
│       ├── response/
│       ├── filter/
│       ├── interceptor/
│       ├── aop/
│       └── config/
├── anitrack-application/                 # 应用服务层（编排用例）
│   └── com.anitrack.application/
│       ├── service/
│       ├── model/
│       ├── converter/
│       ├── assembler/
│       ├── config/
│       └── exception/
├── anitrack-domain/                      # 领域核心层
│   └── com.anitrack.domain/
│       ├── anime/                        # 番剧目录上下文（含 gateway/，定义 BangumiGateway 接口）
│       ├── watchlist/                    # 追番上下文（状态机核心）
│       ├── review/                       # 评价上下文
│       ├── community/                    # 社区互动上下文
│       ├── user/                         # 用户上下文
│       └── common/                       # 公共领域组件（如 AnitrackDomainException 基类）
├── anitrack-infrastructure/              # 基础设施层
│   └── com.anitrack.infra/
│       ├── dal/
│       ├── gateway/                      # 含 bangumi 防腐层
│       ├── repo/
│       ├── converter/
│       ├── auth/
│       ├── config/
│       └── constants/
└── anitrack-common/                      # 公共组件层
    └── com.anitrack.common/
        ├── dto/
        ├── enums/
        ├── utils/
        └── constants/
```

除 `common/` 外，`anime`/`watchlist`/`review`/`community`/`user` 每个上下文子包内均包含：`model/`、`service/`、`repo/`、`enums/`、`exception/`。

**依赖方向**：`starter → application → domain`；`infrastructure → domain`。仓储、网关接口（`XxxRepo`、`BangumiGateway`）统一定义在 `domain` 层，`infrastructure` 实现这些接口，`application` 只依赖 `domain` 定义的接口即可完成编排——因此 `application` 不直接依赖 `infrastructure`，是依赖倒置的自然结果，而非额外规则。`starter` 作为可运行的启动模块，运行时需要装配 `infrastructure` 的实现类，因此也依赖 `infrastructure`。`domain` 不依赖任何业务模块；`common` 被所有模块依赖。

## 领域模型设计

### Anime 上下文（番剧目录，Phase 1）

数据来自 Bangumi API，通过防腐层（ACL）转换为领域模型后本地只读缓存：

```
Anime { id, bangumiId(外部ID), title, coverUrl, totalEpisodes, airDate, summary }
```

业务代码不允许直接修改这些字段，只能通过 ACL 同步更新——"真相"在外部系统。`AnimeRepo` 负责本地缓存的查询与落库。

### Watchlist 上下文（追番，Phase 1 核心）

```
WatchlistItem { id, userId, animeId, status, currentEpisode, updateTime }
enum WatchStatus { WANT_TO_WATCH, WATCHING, WATCHED, DROPPED }
```

- 一个用户对同一番剧只能有一条 `WatchlistItem`（`userId + animeId` 唯一）；评分统一由 Review 承载，不在 `WatchlistItem` 中冗余存储
- 合法状态转移表（`Map<WatchStatus, Set<WatchStatus>>`）内置在聚合根内：`WANT_TO_WATCH → WATCHING`、`WATCHING → WATCHED/DROPPED`、`DROPPED → WATCHING`（重新观看保留弃番时的 `currentEpisode` 进度，不清零）；`WATCHED` 为终态，不可回退
- `changeStatus(newStatus)`：查转移表校验，不合法抛 `IllegalWatchStatusTransitionException`
- `updateProgress(episode)`：仅 `WATCHING` 状态允许调用，且不能超过对应 `Anime.totalEpisodes`（跨上下文校验，放在 `WatchlistDomainService` 里查询 Anime 上下文的仓储后委托聚合根执行）
- 领域事件：`WatchStatusChangedEvent(userId, animeId, oldStatus, newStatus)`，聚合内只产生事件对象，由 `WatchlistApplication` 在事务提交后通过 Spring `ApplicationEventPublisher` 发布，避免 domain 层直接依赖 Spring 类型

### Review 上下文（评价，Phase 2）

```
Review { id, userId, animeId, score(1-10), content, createTime }
```

- "只有 `WATCHED` 状态才能评价"是跨上下文规则，放在 `ReviewDomainService` 里：查询 `WatchlistRepo` 确认状态后再委托创建，不合法抛 `ReviewNotAllowedException`
- 一个用户对同一番剧只能有一条 Review
- 支持修改（`updateReview()`），不支持删除

### Community 上下文（社区互动，Phase 4）

```
Post { id, userId, animeId(可选), title, content, status, commentCount }
enum PostStatus { DRAFT, PUBLISHED, UNDER_REVIEW, REMOVED }
```

- `Comment` 是独立聚合根（持有 `postId`），支持 `parentCommentId` 楼中楼；评论数量无上限，塞进 `Post` 聚合会导致聚合过大，因此不作为内部实体
- `Post.commentCount` 是跨聚合的最终一致计数，由 `CommentCreatedEvent` / `CommentRemovedEvent` 异步维护，不要求与实际评论数强一致
- `Like` / `Follow` 用简单关联表建模（`userId+postId` / `userId+followedUserId`），不单独做聚合根
- `Post.publish()` / `Post.remove()` 沿用枚举+方法的状态转换模式；被举报后转入 `UNDER_REVIEW`，人工审核后转 `PUBLISHED` 或 `REMOVED`
- 领域事件：`PostPublishedEvent`、`PostReportedEvent`（举报后走人工后台审核+状态字段，不引入工作流引擎）

### User 上下文（用户，Phase 0）

```
User { id, username, passwordHash, nickname, avatarUrl, role }
```

密码用 BCrypt 加密；JWT 签发/校验在 `infrastructure.auth`；`UserContextHolder`（ThreadLocal）作为"当前登录用户"的统一获取入口。

## 应用层设计

- 应用服务（`XxxApplication`）只做编排：组装领域对象、调用领域服务/仓储、控制事务边界（`@Transactional`），不写业务规则
- `WatchlistApplication`：`addToWatchlist()` / `changeStatus()` / `updateProgress()`
- `AnimeApplication`：`searchAnime()` 优先查本地缓存，未命中则调用 `BangumiGateway` 拉取并落库
- `XxxAssembler` 组装跨聚合的复杂返回结构（例如"我的追番列表"需要拼接 `WatchlistItem` + `Anime` 标题封面）
- 统一应用异常 `AnitrackAppException` + `AppExceptionEnum` 错误码

## Web 层设计

- `ResponseResult { status, data }` 统一响应体，`success()/fail()` 静态工厂方法
- Controller 路由风格：`/api/watchlist/add`、`/api/watchlist/change_status`（POST为主）
- Jakarta Bean Validation 校验（`@NotNull/@NotBlank` 等）+ `@Valid`
- `@RestControllerAdvice` 全局异常处理，按"应用层异常/领域层异常/参数校验异常/系统异常"分类，统一返回 HTTP 200 + 业务状态码
- JWT鉴权：`HandlerInterceptor` 拦截，解析 `Authorization` header，写入 `UserContextHolder`

## 持久化层设计

- MyBatis（原生 XML Mapper，不用 MyBatis-Plus）+ MySQL + HikariCP
- 每个上下文一张主表：`t_anime`、`t_watchlist_item`、`t_review`、`t_post`、`t_comment`、`t_user`，命名风格：下划线、`表名_id` 主键、`create_time/update_time` 审计字段；`t_watchlist_item`、`t_review` 对 `(user_id, anime_id)` 建唯一索引，兜底"一用户一记录"的领域不变量
- 仓储实现（`XxxRepoImpl implements XxxRepo`）持有 Mapper，负责 PO ↔ 领域模型转换（MapStruct）
- `BangumiGateway`（ACL核心）：接口定义在 `domain.anime.gateway`，`infrastructure` 实现该接口，调用 Bangumi API → 外部 DTO → 转换为 `Anime` 领域模型，隔离外部数据结构变化

## 测试策略

- 领域层（状态机转移、跨上下文规则等核心逻辑）用 JUnit5 + Mockito 做单元测试，重点覆盖非法状态转移、跨上下文校验等边界场景
- Web层用 `@WebMvcTest` + `MockMvc` + `@MockBean` 做集成测试，覆盖参数校验与全局异常处理链路

## 分期路线图

| Phase | 内容 | 练习重点 |
| --- | --- | --- |
| 0 | 脚手架（5模块搭建）+ 用户注册登录（JWT） | 模块依赖方向、Spring Security |
| 1 | 番剧目录（Bangumi ACL）+ 追番核心（状态机/进度） | 聚合根、ACL防腐层、跨上下文校验、领域异常 |
| 2 | 评价（评分/评论） | 唯一性校验、跨上下文规则复用 |
| 3（可选，进阶） | 事件监听扩展（看完提醒评分）、榜单读模型（简单CQRS）、MinIO图片上传 | 事件监听器扩展、读写分离 |
| 4（可选） | 社区互动（帖子/评论/点赞/关注/举报审核） | 聚合边界权衡（Comment为何是独立聚合根）、简单状态审核流 |
