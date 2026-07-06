# anitrack Phase 1b 实施记录

日期：2026-07-06
设计文档：`docs/superpowers/specs/2026-07-06-anitrack-phase1b-watchlist-design.md`
计划文档：`docs/superpowers/plans/2026-07-06-anitrack-phase1b-watchlist.md`

## 目标

实现追番核心（`Watchlist`）上下文：用户对番剧的追看状态机（`WANT_TO_WATCH/WATCHING/WATCHED/DROPPED`）、
观看进度管理（含跨上下文的 `Anime.totalEpisodes` 校验），暴露加入追番/变更状态/更新进度/查询详情/查询列表
5 个接口。Phase 1 的另一半——番剧目录（`Anime` 上下文）——已在 Phase 1a 完成，本次直接复用其
`AnimeRepo`/`Anime` 领域模型。

## 做了什么

按 7 个 Task 逐步实施（Subagent-Driven Development：每个 Task 一个全新子代理实现 + 一个独立子代理审查，
TDD，每个 Task 独立 commit，全程直接在 main 分支上进行，未创建 worktree）：

1. `WatchlistItem` 聚合根（内置状态转移表 + 进度校验）+ `WatchStatus` 枚举 + `WatchStatusChangedEvent` +
   状态机/进度领域异常
2. `WatchlistRepo` 仓储接口 + `AnimeNotFoundException`（新增到已有的 Anime 上下文）+
   `WatchlistDomainService`（跨上下文编排：校验番剧存在性、去重、委托聚合根执行进度更新）
3. `t_watchlist_item` 表 Flyway 脚本 + `WatchlistItemPO`/`WatchlistItemMapper` + `AnimeMapper` 补充
   `selectByIds`
4. `WatchlistItemConverter`（MapStruct）+ `WatchlistRepoImpl` + `AnimeRepo`/`AnimeRepoImpl` 补充
   `listByIds` 批量查询
5. `AppExceptionEnum` 扩展（402xx 段）+ `WatchlistItemBO`/`WatchlistItemViewBO` + `WatchlistAssembler`
   （拼接 `WatchlistItem` + `Anime` 展示数据）+ `DomainServiceConfig`（为无 Spring 注解的
   `WatchlistDomainService` 提供 Bean 装配）+ `WatchlistApplication`（编排 5 个用例 + 异常转译 +
   事件发布）
6. `WatchlistController`（5 个 JWT 鉴权接口）+ Web 层集成测试
7. 全量编译测试 + 真实本地 MySQL 端到端联调

全部完成后做了一次全分支最终 review（opus 模型），结论 Ready to merge，无 Critical/Important 问题，
直接保留在 main 分支上（未 push）。

## 遇到的问题及解决

**1. Task 5 实现子代理中途 stall，控制者接手时发现真实 Mockito 重载 bug**

Task 5 的实现子代理在写完全部 6 个文件（`WatchlistApplication` 及其依赖）后卡死 600 秒无进展，
被判定 failed。核实工作区发现代码文件其实已经写完，只是没跑最终测试、没提交。控制者接手运行测试时
发现真实缺陷：`changeStatus_whenItemExists_shouldUpdateAndPublishEvent` 测试失败，报错
"Wanted but not invoked" 但同时显示确实有一次匹配的调用。

根因：`ApplicationEventPublisher` 接口同时存在 `publishEvent(ApplicationEvent)` 和
`publishEvent(Object)` 两个重载方法。测试里裸写的 `verify(...).publishEvent(any())`（`any()`
无类型实参）在存在多个重载候选时，Java 编译器按"最具体类型优先"规则将其静态绑定到
`publishEvent(ApplicationEvent)`；而生产代码里 `WatchStatusChangedEvent`（普通 record，不继承
Spring 的 `ApplicationEvent`）实际走的是 `publishEvent(Object)` 重载。两次调用命中了接口上不同的
方法句柄，Mockito 按方法签名比对，因此判定"从未调用"。

修复：把 3 处 `publishEvent(any())` 改为显式类型 `publishEvent(any(WatchStatusChangedEvent.class))`，
消除重载歧义。修复后 anitrack-application 模块 22/22 测试全过，任务审查子代理独立复测确认根因分析
成立、修复点无遗漏。这是一个此前项目里未出现过的新坑，已记入进度台账供后续类似场景参考。

**2. Task 6（Controller 层）重点提防了 Phase 1a 出现过的鉴权白名单误加问题**

Phase 1a 的同类任务（`AnimeController` + `@WebMvcTest`）曾出现实现子代理为了让测试编译通过，把新接口
误加进 `WebMvcConfig` 的 JWT 排除名单，属安全回归。本次派发 Task 6 时在简报之外额外明确警告了这个已知
陷阱，并指向已验证正确的 `AnimeControllerTest`（`@MockBean JwtTokenProvider` + `stubValidToken()` +
显式 401 测试）作为参考模式。任务审查子代理独立复核 `git diff --stat` 确认 `WebMvcConfig.java` 在本次
diff 中零改动，5 个接口均有对应的 401 + `never()` 测试，未重现该问题。

**3. `WatchlistDomainService` 是项目里第一个 `XxxDomainService`，需要新的 Bean 装配约定**

`WatchlistDomainService` 按项目规则要求"领域层不依赖 Spring 框架类型"，是不带任何 Spring 注解的纯类。
但它需要作为 Bean 注入进 `WatchlistApplication`。由于项目里此前没有任何 `XxxDomainService` 实现，
这个装配方式是全新的：新增 `anitrack-application.config.DomainServiceConfig`，用 `@Configuration` +
`@Bean` 工厂方法手动 `new WatchlistDomainService(watchlistRepo, animeRepo)`。全分支最终 review 独立
验证了这个装配不存在循环依赖或缺失 Bean 的问题。该模式目前只在计划文档里说明，尚未写入
`docs/rules/anitrack-model-rules.md` 之类的规则文档，留待后续如果出现第二个 `XxxDomainService`
时再考虑是否需要补充规则说明。

**4. 全分支最终 review 未发现 Critical/Important 问题（7 个 Task 完成后）**

与 Phase 0/1a 不同，本次全分支 review 没有发现需要修复的 Important 级别问题（此前两次都各发现过
2 项 `@Transactional` 缺失/测试覆盖缺口之类的跨任务一致性问题）。独立复现 `mvn test` 全 4 个模块
0 失败，跨任务方法签名一致性、异常转译链路完整性、事务边界正确性、JWT 鉴权独立核实全部通过。
记录了 4 项 Minor（`WatchlistApplication` 无 INFO 日志——与 `AnimeApplication` 既有模式一致非新增
问题；`addToWatchlist` 理论并发重复提交场景下 `DuplicateKeyException` 未被专门捕获，会走兜底异常
分支而非报"该番剧已在追番列表中"，设计文档本身把数据库唯一索引定位为"双保险"而非主校验路径，
可接受；`WatchlistRepoImplTest` 缺少 `getByUserAndAnime` 命中场景的直接测试；`listMyWatchlist`
空列表场景未在应用层/控制器层端到端覆盖），均判定非阻塞，留待后续处理。

**5. 本机默认 Maven 环境不可用**

沿用 Phase 0/1a 记录里发现的同一问题：默认 `PATH` 命中 `/Users/ywy/opt/apache-maven-3.6.0/bin/mvn`
（绑定 JDK 8），无法编译本项目。全程改用 `JAVA_HOME=.../temurin-17.jdk` +
`PATH=/opt/homebrew/opt/maven/bin:$PATH`（Maven 3.9.16）。

**6. 真实端到端联调**

复用 Phase 1a 已验证过的本地环境（`application-local.yml` 提供的 MySQL 凭证），用
`SPRING_PROFILES_ACTIVE=local` 跑通完整端到端验证：真实 MySQL 建 `t_watchlist_item` 表、加入追番/
重复加入报错/番剧不存在报错、状态机合法转移/非法转移报错、`DROPPED→WATCHING` 保留进度不清零、
进度更新/倒退报错/超总集数报错、`totalEpisodes=0`（番剧总集数未知）时放开上限校验、`detail`/`list`
（含按状态筛选）均正确拼接 `Anime` 展示信息，全部符合设计预期，细节见
`.superpowers/sdd/task-7-report.md`。

## 最终结果

- `mvn test`：4 个相关模块（domain/infrastructure/application/starter）全过，103 个测试 0 失败
  （新增 `WatchlistItemTest`(15)/`WatchlistDomainServiceTest`(6)/`WatchlistRepoImplTest`(5)/
  `WatchlistApplicationTest`(13)/`WatchlistControllerTest`(18) 共 57 个用例，另有 `AnimeRepoImplTest`
  因 `listByIds` 新增 2 个用例）
- 真实本地 MySQL 端到端联调已完成，行为与设计文档一致，无新问题
- Phase 1b 全部完成，7 个功能 commit + 1 个文档 commit（`824a413..4af53b9`），全程直接在 main 分支上
  实施，未 push
