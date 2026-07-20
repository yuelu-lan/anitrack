# anitrack RAG 已入库番剧列表 实施记录

日期：2026-07-20
设计文档：`docs/superpowers/specs/2026-07-17-rag-doc-list-design.md`
计划文档：`docs/superpowers/plans/2026-07-17-rag-doc-list.md`
分支：`docs/rag-doc-list`（spec+plan+code 同 PR，PR #10）

## 目标

RAG 知识库已能采集（手动传 ids、按条件批量），但入库后无任何查看入口，用户无法知道 Chroma `anime_wiki` collection 里到底索引了哪些动画。新增「查看已入库番剧」能力：后端列表端点 + 前端列表页（前端分页 + 标题搜索）。

## 做了什么

完整走 brainstorming → writing-plans → subagent-driven-development 流程，6 个 Task + final review + 迭代调整，全部落在 `docs/rag-doc-list` 分支：

### 阶段一：Subagent-Driven 执行 6 Task（TDD，每 Task fresh subagent 实现 + 独立 reviewer）

1. **rag-service `GET /documents` 路由**：`vectorStore.ts` 加 `listDocuments`（langchain `Chroma` store 的 public `collection.get({include:["metadatas"],limit:1000})` 取 ids+metadatas），`routes/documents.ts` Fastify plugin，`index.ts` decorate + register。vitest mock decorated 函数验证路由层。
2. **Java domain + infra**：`RagDocumentSummary` 值对象（animeId:Long, title:String，照 `RagDocument` 模式）+ `RagGateway.listDocuments()` 接口 + `RagGatewayImpl` 实现（RestClient GET `/documents` + `X-Internal-Token` + `Long.parseLong` 解析 string→Long + `DocumentsResponse`/`DocumentItem` record）。MockWebServer 测试验证解析 + HTTP 契约。
3. **Java application**：`RagApplication.listDocuments()` 纯委托 gateway。
4. **Java starter**：`RagDocumentSummaryResponse`（starter.response 包）+ `RagIngestController` 加 `GET /documents`（stream map 内联转换，无 converter）+ `RagIngestControllerTest`（@WebMvcTest + JWT mock + jsonPath 断言）。全量回归 BUILD SUCCESS。
5. **webui 列表页**：`services/rag.ts` 加 `listDocuments` + `pages/rag/documents.tsx`（antd Table + Input.Search 前端 filter + useEffect 加载）+ `.umirc.ts` 路由 + `layouts` 菜单「已入库」项（更长 key 放 `/rag` 前避免 startsWith 误高亮）。
6. **端到端验证**：启动 Chroma+rag-service+后端+webui，curl `/api/rag/documents` 返回 53 条，浏览器表格+分页+搜索正常，异常路径（停 rag-service）前端 toast 兜底 + Table 空状态不白屏。

### 阶段二：final whole-branch review + fix

最终 review（opus）**Ready to merge: Yes**，无 Critical/Important，7 项 Minor。修 3 项有价值的：
- `Long.parseLong` 无防御（单条非数字 animeId 致整列表失败）→ try/catch 跳过坏条目 + `log.warn`
- `RagIngestController` 401 测试（spec 第 7 节要求）→ 实测 `@WebMvcTest` 加载 `WebMvcConfig` + `JwtAuthInterceptor`，无 JWT→401 测试 GREEN
- `RagGatewayImplTest` 补 `get(1)` 断言 + null 响应分支测试

### 阶段三：需求迭代（用户验证后调整）

- **扩字段**：用户要原文名 + 其他番剧信息 → 扩 ingest metadata 链路 13 文件（RagDocument/Summary/of + toDocument + RagGatewayImpl ingest/listDocuments + DocumentItem record + Response + controller + rag-service ingest.ts/vectorStore.ts + 前端 services/rag.ts/documents.tsx），重跑 `ingest_by_criteria` 重新采集让 Chroma 数据含新 metadata
- **移除集数字段**：`totalEpisodes` 都是 0（Bangumi `/v0/subjects` 列表响应不含集数，`listAnimeByYearRating` 为省请求没逐条 getById），用户决定不要 → 全链路移除
- **调大 Bangumi timeout**：`application.yml` `read-timeout-ms` 5000→60000、`connect-timeout-ms` 3000→10000（代理慢时 5s 偏紧导致采集失败）
- **pageSize 切换**：`pagination={{ pageSize: 10 }}` 受控固定覆盖切换 → 改 `defaultPageSize` 非受控
- **列省略号**：标题/原文名列 `ellipsis: true`（超长省略 + 悬浮 tooltip）
- **评分列宽**：`width: 120`
- **gitignore**：`rag-service/.gitignore` 加 `chroma_data`（sqlite 数据目录，避免误提交）

## 遇到的问题及解决

**1. chromadb `IncludeEnum` 是 runtime enum 非 string union**

Task 1 brief 建议 `include: ["metadatas"]`，但 chromadb 1.9.0 的 `IncludeEnum` 是 TypeScript `enum`（`dist/chromadb.d.ts`），字符串字面量 `"metadatas"` 不能赋值给 `IncludeEnum`，`as ["metadatas"]` cast 也失败。改用 `import { IncludeEnum } from "chromadb"` + `[IncludeEnum.Metadatas]`（runtime 值仍为 `"metadatas"`），类型安全且运行时等价。reviewer 验证 claim 准确。

**2. mvn `-am -Dtest` 上游模块"无匹配测试"失败**

`mvn -q -pl anitrack-infrastructure -am test -Dtest=RagGatewayImplTest` 因 `-am` 触发上游 `anitrack-common` 跑 surefire 但找不到 `RagGatewayImplTest`，以 `No tests matching pattern` 在 common 先失败，跑不到 infra。加 `-Dsurefire.failIfNoSpecifiedTests=false` 让上游空跑继续，才能暴露 infra 的 RED（编译失败）与 GREEN。后续 Task 沿用。

**3. RED 表现 200+`status:0` 非 brief 预期 404**

Task 4 `GET /api/rag/documents` 未实现时，`NoResourceFoundException` 被全局 `GlobalExceptionHandler` 兜底为 `{"status":0,"message":"系统异常"}` + HTTP 200（而非 404）。这是 anitrack 现有全局异常行为（非本任务引入），断言 `$.status==1` 仍失败，TDD RED 判定成立。

**4. final review 3 项 Minor fix**

- `Long.parseLong` 无 null/非数字防御：ingest 链路保证 animeId 是数字，风险低，但单条坏数据（手动写入/迁移脏数据）会致整列表失败。改 try/catch 跳过 + `log.warn` + `filter(Objects::nonNull)`。
- 401 测试：spec 第 7 节要求，实测 `@WebMvcTest` 加载 `WebMvcConfig`(WebMvcConfigurer) + `JwtAuthInterceptor`(HandlerInterceptor)，`JwtTokenProvider` 已 `@MockBean`，不传 JWT→401 直接 GREEN。项目其他 controller 均无 401 测试（项目级缺口，独立处理）。
- 测试覆盖：`RagGatewayImplTest` 补 `get(1)` 字段断言 + null 响应分支测试。

**5. totalEpisodes 都是 0 → 移除字段**

扩字段后列表 `totalEpisodes` 全 0。原因：Bangumi `/v0/subjects` 列表响应不含集数，`listAnimeByYearRating`（PR #7）为省请求用列表响应（不逐条 `getById`）。用户决定不要该字段 → 全链路移除（metadata 不写/不读/前端不显示）。现有 chroma 存量数据残留 `totalEpisodes` 键，新 `listDocuments` 忽略多余键，无破坏。

**6. `read-timeout-ms: 5000` 偏紧致采集失败**

扩字段后重新采集 `ingest_by_criteria?year=2026&minRating=7.0` 报"调用 Bangumi 浏览接口失败"。排查：代理 7897 在跑、curl 代理 Bangumi 200（单页 3.4s）、application-local.yml proxy 配置对、jar 含 local profile。根因：`application.yml` `read-timeout-ms: 5000` 偏紧，Bangumi 经代理单页 3.4s 接近超时，JVM RestClient 可能 >5s。调大 `read-timeout-ms: 60000` + `connect-timeout-ms: 10000` 后采集成功（53 条）。

**7. pageSize 切换不生效**

前端 `pagination={{ pageSize: 10, showSizeChanger: true, pageSizeOptions: [10,20,50] }}`——`pageSize: 10` 是受控固定值，选 20 被覆盖回 10。改 `defaultPageSize: 10`（非受控，antd 内部管理切换）。

**8. 标题/原文名超长换行**

`columns` 标题/原名列加 `ellipsis: true`（antd 5 默认悬浮 tooltip 展示完整内容）。

**9. chroma_data 未被 gitignore**

`rag-service/chroma_data/`（sqlite 数据）`git status` 显示未跟踪，`rag-service/.gitignore`（node_modules/dist/.env）漏了。加 `chroma_data` 行。

## 最终结果

- `mvn clean install`：198 tests 全过（common 57 + infra 35 + app 40 + domain 3 + starter 63），BUILD SUCCESS
- rag-service：vitest 3 passed，`tsc --noEmit` clean
- webui：`tsc --noEmit` clean
- 真实端到端：curl `GET /api/rag/documents` 返回 53 条（animeId/标题/原文名/放送日期/评分 score+评分人数）；浏览器 `/rag/documents` 表格 + 分页(10/20/50 可切换) + 标题搜索 + 省略号悬浮；异常路径（停 rag-service）后端 `status:0` 前端 toast 兜底 + Table 空状态
- final whole-branch review（opus）：Ready to merge，无 Critical/Important
- PR #10（`docs/rag-doc-list` 分支）：含 spec + plan + Task 1-5 + fix + 扩字段 + 移除集数/timeout + UI 调整 + gitignore + README，已 push 等 merge
- commits：`cb233ca`(spec) `b2f5639`(plan) `4cd50e9`(T1) `2b125e5`(T2) `bfdfe1c`(T3) `6de6498`(T4) `d6f5f83`(T5) `ffc6502`(fix 3 Minor) `c0cbef3`(扩字段) `0413c5a`(移除集数+timeout) `5670581`(pageSize/ellipsis/评分width) `8d7a51e`(gitignore) `336ad08`(README)

## 遗留

- **401 测试项目级缺口**：其他 controller（WatchlistController/ReviewController 等）均无 401 测试，独立技术债统一补全，非本 PR 承担
- **totalEpisodes 真实集数**：若要展示集数，需改 `listAnimeByYearRating` 逐条 `getById`（多 N 倍请求）或换接口，当前移除字段
- **后端分页/搜索**：当前前端分页（53 条够用），数据量 >500 时改 chroma `get` 的 `limit`/`offset` + `where` title 过滤做后端分页（spec 第 9 节后续扩展）
- **`/api/rag/chat` LLM 兼容**：DeepSeek 思考模型 `streamUsage` 字段冲突（`reasoning_tokens/completion_tokens already exists`），`AsyncRequestTimeoutException`，独立于列表功能，07-15 遗留待单独排查
