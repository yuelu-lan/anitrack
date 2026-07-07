# anitrack 规则合规修复 实施记录

日期：2026-07-07
设计文档：`docs/superpowers/specs/2026-07-07-anitrack-rule-compliance-design.md`
实施计划：`docs/superpowers/plans/2026-07-07-anitrack-rule-compliance.md`

## 目标

修复规则审计发现的 4 项中等级别偏差，使后端代码与 `docs/rules/` 对齐：应用层缺 MapStruct
Converter、Web 响应枚举非 `{code,name}`、时间字段未 ISO-8601 序列化、`UserController` 直接注入
`JwtTokenProvider` 签发 token。不涉及功能变更，不新增 API 端点，不调整持久化层。同步顺手修正
状态机重构遗留的规则文档滞后与 spec 内部矛盾。

## 做了什么

分两批实施。批 1 纯文档同步，批 2 代码修复走完整 superpowers 工作流（brainstorming →
writing-plans → subagent-driven-development → finishing-a-development-branch），在
`feature/rule-compliance` 分支上按 9 个 Task 逐步实施（每个 Task 派发独立 implementer 子代理
+ task reviewer 子代理做 spec 合规 + 代码质量审查），全程 TDD。

### 批 1：文档同步（commit `e19f8b9`）

- `anitrack-model-rules.md` / `anitrack-domain-rules.md`：状态机 `TRANSITIONS` 表与
  `changeStatus` 签名同步至 2026-07-07 重构后的新状态机（补 `WANT_TO_WATCH→WATCHED/DROPPED`、
  `DROPPED→WANT_TO_WATCH`；`changeStatus(newStatus, totalEpisodes)` 双参签名）
- `anitrack-domain-rules.md`：新增 `changeStatus` 跨上下文集数校验条目
- refactor spec 第 113 行返回类型矛盾修正：`WatchlistItem` → `WatchStatusChangedEvent`，补
  re-fetch 说明

### 批 2：代码修复（PR #3，5 个 commit 合并到 main）

按 9 个 Task 实施，3 个 commit 分批提交：

**commit A `f9b3965` — 应用层 MapStruct Converter（Task 1-2）**

- 新增 `anitrack-application/converter/` 下 4 个 `@Mapper(componentModel="spring")` 接口：
  `AnimeBOConverter`/`UserBOConverter`/`WatchlistBOConverter`/`ReviewBOConverter`
- 4 个 Application 删除手写 `toBO()`，改注入对应 Converter，方法命名 `xxx2BO`
- application 模块 pom 补 MapStruct 依赖 + annotationProcessorPaths（参照 infra pom）
- toBO 方向 MapStruct 自动映射（源=聚合根 getter、目标=BO `@Builder` 公开），无需手写 default

**commit B `032f364` — Web 枚举 {code,name} + 时间 ISO-8601（Task 3-5）**

- `WatchStatus` 新增 `code` 字段（1/2/3/4）
- 新增 `EnumVO {Integer code, String name}` 不可变值对象
- `WatchlistItemResponse`/`WatchlistItemViewResponse` 的 `status` 字段改 `EnumVO`
- `HttpConverter` 新增 `watchStatus2VO` 转换方法（含 null 防御）
- 新增 `JacksonConfig`：`JavaTimeModule` + 禁用 `WRITE_DATES_AS_TIMESTAMPS`，`LocalDateTime`
  全局输出 ISO-8601 字符串

**commit C `1e07123` — JWT 签发下沉应用层（Task 6-9）**

- domain 新增 `TokenProvider` 接口（`domain.user.service`，纯接口无 Spring 依赖）
- `JwtTokenProvider` `implements TokenProvider` + `@Override generateToken`
- 新增 `LoginBO {UserBO user, String token}`
- `UserApplication.login()` 返回 `LoginBO`，注入 `TokenProvider` 接口（非 infra 实现类），
  在应用层签发 token
- `UserController` 移除 `JwtTokenProvider` 依赖，改用 `LoginBO`
- `HttpConverter.req2BO` 重命名为 `userRegisterReq2BO`/`userLoginReq2BO`（消除泛化重载，
  符合 `xxx2Yyy`）；`toLoginResponse` 改签名为接收 `LoginBO`

**commit `3278ab3` — Minor 修复（最终 review 后）**

- 删除 `AnimeConverterTest`，4 个 Converter 统一只靠 ApplicationTest 覆盖，消除对 MapStruct
  生成类名 `AnimeConverterImpl` 的耦合
- `WatchlistControllerTest` 的 updateProgress/detail/list 三测试补 `watchStatus2VO` stub，
  与 add/changeStatus 一致，避免 status 字段序列化为 null

**commit `fa35ead` — bean 名冲突修复（真实启动暴露）**

- application 层 4 个 Converter 加 `BO` 后缀（`AnimeBOConverter` 等），与 infra 层同名
  Converter 生成的 MapStruct Impl bean 名解耦，解决真实启动时的
  `ConflictingBeanDefinitionException`

## 关键设计决策

**1. JWT 下沉的依赖方向处理**

`JwtTokenProvider` 在 `infrastructure.auth`，`UserApplication` 在 application 层。规则要求
`application` 不直接依赖 `infrastructure`（依赖倒置）。方案：在 domain 层新增 `TokenProvider`
纯接口，`JwtTokenProvider` 实现之，`UserApplication` 依赖接口而非实现类。`TokenProvider` 无
Spring 依赖，放 domain 层合规。

`JwtAuthInterceptor`（starter 层）仍直接持有 `JwtTokenProvider` 实现类——starter 可依赖 infra，
属合规方向，且拦截器用的 `validateToken`/`getUserId` 不在 `TokenProvider` 契约内，无需接口化。

**2. changeStatus 事件透传 vs 返回 item**

refactor spec 第 113 行原本写 `DomainService.changeStatus` 返回 `WatchlistItem`，但第 101 行又
说返回 `WatchStatusChangedEvent`。本次批 1 核实确认：实际代码遵循第 101 行 + 实施总结的决策
（返回 event，应用层发布后 re-fetch 取 item 转 BO），第 113 行是写 spec 时未同步的遗留。批 1
修正了 spec 文字，代码不动。

**3. `@WebMvcTest` 切片下保留 `@MockBean JwtTokenProvider`**

`UserControllerTest` 移除 Controller 的 `JwtTokenProvider` 依赖后，测试里的
`@MockBean JwtTokenProvider` 不能删——`WebMvcConfig` 注册的 `JwtAuthInterceptor` 依赖它，
`@WebMvcTest` 切片需要该 Bean 构造拦截器。这是计划里"修正 Step 3"明确记录的判断。

## 遇到的问题及解决

**1. 真实启动报 `ConflictingBeanDefinitionException`（commit `fa35ead`）**

PR 合并前手动启动应用时报错：`animeConverterImpl` bean 名冲突——`infra.converter.AnimeConverterImpl`
与 `application.converter.AnimeConverterImpl` 同名。MapStruct 默认用类名生成 bean 名，两个模块
各有同名 `AnimeConverter`/`UserConverter`/`ReviewConverter` 接口就撞了。

根因：之前所有测试都是 `@WebMvcTest` 切片，不扫全 bean，冲突从未暴露。真实启动全 bean 扫描才触发。

修复：application 层 4 个 Converter 加 `BO` 后缀（`AnimeBOConverter` 等），与 infra 的
`AnimeConverter`（toPO/toDomain 方向）天然区分，生成的 Impl 名也不再撞。重新启动验证通过。

> 教训：切片测试无法覆盖 bean 装配冲突。本次这类"两个模块同名 MapStruct Converter"的问题，
> 只有全 context 启动才会暴露。后续若新增跨模块同名 Converter，需在命名上预先区分，或考虑
> 加一个 `@SpringBootTest` 启动冒烟测试。

**2. `WatchlistControllerTest` 的 `watchStatus2VO` mock stub 遗漏（Task 4）**

Task 4 implementer 发现 brief Step 4 遗漏了 `watchStatus2VO` 的 mock stub：测试用
`thenCallRealMethod()` 调 `watchlistItemBO2Response`，其内部对 `watchStatus2VO` 的调用走 mock
默认返回 null，导致 `$.data.status.name` 断言失败。implementer 在 add/changeStatus 两个测试补
了 stub，最终 review 后又在 updateProgress/detail/list 三测试补齐（commit `3278ab3`）。

这是 Mockito 标准行为：mock 对象的 `thenCallRealMethod` 真实方法体内调 `this.其他方法` 仍走
mock dispatch，返回默认值。

**3. macOS BSD sed 不支持 `\b` 边界（commit `fa35ead` 重命名时）**

批量替换 Converter 引用时，`sed -e 's/\bAnimeConverter\b/...'` 在 macOS BSD sed 上不生效
（`\b` 不被识别），导致首次替换全部失败。改用无边界全局替换（`s/AnimeConverter/.../g`）解决，
这些标识符足够独特不会误伤。

## 最终结果

- `mvn clean test`：5 模块全过，176 个测试 0 失败 0 错误
  - common 3 / domain 54（含新增 `WatchStatusTest` 1）/ infrastructure 24 / application 35
    （删 `AnimeConverterTest` 后 -1）/ starter 60（含新增 `JacksonConfigTest` 1）
- 真实启动验证：bean 冲突已解决，应用正常启动
- 全分支最终 review（opus）：Ready to merge，无 Critical/Important
- PR #3 已合并到 main（merge commit `d5f4bf9`），feature 分支已删除

## 遗留的低级别偏差

本次只修了中等级别偏差。以下低级别偏差未处理，按层分组记录供后续 triage：

### 应用层

1. **`AnitrackAppException` 缺 `build()` 静态工厂**——规则 4.3 要求 `build(String, Object...)`
   与 `build(AppExceptionEnum)` 两个静态方法，实际只有一个 public 构造器，调用点全用 `new`。
   功能等价，但与规则签名不符。严重度：低
2. **`UserApplication` 承担用户名唯一性业务规则**——`existsByUsername` 直接写在应用层，缺
   `UserDomainService`。其他上下文（Watchlist/Review）均有 DomainService，user 上下文缺位。
   纯架构一致性问题。严重度：低
3. **应用服务普遍缺 `@Slf4j` / 关键写操作 INFO 日志**——`WatchlistApplication`/
   `ReviewApplication`/`UserApplication` 均无 `@Slf4j`，changeStatus/addReview/register 等关键
   写操作无 INFO 日志（规则 3.4）。`AnimeApplication` 有 `@Slf4j` 但仅用于 error。phase1b 总结
   里已记为已知 Minor。严重度：低

### Web 层

4. **`WatchlistController.list` 缺 `@Valid`**——`WatchlistListReq` 也无任何校验注解（规则 4.1）。
   严重度：低
5. **Controller 缺 `@Slf4j`**——4 个 Controller 均无（规则 2.1 示例含）。当前 Controller 也确实
   无日志输出。严重度：低
6. **请求对象用 `@Getter` 而非规则示例的 `@Data`**——所有 `XxxReq` 用 `@Getter`，对反序列化无
   影响，纯风格偏离。严重度：低

### 持久化层

7. **MyBatis XML 占位符未写 `jdbcType`**——规则示例带 `#{userId,jdbcType=BIGINT}`，实际全部
   未写。MyBatis 可推断类型，运行无影响。严重度：低
8. **INSERT 未显式写 `create_time`/`update_time`**——依赖 DB `DEFAULT CURRENT_TIMESTAMP` 兜底
   （V3/V4 迁移脚本已配 DEFAULT + ON UPDATE）。功能等价。严重度：低
9. **`anime.cover_url` `VARCHAR(512)`**——规则 4.2 短文本建议 255，URL 字段合理放宽。严重度：低

### 架构/设计

10. **`SecurityConfig` 全量 `permitAll`，无 RBAC 角色校验**——规则 7.5 提 `@PreAuthorize`，当前
    spec 仅区分登录/未登录，无管理角色场景（YAGNI）。可接受。严重度：低

### 建议优先级

- 值得修：1（异常工厂统一）、4（`@Valid` 健壮性）
- 看情况：2（`UserDomainService` 架构一致性）、3/5（日志）
- 可不动：6/7/8/9（风格/等价实现）、10（YAGNI）

## 执行方式

Subagent-Driven Development：每个 Task 派发独立 implementer 子代理（机械任务用 haiku、跨层
判断用 sonnet），完成后派发 task reviewer 子代理做 spec 合规 + 代码质量审查。控制器（我）负责
协调、审查 ⚠️ 项、修复 Minor、最终全分支 review（opus）。Task 6/7（极简：新建接口、加
implements）由控制器直接执行跳过 review，合并到 Task 8 一起 review。
