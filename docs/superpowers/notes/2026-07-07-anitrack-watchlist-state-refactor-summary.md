# anitrack 追番状态机与进度逻辑重构 实施记录

日期：2026-07-07
设计文档：`docs/superpowers/specs/2026-07-07-anitrack-watchlist-state-refactor-design.md`
实施计划：`docs/superpowers/plans/2026-07-07-anitrack-watchlist-state-refactor.md`

## 目标

修正 Phase 1b 追番状态机的若干语义问题：想看阶段无法直接标记看完/弃番、在看阶段进度不能倒退、
转完看时进度未自动置满、集数异常的番剧无法追番等。在不改变模块划分与持久化方案的前提下，重新
定义状态机边、进度校验规则、`changeStatus` 的进度联动与集数有效性约束，并顺手修复 `updateProgress`
不刷新 `updateTime` 的遗留问题。

## 做了什么

按 9 个 Task 逐步实施（TDD，每个 Task 独立 commit），全程在 main 分支上直接开发，最后移到
feature 分支开 PR 合并：

1. 新增 `AnimeTotalEpisodesInvalidException` 领域异常
2. 扩展 `WatchlistItem` 状态机 `TRANSITIONS` 表（新增 3 条边）
3. 调整 `updateProgress` 校验（下限 `>=0`、删倒退、刷 `updateTime`）
4. 扩展 `changeStatus` 签名为 `(newStatus, totalEpisodes)`，转看完时进度置满 + 聚合根自我保护
5. `WatchlistDomainService` 新增 `changeStatus` 编排与集数校验，返回 `WatchStatusChangedEvent`
6. `AppExceptionEnum` 新增 `ANIME_TOTAL_EPISODES_INVALID`（40103）
7. `WatchlistApplication.changeStatus` 改走 DomainService，事件透传
8. `WatchlistControllerTest` 非法转移样例调整（`WATCHED→WATCHING`）
9. 全量构建与回归

全部完成后做了一次全分支最终 review（opus），修复 2 项 Minor，合并到 main
（分支 `feature/watchlist-state-refactor`，10 个功能 commit + 2 个文档 commit）。

## 关键设计决策

**1. 集数异常允许追番，但禁止转在看/看完**

最初设计是"集数异常不可追番"，对话中调整为：集数未知（`null` 或 `<=0`）的番剧允许加入想看，
等数据更新后再开追。集数校验从追番入口移到 `changeStatus`，由 DomainService 统一拦截——
转在看/看完需集数有效，转弃番/想看不校验。

**2. changeStatus 进度联动与事件透传**

`changeStatus` 签名扩展为 `(newStatus, totalEpisodes)`，转看完时 `currentEpisode = totalEpisodes`。
聚合根作为不变量守护者，对 `totalEpisodes` 无效自我保护抛 `AnimeTotalEpisodesInvalidException`
（理论上不会触发，DomainService 已预校验，但防御纵深有价值）。

事件透传设计：聚合根产生 `WatchStatusChangedEvent` → DomainService 透传 → Application 发布。
DomainService.changeStatus 返回 event 而非 item，应用层发布 event 后重新 `getByUserAndAnime`
取 item 转 BO。多一次查询换取职责清晰（event 携带真实 oldStatus，不再像旧代码那样自造 null）。

**3. 1 集番剧边界**

进入在看时进度保持当前值（不强制置 1），避免 1 集番剧点在看瞬间进度 1 等于总集数被自动转看完
的矛盾。配合"取消进度自动转看完"的设计，1 集番剧可正常停留在在看状态。

## 遇到的问题及解决

**1. `changeStatus` 签名扩展破坏现有调用方（Task 4）**

聚合根 `changeStatus` 从单参改为双参后，一次性破坏了 Task 2 测试、`WatchlistDomainServiceTest`、
`WatchlistApplicationTest`。按任务顺序逐层修复：Task 4 只修聚合根测试，Task 5/7 修下游。
中间状态编译不通过属预期，TDD 红绿仍能在目标模块内验证。

**2. 事件透传导致应用层拿不到 item（Task 7 设计）**

DomainService 返回 event 后，应用层需要 item 转 BO。最初在计划里留下了一段混乱的"决策"讨论。
最终固化方案：DomainService 返回 event，应用层发布后重新查询 item。多一次 repo 查询，但
职责分层清晰，event 的 oldStatus 真实可靠。

**3. 全分支最终 review 发现 2 项 Minor**

- `WatchlistApplication.changeStatus` re-fetch 后无 null 防御，与同文件 `getWatchlistItem` 风格
  不一致——补上 `if (item == null) throw ...WATCHLIST_ITEM_NOT_FOUND`
- 1 集番剧边界无显式测试——新增 `changeStatus_whenTotalEpisodesIsOne_shouldAllowWatchingWithProgressZero`

修复后 174 个测试全过，判定 Ready to merge，合并到 main。

**4. 本地 Maven/JDK 环境问题（与 Phase 0 同源）**

无 `mvnw` 包装器，默认 `JAVA_HOME` 是 JDK 8。子代理统一使用
`JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home /opt/homebrew/bin/mvn`。
跨模块测试时需先 `install -DskipTests` 上游模块。

## 最终结果

- `mvn test`：5 模块全过，174 个测试 0 失败 0 错误
  - common 3 / domain 53（含新增 `AnimeTotalEpisodesInvalidExceptionTest` 1、`WatchlistItemTest` 22、
    `WatchlistDomainServiceTest` 13）/ infrastructure 24 / application 35 / starter 59
- 全分支最终 review（opus）：Ready to merge，无 Critical/Important
- PR #2 已合并到 main（`a379c2c`），分支 `feature/watchlist-state-refactor`
- 重构全程未触碰持久化层（PO/Mapper/XML/RepoImpl）、未改 API 端点与请求响应结构、未引入"进度自动转看完"

## 执行方式

Subagent-Driven Development：每个 Task 派发独立 implementer 子代理（机械任务用 haiku、
跨层判断用 sonnet），完成后派发 task reviewer 子代理做 spec 合规 + 代码质量审查。
控制器（我）负责协调、审查 ⚠️ 项、修复 Minor、最终全分支 review。
