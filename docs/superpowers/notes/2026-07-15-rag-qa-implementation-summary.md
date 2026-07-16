# anitrack RAG 番剧百科问答 实施记录

日期：2026-07-15
设计文档：`docs/superpowers/specs/2026-07-15-rag-design.md`
计划文档：`docs/superpowers/plans/2026-07-15-rag-qa.md`

## 目标

为 anitrack 新增基于番剧百科的 RAG 问答页面：用户在前端提问，前端用 Vercel ai-sdk 流式展示
回答；Java 后端做 JWT 鉴权与流式透传；独立 rag-service（Node/TS + Fastify + LangChain.ts +
Chroma）承载 RAG 引擎（embedding + 向量检索 + LLM 流式生成）；数据采集由 Java 侧复用
`BangumiGateway` 拉取番剧详情、组装文本块后 POST 给 rag-service 入库。前置任务先把 `Anime`
领域模型全量对齐 Bangumi Subject，并为 `BangumiGateway` 增加 `getById`。

## 做了什么

按 6 个阶段 16 个 Task 逐步实施（Subagent-Driven Development：每个 Task 一个全新子代理实现 +
一个独立子代理审查，TDD，每个 Task 独立 commit；在 `feat/rag-qa` 分支上实施，暂未合并回 main）：

### 阶段一：Anime 领域模型全量对齐 Bangumi Subject（Task 1-5）

1. 领域值对象 `Rating`/`Collection`/`AnimeTag`/`AnimeImages`/`Infobox`/`InfoboxItem`
2. `Anime` 扩展 13 个字段 + 全字段 `fromBangumi`/`reconstitute` 工厂方法（保留旧签名作重载）
3. `BangumiSubjectDTO` 扩展全字段 + 4 个新 DTO（`Rating`/`Collection`/`Tag`/`Infobox`，
   infobox 的 value 多态用 `@JsonProperty("value")` setter 处理 String 或 List<{k,v}>）+
   `BangumiConverter` 全字段映射
4. JSON `TypeHandler` 基类 `JsonTypeHandler<T>` + 6 个子类（Rating/Collection/AnimeTagList/
   InfoboxList/AnimeImages/StringList），复杂字段以 JSON 列持久化
5. `AnimePO` 全字段 + `AnimeMapper.xml` resultMap/insert/update 指定 typeHandler +
   infra `AnimeConverter`（MapStruct）全字段映射 + Flyway `V5` 迁移（ALTER TABLE 加 JSON 列）

### 阶段二：BangumiGateway.getById（Task 6）

6. `BangumiGateway` 接口加 `getById(Long)`，`BangumiGatewayImpl` 用 RestClient GET
   `/v0/subjects/{id}`，404 返回 null，其他异常包装为 `BangumiApiException`；MockWebServer
   单测覆盖正常路径与 404

### 阶段三：rag-service（Fastify + LangChain.ts + Chroma）（Task 7-9）

7. `rag-service/` 脚手架：package.json（type: module）、tsconfig、config.ts（`required()`
   环境变量校验）、index.ts（Fastify + /health）
8. RAG 核心模块：`embeddings.ts`（硅基流动 OpenAI 兼容）、`vectorStore.ts`（Chroma
   `fromExistingCollection` + `addDocuments`，id=metadata.animeId）、`retriever.ts`、
   `chain.ts`（`streamAnswer` 生成器，`RunnableSequence` 组装 prompt+retriever+model+
   `StringOutputParser`，`chain.stream()` 逐 token yield）
9. `plugins/auth.ts`（onRequest hook 校验 `X-Internal-Token`）、`plugins/sse.ts`（纯文本流，
   非 `data:` 帧，匹配前端 `TextStreamChatTransport`）、`routes/ingest.ts`、`routes/query.ts`
   （SSE 流式问答，ChatOpenAI streaming）、index.ts 挂载

### 阶段四：Java 侧 RAG 接入（Task 10-13）

10. domain 层 `rag` 上下文：`RagGateway` 接口（`ingest`/`streamQuery` 返回 `Stream<String>`）、
    `RagQuery`/`RagDocument` 值对象
11. infra 层 `RagProperties`（`@ConfigurationProperties(prefix="anitrack.rag")`）+
    `RagClientConfig` + `RagGatewayImpl`（ingest 用 RestClient，streamQuery 用 JDK HttpClient
    解析纯文本流为 `Stream<String>`）
12. application 层 `RagApplication`：`ingestAnimeWiki`（逐个 getById，失败跳过记 ERROR，
    组装文本块内联在 application 避免反向依赖 infra）+ `streamChat` 委托 `streamQuery`
13. starter 层 `RagChatController`（`StreamingResponseBody` 流式透传，produces=text/plain）+
    `RagIngestController`（手动触发采集）+ `RagChatReq`（`@NotBlank message`）+ application.yml
    追加 `anitrack.rag` 配置

### 阶段五：前端问答页（Task 14）

14. webui 新增 `/rag` 页面，用 ai-sdk `useChat` + `TextStreamChatTransport`，
    `prepareSendMessagesRequest` 动态构造 `{message}` body（修正 brief 的闭包 bug），
    headers 函数化注入 JWT，`m.parts` 适配 ai-sdk v5，路由注册 + 侧边栏菜单项

### 阶段六：docker-compose 集成与端到端验证（Task 15-16）

15. `rag-service/Dockerfile`（多阶段构建）+ docker-compose 新增 chroma 与 rag-service 服务 +
    `.env.example` 扩展 + `.dockerignore`
16. 端到端验证（本地非 docker 运行）

全部完成后做了一次全分支最终 review（sonnet 模型），发现 2 项 Important（Docker 部署相关），
修复并复审通过后，又追加修复了 3 项用户体验/文档 Minor。分支保留，未合并。

## 遇到的问题及解决

**1. brainstorming 阶段确认集成形态与模型来源**

RAG 要用 langchain，但项目后端是 Java——langchain 是 Python/TS 生态。与用户确认后定为：
独立 Node/TS 服务承载 LangChain.ts（用户指定用 TS 版而非 Python），Java 后端 HTTP 调用，
前端走 Java 后端。LLM/embedding 用硅基流动（OpenAI 兼容接口，后续可切换国内模型），向量库
用 Chroma（Docker 自部署）。这些决策在 spec 与计划里全部环境变量化，代码不硬编码。

**2. BangumiGateway 只有 search 没有 getById，且 Anime 字段不全**

调研发现现有 `BangumiGateway` 只有 `search(keyword)`，没有按 ID 拉详情的能力；`Anime` 领域
模型也缺 tags/rating/collection/infobox 等字段。与用户确认后决定：前置任务把 `Anime` 全量
对齐 Bangumi Subject（19 个字段），并为 `BangumiGateway` 加 `getById`。字段全量对齐涉及
domain 值对象 → Anime → DTO/Converter → PO/XML/Flyway 五处同步，复杂结构字段（tags/rating/
collection/infobox/images）用 MySQL JSON 列 + 自定义 `TypeHandler` 持久化（项目 persist-rules
第五节明确允许）。

**3. brief 多处设计缺口在实现期发现并经授权修复**

- Task 4：brief 的 `JsonTypeHandler` 未重写 `setParameter`，MyBatis `BaseTypeHandler` 在
  `parameter==null && jdbcType==null` 时抛异常；且 Task 1 值对象用 `@Getter @AllArgsConstructor`
  + final 字段，无默认构造器，`new ObjectMapper()` 无法反序列化。经授权修复：基类重写
  `setParameter`（null 走 `Types.OTHER`）+ 6 个值对象补 `@JsonCreator`/`@JsonProperty`。领域
  层因此引入 jackson-annotations 依赖——审查确认可接受（纯标记注解、Lombok 先例、非 Spring 类型）。
- Task 8：brief 的 `streamAnswer` 标注 `model: BaseChatModel`，但测试传异步生成器函数，
  `tsc --noEmit` 报 TS2345。经授权修复：放宽为联合类型 + `as BaseChatModel` 断言（运行时
  RunnableSequence 正确处理两种输入）。
- Task 11：brief 的单测用 `new RagGatewayImpl(url, "token")` 但实现注入 `RagProperties +
  RestClient.Builder`——构造器不一致。coordinator 预检发现并在 brief 里标注修正方向。
- Task 13：brief 里 RagChatController 先写 SseEmitter 版又说改用 StreamingResponseBody。
  coordinator 预检标注以 StreamingResponseBody 为准，忽略 SseEmitter 段。
- Task 13：brief 的 StreamingResponseBody import 路径漏 `.annotation`，编译报错后修正。

**4. 审查循环里发现并修复的 Important 问题**

- Task 3：infobox value 为 List<{k,v}> 的多态解析分支未被测试覆盖。补 `toDomain_maps_infobox_
  value_as_list` 测试。
- Task 9：query 路由 `topK ?? 4` 硬编码导致 `TOP_K` 环境变量失效。改为 `topK ??
  (app as any).defaultTopK`，index.ts decorate `defaultTopK = config.topK`。
- Task 11：`streamQuery` 资源泄漏（InputStream/HttpClient 未关闭）+ JSON 手动转义不完整
  （null NPE + 漏 \r\t）。修复：`BufferedReaderLines` 实现 `AutoCloseable` + `Stream.onClose`
  串联 + HttpClient 提为字段复用；删除 `escape()` 改用 `ObjectMapper.writeValueAsString`；
  topK fallback 走 `properties.getDefaultTopK()`；`hasNext` 的 IOException 改 `log.error`。
- Task 12：`fetchSafely` 失败跳过分支未被测试覆盖；`toDocument` 评分分支 `rating.getTotal()`
  为 null 会拼 "null人"。补失败跳过测试 + total null 兜底。
- Task 15：Dockerfile `npm ci --omit=dev` 跳过 devDependencies 导致 `npm run build`（tsc）
  失败；`package-lock.json` 未提交导致 `npm ci` 报错。改多阶段构建（builder 全量安装+编译，
  运行阶段仅生产依赖）+ 提交 lockfile。

**5. 本机默认 Maven 环境不可用**

沿用此前 Phase 记录里的同一问题：默认 `PATH` 命中的 mvn 绑定 JDK 8。全程改用
`JAVA_HOME=.../temurin-17.jdk` + `PATH=/opt/homebrew/opt/maven/bin:$PATH`（Maven 3.9.16）。
rag-service 侧 npm install 需 `--legacy-peer-deps`（@langchain/community 可选 peer
better-sqlite3 与 typeorm 冲突）。

**6. 端到端验证：Bangumi API 被墙 + 本地无 docker**

本机没有安装 Docker，但 MySQL 8.0.46 + chromadb 1.5.9 + Node 24 + Java 17 齐全，改为本地
非 docker 运行：chroma run（pip 安装）+ rag-service（tsx watch）+ Java 后端（local profile
jar 启动）。

首次验证时 Bangumi 采集全部超时——`api.bgm.tv` 解析到 `157.240.21.9`（Meta CDN 段），国内
直连被墙。查到 macOS 系统代理 `127.0.0.1:7897`（Clash），经代理 curl 返回 200。但已启动的
Java 后端没带代理参数。重启 Java 后端加 `-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7897
-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7897` 后，采集 `bangumiIds=1,2,3` 返回 3（真实
数据全拉到入库），全链路问答"介绍一下 1 号条目"流式生成准确回答（书籍《第一次的亲密接触》、
作者蔡智恒、评分 7.9 等真实数据）。验证用 API key 写入本地 `.env`（.gitignore 忽略，未提交）。

**7. MySQL 启动需 sudo**

MySQL 是官方 DMG 安装（`/usr/local/mysql`），`mysql.server start` 因 data 目录权限需 root。
sudo 在当前环境无法交互输入密码，请用户通过系统设置界面启动。

## 最终结果

- `mvn test`：5 模块全过，61 个测试 0 失败（本次新增覆盖 domain 值对象/Anime/BangumiConverter/
  JsonTypeHandler/AnimeConverter/BangumiGatewayImpl/RagGatewayImpl/RagApplication/RagChatController）
- rag-service：vitest 全过（chain.test.ts + query.test.ts），`tsc --noEmit` 无错误
- 真实端到端联调已完成（带 Bangumi 代理）：采集入库 + 全链路流式问答 + 错误路径（无 JWT→401、
  空 message→400、采集失败跳过）全部符合预期
- 最终全分支 review 通过，2 项 Important（Dockerfile + lockfile）已修，另修 3 项 Minor
  （Controller 错误透传 + 前端 error 展示 + spec 6.2 文档同步）
- RAG 功能全部完成，27 个 commit（`15bfa2a..c49f71f`），分支 `feat/rag-qa` 保留，本地未合并、
  未 push
- 遗留 7 项 Minor（query.ts `as any`、ingest RestClient 复用、响应末尾多 `\n`、值对象 equals、
  500 异常路径测试、ControllerTest 流式内容断言、领域层 record 重构）不影响功能，可后续处理
