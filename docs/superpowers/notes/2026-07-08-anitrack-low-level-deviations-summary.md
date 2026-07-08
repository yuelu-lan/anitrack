# anitrack 低级别偏差修复 实施记录

日期：2026-07-08
前置审计：`docs/superpowers/notes/2026-07-07-anitrack-rule-compliance-summary.md`（遗留的低级别偏差一节）

## 目标

修复前次规则合规修复遗留的 10 项低级别偏差中的 1-5 项，使后端代码与 `docs/rules/` 进一步对齐：
异常工厂签名、`UserDomainService` 架构一致性、应用服务日志、Controller 日志、`@Valid` 健壮性。
不涉及功能变更，不新增 API 端点。

## 做了什么

在 `feature/low-level-deviations` 分支上分 4 个 commit 实施（偏差4 为决策记录，无代码改动）：

### 偏差1：`AnitrackAppException` 静态工厂（commit `bb229e7`）

- 构造器改 `private`，新增 `build(String, Object...)` 与 `build(AppExceptionEnum)` 两个静态工厂方法，
  与规则 4.3 签名对齐
- 4 个 Application（21 处）与 4 个 ControllerTest（10 处）的 `new AnitrackAppException(...)`
  统一替换为 `AnitrackAppException.build(...)`

### 偏差2：`UserDomainService` 下沉（commit `2624226`）

- 新建 `domain.user.exception.UsernameAlreadyExistsException`（继承 `AnitrackDomainException`）
- 新建 `domain.user.service.UserDomainService.register(username, passwordHash, nickname)`：
  唯一性校验（抛领域异常）+ `User.register` + `userRepo.save` + 返回
- `UserApplication.register` 改为 encode 后委托领域服务，捕获 `UsernameAlreadyExistsException`
  转 `AnitrackAppException`；`DomainServiceConfig` 加 `userDomainService` Bean
- `UserApplicationTest` 重写 register 两用例（mock `UserDomainService` 替代 mock `userRepo`）
- `login` 的密码校验属基础设施关注点，保留应用层不下沉

### 偏差3：应用服务 INFO 日志（commit `0e78b7b`）

- `UserApplication`/`WatchlistApplication`/`ReviewApplication` 加 `@Slf4j`
  （`AnimeApplication` 已有）
- register/login、addToWatchlist/changeStatus/updateProgress、addReview/updateReview 成功
  各一行 INFO，携带 userId/animeId
- `searchAnime` 补 Bangumi 调用前后 INFO（出站调用属关键编排）
- 查询类操作（get/list/detail）不加日志

### 偏差5：Controller `@Slf4j` + 入口日志（commit `281c446`）

- 4 个 Controller 加 `@Slf4j`
- register/login、watchlist add/change_status/update_progress、review add/update、
  anime search 入口各一行 INFO，携带 userId/animeId
- 纯查询接口（watchlist detail/list、review detail/list_by_anime/my_list、anime detail）不加

### 偏差4：`WatchlistController.list` 的 `@Valid`（决策：不加）

`WatchlistListReq.status` 是可选过滤条件（规则 10.9：空值/null/参数不存在统一视为"不过滤"），
`WatchlistListReq` 无需任何校验注解，加 `@Valid` 实际无效果。决策保持现状不加，标注为
可接受偏离。若未来 `WatchlistListReq` 新增需校验字段，再补 `@Valid`。

## 关键设计决策

**1. `UserDomainService` 的边界**

照 `WatchlistDomainService`/`ReviewDomainService` 模式：`register` 下沉唯一性校验 + 保存。
`login` 的密码校验依赖 `PasswordEncoder`（Spring Security 基础设施），属应用层关注点，
不下沉到领域服务。这与"领域层不依赖 Spring 框架类型"的架构要求一致。

**2. 日志粒度：写操作 + 外部调用**

规则 3.4 只说"关键编排步骤记录 INFO"，未明确粒度。本次定：写操作成功记一行，
外部 HTTP 调用（Bangumi）前后各一行，查询类不加。Controller 入口日志只加写接口
与触发外部调用的 search，避免查询接口产生噪音。

**3. 异常工厂与构造器私有化**

`AnitrackAppException` 构造器改 private 后，所有调用点必须走 `build`。这强制了
异常构造的统一入口，未来加监控/告警 hook 只需改 `build` 两处。

## 最终结果

- `mvn clean test`：5 模块全过，176 个测试 0 失败 0 错误
  - common 3 / domain 54 / infrastructure 24 / application 35 / starter 60
- 无功能变更，无 API 契约调整

## 剩余遗留偏差

本次修了 1-5，剩余 6-10 仍为低级别，按之前 triage：

- 6（请求对象用 `@Getter` 而非 `@Data`）：风格偏离，可不动
- 7（MyBatis XML 占位符未写 `jdbcType`）：MyBatis 可推断，可不动
- 8（INSERT 未显式写 `create_time`/`update_time`）：DB DEFAULT 兜底，可不动
- 9（`anime.cover_url` VARCHAR(512)）：URL 字段合理放宽，可不动
- 10（`SecurityConfig` 全量 `permitAll`）：当前 spec 无 RBAC 场景（YAGNI），可不动

## 执行方式

直接 TDD 改动，未走完整 superpowers 工作流（规模小、方案明确）。每完成一项 commit 一项，
便于 review。`UserApplicationTest` 因 `UserApplication` 构造器变化重写 register 两用例。
