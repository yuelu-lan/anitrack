# anitrack RAG 按条件批量采集 实施记录

日期：2026-07-17
设计文档：`docs/superpowers/specs/2026-07-17-rag-ingest-by-criteria-design.md`
分支：`feat/rag-ingest-by-criteria`（后端，PR #7）+ `feat/webui-rag-ingest`（前端，PR #8）

## 目标

RAG 基础设施已就绪（rag-service + Chroma + `/api/rag/ingest`），但采集只能手动传 `bangumiIds`、无 UI 入口。新增「一键嵌入 2026 评分≥7.0 动画」能力：后端按年份+评分批量采集端点 + 前端采集管理页。

## 做了什么

两个 PR 合并 main，附 README 修正：

### PR #7 后端 `feat/rag-ingest-by-criteria`

- `BangumiGateway.listAnimeByYearRating(year, minRating)`：调 Bangumi 正式接口
  `GET /v0/subjects?type=2&year&sort=rank&limit=50&offset=N` 分页拉完全部年度动画
  （响应 `data` 是完整 `Subject` 含 `rating`），客户端过滤 `rating.score≥minRating`
- `RagApplication.ingestByCriteria`：直接用搜索结果 `Anime` 组装 `RagDocument`
  （复用 `toDocument`），省掉 `ingestAnimeWiki` 的二次 `getById`
- `RagIngestController` 加 `POST /api/rag/ingest_by_criteria?year=&minRating=`
- `BangumiProperties` + `BangumiClientConfig`：RestClient 可选代理
  （`proxyHost`/`proxyPort`，非空则 `setProxy`），`application-local.yml(.example)` 配 `127.0.0.1:7897`
- 单测 2 个：`BangumiGatewayImplTest`（分页+过滤）、`RagApplicationTest`（编排）
- 优化：`limit` 20→50（OpenAPI `maximum=50`，分页 12 次→5 次）

### PR #8 前端 `feat/webui-rag-ingest`

- `src/pages/rag/ingest.tsx`：`year` + `minRating` `InputNumber` + 开始采集 `Button` + 入库数 `Alert`
- `src/services/rag.ts`：`ingestByCriteria`，`params` query（后端 `@RequestParam`）+
  `timeout: 120000`（覆盖全局 `10000`）
- `.umirc.ts` 加 `/rag/ingest` 路由；`layouts/index.tsx` 菜单加「RAG 采集」项
  （放「番剧问答」前，`startsWith` 匹配避免误高亮）

### 附带

- README 演示密码 `password123` → `111111`（`R__seed_demo_data.sql:2` 注释明确
  「密码均为明文 111111 的 BCrypt 哈希」，README 文档与代码不一致），commit `db396c0`

## 遇到的问题及解决

**1. Bangumi 接口选择：实验性 vs 正式**

`POST /v0/search/subjects` 支持 `type`+`air_date`+`rating` 一次过滤（6 页 114 部，最省请求），
但 OpenAPI 标注「实验性 API，schema 和行为可能随时改动」。与用户确认后改用
`GET /v0/subjects`（正式接口，支持 `type`+`year`+`sort`，响应含 `rating`）分页拉完 + 客户端
过滤。代价：多拉无评分/低分条目（2026 共 230 部过滤剩 54），但走正式接口更稳。

**2. Java RestClient 不走系统代理**

`SimpleClientHttpRequestFactory` 默认不读 Clash http 代理（7897），Bangumi 国内不可达。加可选
`proxyHost`/`proxyPort` 配置（`BangumiProperties` + `BangumiClientConfig` `setProxy`），留空
不走代理兼容生产。`application-local.yml` 配 `127.0.0.1:7897`。区别于 07-15 RAG 验证时用
JVM `-Dhttp.proxyHost` 命令行参数，这次做成配置项更通用。

**3. 前端 request 默认 timeout 10s**

`app.ts` 全局 `timeout: 10000`，ingest ~40s 必超时。service 层 per-request `timeout: 120000`
覆盖。

**4. 演示密码文档错**

README 写 `password123`，但 `R__seed_demo_data.sql:2` 注释明确「密码均为明文 111111 的 BCrypt
哈希」。实测 python `bcrypt.checkpw`：`password123` 不匹配、`111111` 匹配。修正 README。

**5. Bangumi OpenAPI 文档站抓不到 + 字段名探测**

`bangumi.github.io/api` 是 Swagger SPA，WebFetch 只能拿到静态 HTML（「Bangumi API」几个字）。
改读 `github.com/bangumi/api` 仓库 `open-api/v0.yaml` 源文件，确认 filter 字段：
`air_date`/`rating` 用 `">=YYYY-MM-DD"`/`">=7"` 比较运算符字符串格式，`limit` `maximum=50`
（实测 100 也能返回但超文档上限，不冒险）。最初用 curl 实测探测字段名（`date` 被 Bangumi 忽略
返回默认全量、`air_date` 需带 `>=`/`<=` 格式才生效），后才定位到 yaml 源。

**6. 本机 Maven 环境**

沿用 07-15 记录：默认 `mvn` 绑定 JDK 8（3.6.0 不满足 Spring Boot 3.5.3 插件要求 3.6.3+，
`maven-clean-plugin:3.4.1`/`maven-install-plugin:3.1.4` 抛 `PluginIncompatibleException`），
改用 homebrew `/opt/homebrew/bin/mvn`（3.9.16）+ temurin-17。

**7. 前端验证：wait_for 误匹配 + uid 失效**

chrome-devtools 验证时，`wait_for ["采集完成","入库"]` 的「入库」匹配了顶部 info Alert 的
「embedding 入库」（一直存在），没真正等采集完成。改用精确「采集完成」匹配结果 Alert/toast。
另热更新后旧 uid 失效，需重新 `take_snapshot` 拿采集按钮 uid。

## 最终结果

- `mvn clean install`：61 tests 全过（含 2 个新单测），BUILD SUCCESS
- 实测 `POST /api/rag/ingest_by_criteria?year=2026&minRating=7.0` → 入库 54 部（~40s），
  Chroma `anime_wiki` collection dimension=2560（Qwen3-Embedding-4B）
- 浏览器端到端：`http://localhost:8000` 登录 `alice`/`111111` → 侧栏「RAG 采集」→
  填 2026/7.0 → 点「开始采集」→ ~40s 后 success Alert「采集完成，入库 54 部番剧百科」+ toast
- PR #7、#8 合并 main（#8 依赖 #7：先合 #7，`update-branch` 让 #8 含 #7 后再合 #8）
- commit：`3099607`（#7 主体）+ `a94f407`（#7 limit 优化 20→50）+ `82f2102`（#8）+
  `db396c0`（README 密码修正）

## 遗留

- `/api/rag/chat` 问答端到端仍受阻于 rag-service 的 LLM 兼容（DeepSeek 思考模型 `streamUsage`
  字段冲突，`reasoning_tokens/completion_tokens already exists` warning，后端
  `AsyncRequestTimeoutException`）。`b335d76` 修过一次未彻底，独立于按条件采集，待单独排查。
