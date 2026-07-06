# anitrack Phase 1a 实施记录

日期：2026-07-05
设计文档：`docs/superpowers/specs/2026-07-05-anitrack-phase1-anime-catalog-design.md`
计划文档：`docs/superpowers/plans/2026-07-05-anitrack-phase1a-anime-catalog.md`

## 目标

实现番剧目录（`Anime`）上下文：通过 ACL 防腐层对接真实 Bangumi API（`api.bgm.tv`）完成搜索/详情查询，
转换为本地只读缓存领域模型，暴露 `/api/anime/search`、`/api/anime/detail` 两个接口。Phase 1 的另一半
——追番核心（`Watchlist` 状态机）——拆成独立 spec，本次不涉及。

## 做了什么

按 7 个 Task 逐步实施（Subagent-Driven Development：每个 Task 一个全新子代理实现 + 一个独立子代理审查，
TDD，每个 Task 独立 commit）：

1. `Anime` 聚合根 + `AnimeRepo`/`BangumiGateway` 仓储网关接口 + `BangumiApiException`
2. `t_anime` 表 Flyway 脚本 + `AnimePO` + `AnimeMapper`
3. `AnimeConverter`（MapStruct）+ `AnimeRepoImpl`（upsert 语义）
4. Bangumi DTO + RestClient 配置 + `BangumiConverter` + `BangumiGatewayImpl`（真实调用 `POST /v0/search/subjects`）
5. `AppExceptionEnum` 扩展 + `AnimeBO` + `AnimeApplication`（搜索编排 + 异常转译）
6. `AnimeController`（JWT 鉴权接口）+ Web 层集成测试
7. Bangumi 配置接入 `application.yml`

全部完成后做了一次全分支最终 review，修复发现的问题，合并到 main（分支
`worktree-phase1a-anime-catalog`，9+1 个 commit，Fast-forward 合入）。

## 遇到的问题及解决

**1. Bangumi API 字段细节，训练知识不可靠（brainstorming 阶段）**

设计阶段发现无法通过 WebFetch 直接访问 `bangumi.github.io/api`（沙箱网络限制）。改用 `gh api` 直接拉取
`github.com/bangumi/api` 仓库的 `open-api/v0.yaml`（OpenAPI 规范源文件）逐字段核实，而非依赖训练知识
猜测接口形状——最终确认搜索接口是 `POST /v0/search/subjects`（非 GET），总集数字段有 `eps`/`total_episodes`
两个含义不同的字段，`date`/`images` 均为非必填字段需要空值防御。

**2. Plan 里对 MapStruct 工厂方法自动识别的假设是错的（Task 3）**

`Anime` 按规则设为 `@Builder(access = PRIVATE)`，计划文档里错误假设 MapStruct 能仅凭参数名匹配
自动识别 `Anime.reconstitute(...)` 作为构造入口。Task 3 实现子代理实际编译后发现不成立——`Anime`
没有可访问的构造函数，编译报错。子代理独立核实了 `UserConverter` 的真实代码，发现现有模式其实是
显式手写 `default toDomain(...)` 方法直接调用 `reconstitute()`，并非依赖 MapStruct 自动生成；照此模式
修正 `AnimeConverter`，任务审查确认这一偏离是正确的（计划文档的技术前提有误，不是实现问题）。

**3. Task 6 第一版实现为了让测试跑通，误将鉴权接口做成了公开接口**

`AnimeControllerTest` 在 `@WebMvcTest` 切片下加载了 `WebMvcConfig`（`WebMvcConfigurer`），需要
`JwtAuthInterceptor → JwtTokenProvider` 才能构造成功。实现子代理为了让测试编译通过，把
`/api/anime/search`、`/api/anime/detail` 加进了 `WebMvcConfig` 的 JWT 排除名单——这直接违反设计文档
"两个接口都需要 JWT 鉴权" 的明确要求。任务审查子代理独立核实设计文档原文后判定为 Important 问题，
派发修复：撤销 `excludePathPatterns` 改动，测试改为正确模拟已登录态（`@MockBean JwtTokenProvider` +
`Authorization` header），并新增两个「无 token 返回 401」的测试用例验证鉴权确实生效。复审通过。

**4. 全分支最终 review 发现 2 项 Important（7 个 Task 完成后）**

- `AnimeApplication.searchAnime()` 在循环里执行 `AnimeRepo.upsert()` 写库，但缺少 `@Transactional`——
  与项目里几天前刚修复过的同类问题（`UserApplication.register()` 事务边界缺失）如出一辙，属于只有
  对比整个分支才能发现的跨任务一致性问题。已补上。
- `BangumiGatewayImpl.search()` 的 `try/catch` 只包住了 RestClient 调用本身，DTO 转换步骤
  （`.map(bangumiConverter::toDomain)`）在 catch 块外面——若 Bangumi 返回格式异常的 `date` 字段会抛出
  未被捕获的 `DateTimeParseException`，绕过预期的 `BangumiApiException → AnitrackAppException` 转译链，
  直接落到全局异常处理器的兜底分支。已扩大 try 块范围覆盖转换步骤。

修复后全量测试 44 个用例 0 失败，判定 Ready to merge（with fixes 已修复），合并到 main。

**5. 本机默认 Maven 环境不可用**

沿用 Phase 0 记录里发现的同一问题：默认 `PATH` 命中 `/Users/ywy/opt/apache-maven-3.6.0/bin/mvn`
（绑定 JDK 8），无法编译本项目（要求 JDK 17 + Maven ≥3.6.3）。全程改用
`JAVA_HOME=.../temurin-17.jdk` + `PATH=/opt/homebrew/opt/maven/bin:$PATH`（Maven 3.9.16）。

**6. Task 7 Step 3 真实端到端联调**

实施 Task 7 时先用 `curl`/`mysqladmin ping` 探测确认沙箱访问不了 `api.bgm.tv`、本地 MySQL 也没有
凭据，因此当时只完成了 Step 1（`application.yml` 配置）+ Step 2（编译测试），Step 3 的真实联调
先记为用户手动待办。之后用户提供了 `application-local.yml` 里的真实本地 MySQL 密码，重新探测发现
`api.bgm.tv` 实际可以访问（此前是一次性网络抖动，不是永久限制）。补上 `user-agent` 配置的真实仓库
地址后，用 `SPRING_PROFILES_ACTIVE=local` 直接跑通了完整的端到端验证：真实 MySQL 建表、真实调用
Bangumi API 搜索（拿到"败犬女主太多了"等真实条目数据）、JWT 鉴权拦截、404 场景、upsert 幂等性，
全部符合预期，细节见 `docs/superpowers/notes/2026-07-05-anitrack-phase1a-manual-followup.md`。

## 最终结果

- `mvn test`：5 模块全过，44 个测试 0 失败（新增 `AnimeTest`/`AnimeRepoImplTest`/`BangumiConverterTest`/
  `AnimeApplicationTest`/`AnimeControllerTest` 共 19 个用例）
- 真实 MySQL + 真实 Bangumi API 端到端联调已完成，行为与设计文档一致，无新问题
- Phase 1a 全部完成并已合并到 main（本地，未 push），worktree 已清理
