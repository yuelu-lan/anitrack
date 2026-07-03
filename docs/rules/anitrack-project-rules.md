# anitrack 项目规则（总纲）

## AI 角色定位

你是 anitrack 项目的资深 Java 后端工程师，精通 Spring Boot 3、DDD（领域驱动设计）与六边形架构思想，编写代码时严格遵循本规则集。

## 设计原则

- **SOLID**：单一职责、开闭原则、里氏替换、接口隔离、依赖倒置
- **DRY**：不重复代码逻辑
- **KISS**：保持实现简单直接
- **YAGNI**：不做当前不需要的设计（参见 anitrack 设计文档"评估后判断当前不需要的能力"一节）

## 技术栈

| 分类 | 选型 |
| --- | --- |
| 语言/框架 | Java 17 + Spring Boot 3.x |
| Web | Spring MVC（`spring-boot-starter-web`） |
| 参数校验 | Hibernate Validator（`spring-boot-starter-validation`） |
| 构建工具 | Maven（多模块） |
| 架构风格 | DDD + 依赖倒置（六边形架构思想） |
| 数据库 | MySQL 8.x |
| ORM | MyBatis 原生 XML Mapper（不使用 MyBatis-Plus） |
| 连接池 | HikariCP |
| 数据库版本管理 | Flyway |
| 认证框架 | Spring Security |
| JWT库 | jjwt（`io.jsonwebtoken`） |
| 密码加密 | `BCryptPasswordEncoder` |
| Bean拷贝 | MapStruct |
| 简化样板代码 | Lombok |
| 外部HTTP调用 | Spring `RestClient`（调用 Bangumi API） |
| 定时任务 | Spring `@Scheduled` |
| 分布式ID | 数据库自增主键 |
| 单元测试 | JUnit5 + Mockito + AssertJ |
| Web层集成测试 | `@WebMvcTest` + MockMvc + `@MockBean` |
| 日志 | SLF4J + Logback，MDC + traceId拦截器 |
| API文档 | springdoc-openapi（可选） |

## 模块划分

Maven 多模块，5 个子模块，编译期强制依赖方向（不含 `api` 模块）：

```
anitrack/
├── anitrack-starter/          # Web启动层：controller/converter/request/response/filter/interceptor/aop/config
├── anitrack-application/      # 应用服务层：service/model/converter/assembler/config/exception
├── anitrack-domain/           # 领域核心层：anime/watchlist/review/community/user/common
├── anitrack-infrastructure/   # 基础设施层：dal/gateway/repo/converter/auth/config/constants
└── anitrack-common/           # 公共组件层：dto/enums/utils/constants
```

除 `common/` 外，`anime`/`watchlist`/`review`/`community`/`user` 每个上下文子包内均包含：`model/`、`service/`、`repo/`、`enums/`、`exception/`。

## 依赖关系规范

- **依赖方向**：`starter → application → domain`；`infrastructure → domain`
- 仓储、网关接口（`XxxRepo`、`BangumiGateway`）统一定义在 `domain` 层，`infrastructure` 实现这些接口
- `application` 只依赖 `domain` 定义的接口完成编排，不直接依赖 `infrastructure`（依赖倒置的自然结果）
- `starter` 作为可运行的启动模块，运行时需要装配 `infrastructure` 的实现类，因此依赖 `infrastructure`
- `domain` 不依赖任何业务模块；`common` 被所有模块依赖

## 包结构规范

- `com.anitrack.starter`：`controller/`、`converter/`、`request/`、`response/`、`filter/`、`interceptor/`、`aop/`、`config/`
- `com.anitrack.application`：`service/`、`model/`、`converter/`、`assembler/`、`config/`、`exception/`
- `com.anitrack.domain.{anime|watchlist|review|community|user}`：`model/`、`service/`、`repo/`、`enums/`、`exception/`；`anime` 上下文额外包含 `gateway/`（定义 `BangumiGateway` 接口）
- `com.anitrack.domain.common`：公共领域组件，如 `AnitrackDomainException` 基类
- `com.anitrack.infra`：`dal/`、`gateway/`（含 bangumi 防腐层）、`repo/`、`converter/`、`auth/`、`config/`、`constants/`
- `com.anitrack.common`：`dto/`、`enums/`、`utils/`、`constants/`

## 命名规范

### 类命名

| 类型 | 命名规则 | 示例 |
| --- | --- | --- |
| Controller | `Xxx` + `Controller` | `WatchlistController` |
| 应用服务 | `Xxx` + `Application` | `WatchlistApplication` |
| 领域服务 | `Xxx` + `DomainService` | `WatchlistDomainService` |
| 仓储接口（domain层） | `Xxx` + `Repo` | `WatchlistRepo` |
| 仓储实现（infra层） | `Xxx` + `RepoImpl` | `WatchlistRepoImpl` |
| 网关接口（domain层） | `Xxx` + `Gateway` | `BangumiGateway` |
| 持久化对象 | `Xxx` + `PO` | `WatchlistItemPO` |
| Mapper接口 | `Xxx` + `Mapper` | `WatchlistItemMapper` |
| 应用层模型 | `Xxx` + `BO` | `WatchlistBO` |
| 请求对象 | `Xxx` + `Req` | `WatchlistAddReq` |
| 响应对象 | `Xxx` + `Response` | `WatchlistResponse` |
| 组装器 | `Xxx` + `Assembler` | `WatchlistAssembler` |
| 转换器 | `Xxx` + `Converter` | `HttpConverter` |
| 领域异常 | `Xxx` + `Exception` | `IllegalWatchStatusTransitionException` |
| 应用异常 | `AnitrackAppException` | 统一应用异常类 |

### 方法命名

| 场景 | 命名规则 | 示例 |
| --- | --- | --- |
| 查询单个 | `getXxx` | `getWatchlistItem` |
| 查询列表 | `listXxx` | `listWatchlistItems` |
| 新增 | `addXxx` / `createXxx` | `addToWatchlist` |
| 修改 | `updateXxx` / `changeXxx` | `changeStatus` |
| 删除 | `removeXxx` / `deleteXxx` | `removeFromWatchlist` |
| 判断 | `isXxx` / `hasXxx` | `isStatusTransitionAllowed` |
| 转换 | `xxx2Yyy` | `watchlistReq2BO` |

## 测试规范

单测框架使用 JUnit5 + Mockito + AssertJ，详见 `anitrack-unittest-rules.md`；Web层集成测试使用 `@WebMvcTest`，详见 `anitrack-web-test-rules.md`。

## 日志与监控

- 使用 SLF4J + Logback，禁止直接使用 `System.out.println`
- 关键业务操作（状态变更、跨上下文调用）记录 INFO 级别日志
- 异常场景记录 ERROR 级别日志并携带上下文信息（如 userId、animeId）
- 使用 MDC + traceId 拦截器实现请求链路追踪

## 数据访问与ORM

ORM 框架使用 MyBatis 原生 XML Mapper，不引入 MyBatis-Plus，不允许联表查询，不允许使用外键，详见 `anitrack-persist-rules.md`。

## 架构要求

- 领域层不依赖 Spring 框架类型（如 `ApplicationEventPublisher`），领域事件的发布由应用层负责
- 跨上下文规则放在对应的 `XxxDomainService` 中实现（查询另一上下文的只读仓储 + 委托聚合根执行）
- Bangumi 等外部系统的数据通过防腐层（ACL）转换为领域模型，业务代码不允许绕过 ACL 直接操作外部数据结构
