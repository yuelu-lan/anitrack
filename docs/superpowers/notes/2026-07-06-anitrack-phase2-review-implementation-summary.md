# anitrack Phase 2 实施记录

日期：2026-07-06
设计文档：`docs/superpowers/specs/2026-07-06-anitrack-phase2-review-design.md`
计划文档：`docs/superpowers/plans/2026-07-06-anitrack-phase2-review.md`

## 目标

实现评价（`Review`）上下文：用户对已看完（`WATCHED`）番剧的评分（1-10）与评论（可选），
依赖 Phase 1b 的 `WatchlistRepo` 做跨上下文校验，暴露新增/修改/查看我的评价/按番剧分页列表/
我的评价列表 5 个接口，并落地项目第一个通用分页响应壳 `PageResponse<T>`。

## 做了什么

按 7 个 Task 逐步实施（Subagent-Driven Development：每个 Task 一个全新子代理实现 + 一个独立
子代理审查，TDD，每个 Task 独立 commit；本次用户选择新建 worktree/feature 分支
`worktree-phase2-review`，完成后合并回 main，不同于 Phase 1b 直接在 main 上实施）：

1. `Review` 聚合根（内置 score 1-10 校验）+ `IllegalReviewScoreException`
2. `ReviewRepo` 仓储接口 + `ReviewNotAllowedException`/`ReviewAlreadyExistsException` +
   `ReviewDomainService`（跨上下文编排：查询 `WatchlistRepo` 校验 `WATCHED` 状态、查重）
3. `t_review` 表 Flyway 脚本 + `ReviewPO`/`ReviewMapper` + `UserMapper` 补充 `selectByIds`
4. `ReviewConverter`（MapStruct）+ `ReviewRepoImpl` + `UserRepo`/`UserRepoImpl` 补充 `listByIds`
   批量查询（供拼接评论人昵称/头像）
5. `AppExceptionEnum` 扩展（403xx 段）+ `ReviewBO`/`ReviewWithUserViewBO`/`ReviewWithAnimeViewBO`/
   `ReviewPageBO` + `ReviewAssembler`（拼接 `Review+User`/`Review+Anime` 两种展示数据）+
   `DomainServiceConfig` 补充 `reviewDomainService` Bean 装配 + `ReviewApplication`（编排 5 个
   用例 + 异常转译）
6. `PageResponse<T>` 通用分页响应壳（项目首次落地）+ 请求/响应对象 + `ReviewController`（5 个
   JWT 鉴权接口）+ Web 层集成测试
7. 全量编译测试 + 真实本地 MySQL 端到端联调

全部完成后做了一次全分支最终 review（opus 模型），发现 2 项 Important，修复并复审通过后，
以 `--no-ff` 合并到 main（merge commit `3a74d98`），worktree 已清理，未 push。

## 遇到的问题及解决

**1. brainstorming 阶段两次发现 spec 自身的设计缺陷，写入前就地修正**

- 分页响应壳最初在 spec 里定为 `PageResponse{ list, total }` 两字段，写实施计划前核对
  `docs/rules/anitrack-web-rules.md` 第10.5节才发现项目早就定义了 `pageNum/pageSize/total/list`
  四字段的统一约定（此前从未有接口真正落地过分页，所以这个约定一直没被用上）。若不核对直接照抄
  spec，会造出一套与既有项目规范并存的重复概念。已改写 spec 与后续 Web 层设计对齐四字段版本。
- spec 的领域异常清单里声明了 `ReviewNotFoundException extends AnitrackDomainException`，但通读
  全部业务流程后发现 `updateReview`/`getMyReview` 都是纯应用层判断直接抛 `AnitrackAppException`，
  从来不会真正抛出这个类——是从 Watchlist 侧 `WatchlistItemNotFoundException`（该类真实被
  `WatchlistDomainService.updateProgress` 使用）类比时误加的死代码。写入前已删除。

**2. worktree 与本地 main 之间的提交/未跟踪文件没有自动同步（Task 派发前的环境准备阶段）**

`EnterWorktree` 默认从 `origin/<default-branch>` 新建 worktree（"fresh" 策略），而不是从本地
`main`。当时本地 `main` 已经领先 `origin/main` 3 个 commit（brainstorming 阶段写的 spec 文档），
且实施计划文档本身当时还是未提交的 untracked 文件——两者在新建的 worktree 里都不存在。派发
Task 1 之前发现这个缺口，在 worktree 里 `git merge main --ff-only` 补齐 3 个 spec commit，再手动
`cp` 计划文件过去并单独 commit。这是本项目第一次真正使用 worktree 做多 Task 实施（Phase 1a 虽然
也建过 worktree 但当时本地 main 与 origin 一致，未触发这个问题；Phase 1b 直接在 main 上实施，
不适用），记录下来供后续类似场景参考：**用 worktree 前如果本地分支领先远端或有未提交内容，
必须显式核对并同步，不能假设 worktree 会自动带上它们**。

**3. 全分支最终 review 发现 2 项 Important（7 个 Task 完成后）**

- `ReviewRepoImpl.add()` 返回的 `Review` 里 `createTime` 恒为 `null`——`t_review.create_time` 是
  数据库侧 `DEFAULT CURRENT_TIMESTAMP`，`insert()` 的 `useGeneratedKeys` 只回写自增 `id`，从不
  回写 `create_time`，而代码复用同一个 `po` 对象做转换，因此新建评价刚返回时时间戳永远是空的。
  这与 Phase 0 记录里 `UserRepoImpl.save()` id 未回写、Phase 1b 里 `WatchlistRepoImpl.add()`
  的 `updateTime` 同款遗漏是同一个根因模式（insert 后用同一个未刷新的 PO 对象转换），但因为
  `createTime` 是 Review 领域模型明确暴露给客户端的字段（不像 `WatchlistItem` 干脆不暴露
  `createTime`），对使用方而言是更显眼的真实 bug。修复：insert 后用
  `reviewMapper.selectByUserAndAnime(...)` 重新查一次再转换，而不是复用原 `po`；同时改造了
  被掩盖问题的单测——原测试直接 mock `toDomain(po)` 返回预置对象，无法区分"转换原始 po"还是
  "转换重新查询的 po"，改为 mock 两个不同的 PO 对象并用 `verify(..., never()).toDomain(po)`
  钉死这个区别。
- `ReviewListByAnimeReq.page`/`pageSize` 完全没有边界校验，与 spec 明确写的"Controller 只做
  必填校验"直接冲突——但这条 spec 决策本身与项目早就存在的 `docs/rules/anitrack-web-rules.md`
  第3.2节（分页请求需要 `@Min(1)`/`@Max(100)`）矛盾，是本项目第一个真正落地分页的接口，第一次
  真正触发这条此前一直没被用到的规则。`page<=0` 会让 `offset` 算出负数，SQL 层报错后被兜底成
  模糊的"系统异常"而非清晰的 400。这是 spec 与项目规则的冲突而非纯粹实现缺陷，按流程呈报给用户
  定夺，用户选择对齐既有规则，补上边界校验注解与 4 个对应的 400 测试用例。

修复后单独一个 commit，复审确认两处修复均生效、无新增问题、改动范围未超出这两个文件对，判定
Ready to merge。

**4. 本机默认 Maven 环境不可用**

沿用此前三个 Phase 记录里的同一问题：默认 `PATH` 命中 `/Users/ywy/opt/apache-maven-3.6.0/bin/mvn`
（绑定 JDK 8），无法编译本项目。全程改用 `JAVA_HOME=.../temurin-17.jdk` +
`PATH=/opt/homebrew/opt/maven/bin:$PATH`（Maven 3.9.16）。

**5. 真实端到端联调**

复用此前已验证过的本地环境（`application-local.yml` 提供的 MySQL 凭证），`SPRING_PROFILES_ACTIVE=local`
跑通完整流程：真实 MySQL 建 `t_review` 表（Flyway 升到 v4）、未看完时评价报错、推进到 `WATCHED`
后新增评价成功、重复评价报错、非法评分报错、修改评价、查看详情、按番剧分页列表（正确拼接评论人
昵称）、我的评价列表（正确拼接番剧标题/封面），全部符合预期。

**6. 合并阶段：本地 main 工作区残留的未跟踪计划文件阻塞 merge**

`git merge worktree-phase2-review` 第一次执行时被 git 拒绝："以下未跟踪文件会被合并覆盖"——
因为问题 2 里手动 `cp` 到 worktree 的计划文件，在原本的 main 工作区里也留了一份未提交的同名
副本（内容逐字节相同）。`diff` 确认内容一致后直接删除该副本，重新执行 merge 成功。

## 最终结果

- `mvn test`：5 模块全过，158 个测试 0 失败（本次新增 55 个：`ReviewTest`(10)、
  `ReviewDomainServiceTest`(4)、`ReviewRepoImplTest`(6)、`UserRepoImplTest`(2，新文件)、
  `ReviewApplicationTest`(12)、`ReviewControllerTest`(21)）
- 真实本地 MySQL 端到端联调已完成，行为与设计文档一致，无新问题
- Phase 2 全部完成，8 个功能/修复 commit + 1 个 merge commit（`d710fbe..3a74d98`），
  worktree 已清理，本地 main 未 push
