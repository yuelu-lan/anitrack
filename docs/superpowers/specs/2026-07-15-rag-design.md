# anitrack RAG 问答功能设计

> 日期：2026-07-15
> 范围：为 anitrack 增加基于番剧百科的 RAG 问答页面

## 1. 背景与目标

在现有追番管理项目基础上，新增一个 RAG 问答页面，用户可就番剧百科内容提问，前端以流式（打字机）方式展示回答。

首版范围限定为**番剧百科问答**，知识库数据源为 Bangumi API 拉取的番剧资料。用户追番/评论等结构化数据留作后续扩展。

## 2. 关键决策

| 维度 | 决策 |
|---|---|
| 集成形态 | 独立 Node.js/TypeScript 服务（LangChain.ts）承载 RAG 引擎，Java 后端通过 HTTP 调用，前端统一走 Java 后端 |
| 语言/框架 | rag-service 使用 TypeScript + Fastify + LangChain.ts |
| LLM/embedding | 硅基流动（SiliconFlow，OpenAI 兼容接口），后续可在国内大模型间切换 |
| 向量库 | Chroma（Docker 自部署） |
| 数据采集 | Java 侧复用现有 `BangumiGateway` 拉取并组装文本块，POST 给 rag-service 入库 |
| 输出方式 | 全链路流式（SSE），前端使用 Vercel ai-sdk |
| 服务间鉴权 | Java → rag-service 共享密钥（`X-Internal-Token` header） |
| 用户鉴权 | 前端 → Java 复用现有 JWT |

## 3. 整体架构

### 链路总览

```
webui (React + ai-sdk)
   │ SSE
   ▼
Java anitrack-starter ── RagChatController（JWT 鉴权 + 流式透传）
   │                         ▲
   │ SSE (X-Internal-Token)  │ BangumiGateway 拉取/清洗文本块
   ▼                         │
rag-service (Node/TS + Fastify)
   ├─ embedding (硅基流动)
   ├─ Chroma 向量库 (collection: anime_wiki)
   └─ LangChain.ts 流式 RAG chain ──▶ 硅基流动 LLM
```

### 三个职责单元

| 单元 | 归属 | 职责 |
|---|---|---|
| 数据采集 | Java 侧（infrastructure） | 复用 `BangumiGateway` 拉番剧简介/标签 → 组装文本块 → 调 rag-service `/ingest` 入库 |
| RAG 引擎 | 新增 `rag-service/`（仓库根目录独立子项目） | embedding + Chroma 读写 + 流式生成，暴露 `/query`(SSE) 与 `/ingest` 两个接口 |
| 问答编排 | Java 侧（starter + application） | `RagChatController` 鉴权用户、调 rag-service `/query`、流式透传给前端 |

### 模块落点

- **Java 侧不新增 Maven 模块**，RAG 相关代码落入现有 DDD 分层（详见第 7 节）。
- **`rag-service/` 是独立 Node/TS 项目**，与 `webui/` 同级，有自己的 `package.json`、`tsconfig`、Dockerfile。
- **Chroma** 作为新服务加入现有 `docker-compose.yml`。

## 4. 数据流

### 4.0 前置：领域模型扩展

RAG 采集需要更完整的番剧百科字段，因此先做两处扩展（独立于 RAG 链路，是 RAG 的前置任务）：

1. **`BangumiGateway` 新增 `getById`**：调用 Bangumi API `GET /v0/subjects/{subject_id}` 拉取单条目详情。现有 `search(keyword)` 保留。
2. **`Anime` 领域模型全量对齐 Subject**：补齐 Bangumi Subject 的全部字段，新增值对象 `Rating`、`Collection`、`AnimeTag`、`AnimeImages`、`Infobox`。同步扩展 `AnimePO`、`AnimeMapper.xml`、建表迁移 `V5`、`BangumiConverter`。

Anime 扩展后字段（对齐 Subject v0）：

| 字段 | 类型 | 说明 |
|---|---|---|
| id / bangumiId | Long | 内部主键 / Bangumi 条目 ID |
| type | Integer | 条目类型（动画/书籍/游戏等） |
| titleOriginal / titleCn | String | name / name_cn |
| summary | String | 简介 |
| nsfw / locked / series | Boolean | 标记位 |
| airDate | String | date（YYYY-MM-DD） |
| platform | String | 平台（TV/Web 等） |
| coverUrl | String | images.large 作为封面 |
| images | AnimeImages | 五种尺寸封面 |
| eps / totalEpisodes / volumes | Integer | 话数 / 章节数 / 册数 |
| metaTags | List<String> | 维基维护的 meta_tags |
| tags | List<AnimeTag> | 用户标签（name + count） |
| rating | Rating | 评分（score/rank/total/count 分布） |
| collection | Collection | 收藏统计（wish/collect/doing/onHold/dropped） |
| infobox | List<Infobox> | 维基信息框键值对 |

### 4.1 数据采集（离线，非用户请求路径）

```
[定时任务 / 手动触发]
   ▼
RagApplication.ingestAnimeWiki(animeIds)
   ▼
BangumiGateway.getById(bangumiId) 拉取单条目详情（summary/tags/rating/infobox 等全字段）
   ▼
组装为 LangChain Document 结构
   （pageContent = 拼接的百科文本；metadata = {animeId, title, source:"bangumi"}）
   ▼
RagGatewayImpl.ingest(blocks) ──HTTP POST──▶ rag-service /ingest
   ▼
rag-service: embedding(硅基流动) → 写入 Chroma "anime_wiki"
```

**切片策略**：首版一部番剧 = 一个 Document，不做二次切片。若后续某些番剧简介过长（超出 embedding 上下文），再引入 `RecursiveCharacterTextSplitter` 兜底。

**增量更新**：metadata 带 `animeId`，入库前去重（同 animeId 覆盖）。首版可全量重建，增量留作后续。

### 4.2 问答查询（用户请求路径，流式）

```
webui: ai-sdk useChat() 发起 SSE 请求
   ▼ POST /api/rag/chat (Authorization: Bearer <JWT>)
RagChatController
   ├─ 校验 JWT（复用现有鉴权）
   ├─ 组装 RagQuery（question, 可选 animeId 过滤）
   ▼
RagGatewayImpl.streamQuery() ──SSE (X-Internal-Token)──▶ rag-service /query
   ▼
rag-service:
   ├─ 用问题 embedding 检索 Chroma top-k（首版 k=4）
   ├─ 组装 prompt（检索片段 + 问题）
   ├─ LangChain.ts 流式 chain 调硅基流动 LLM
   └─ 逐 token SSE 返回
   ▼ (Java 逐 chunk 透传)
webui: ai-sdk 渲染打字机效果
```

**关键点**：
- Java 后端是 SSE 透传管道：用 `SseEmitter` 把 rag-service 的每个 chunk 原样转发给前端，不在 Java 侧缓冲完整回答。
- 错误透传：rag-service 返回错误事件时，Java 转成前端可识别的错误 SSE 事件。
- top-k、prompt 模板放在 rag-service 内部配置。

**数据流边界**：
- Java 侧只搬运"问题进、流式回答出"，不做任何 LLM/embedding 逻辑。
- rag-service 只接收"已清洗文本块"（采集）和"问题"（查询），不直接碰 Bangumi。

## 5. rag-service 内部结构

```
rag-service/
├── package.json            # @langchain/core, @langchain/community, @langchain/openai, chromadb, fastify
├── tsconfig.json
├── Dockerfile
├── .env.example
└── src/
    ├── index.ts            # Fastify 实例创建 + register 各 plugin
    ├── config.ts           # 读取环境变量，集中管理（baseURL、model 名等可切换）
    ├── plugins/
    │   ├── auth.ts         # onRequest hook，校验 X-Internal-Token
    │   └── sse.ts          # SSE 流式响应工具（reply.raw.write 封装）
    ├── routes/
    │   ├── ingest.ts       # 导出 FastifyPlugin，注册 POST /ingest
    │   └── query.ts        # 导出 FastifyPlugin，注册 POST /query (SSE)
    └── rag/
        ├── embeddings.ts   # 硅基流动 embedding 客户端（OpenAI 兼容封装）
        ├── vectorStore.ts  # Chroma collection "anime_wiki" 初始化与复用
        ├── retriever.ts    # top-k 检索封装
        └── chain.ts        # 流式 RAG chain（prompt + 检索 + LLM 流式生成）
```

目录贴合 Fastify 的 plugin/hook 心智模型：路由以 `FastifyPlugin` 形式 `register` 挂载，鉴权用 `onRequest` hook（而非 Express 式 middleware），SSE 响应通过 `reply.raw.write()` 并在 `plugins/sse.ts` 统一封装。

## 6. 接口契约

### 6.1 `POST /ingest` — 批量入库

```
Header: X-Internal-Token: <共享密钥>
Body: {
  documents: [
    { pageContent: string, metadata: { animeId: string, title: string } }
  ]
}
Response: 200 { ingested: number }
```

幂等：同 `animeId` 覆盖（Chroma 按 metadata 删除旧的再写入，或首版全量重建）。

### 6.2 `POST /query` — 流式问答

```
Header: X-Internal-Token: <共享密钥>
Body: { question: string, topK?: number }
Response: text/plain（纯文本流，逐 token 输出，匹配前端 ai-sdk TextStreamChatTransport）
  你好番剧...
  （出错）[ERROR] ...
```

说明：采用纯文本流而非 `data:` SSE 帧，是为了直接对接前端 ai-sdk 的 `TextStreamChatTransport`（该 transport 解析纯文本流）。Java 后端透传时保持同样的纯文本格式。

## 7. 模型与库可配置

硅基流动走 OpenAI 兼容接口，切换国内模型只改环境变量，无需改代码：

```
LLM_BASE_URL=https://api.siliconflow.cn/v1
LLM_MODEL=<模型名>
EMBEDDING_BASE_URL=https://api.siliconflow.cn/v1
EMBEDDING_MODEL=BAAI/bge-large-zh-v1.5
```

LangChain.ts 的 `ChatOpenAI` / `OpenAIEmbeddings` 支持自定义 `baseURL`，无需写新适配器。

## 8. Java 侧落点（按 DDD 分层，不新增 Maven 模块）

| 层 | 新增内容 | 说明 |
|---|---|---|
| `domain/rag` | `RagGateway` 接口、`RagQuery`/`RagChunk` 值对象 | 领域层定义防腐接口，不依赖任何 HTTP 类型 |
| `infrastructure/rag/gateway` | `RagGatewayImpl`（`RestClient` 调 rag-service）、`RagProperties`（配置 baseURL/token） | 实现 `RagGateway`，处理共享密钥 header、SSE 流解析 |
| `infrastructure/rag/ingest` | `AnimeWikiIngestor`（调 `BangumiGateway` 拉取+组装文本块） | 采集编排，复用现有 ACL |
| `application` | `RagApplication`（编排采集 + 流式查询透传） | 应用服务，事务/编排边界 |
| `starter` | `RagChatController`（SSE 透传）、`RagIngestController`（手动触发采集） | Web 层 |

`RagGateway.streamQuery()` 返回流式 chunk（基于 Spring MVC，不引入 WebFlux；用 `Stream<String>` 或回调推送到 `SseEmitter`），`RagChatController` 用 `SseEmitter` 逐 chunk 透传前端。

## 9. 前端集成（webui）

- 新增 `webui/src/pages/rag/` 问答页。
- 用 Vercel `ai-sdk` 的 `useChat`，endpoint 指向 Java 后端 `/api/rag/chat`。
- Java 后端需支持 ai-sdk 期望的 SSE 数据格式（`data: {"token":"..."}\n`），与 rag-service 输出格式对齐。
- 鉴权：请求带现有 JWT，复用 webui 已有请求拦截器。

## 10. 错误处理

| 场景 | 处理 |
|---|---|
| rag-service 不可达 | `RagGatewayImpl` 捕获，转成 SSE 错误事件透传前端，不挂连接 |
| LLM/embedding 超时（硅基流动） | rag-service 设超时，返回 `{"error":"..."}` 事件 |
| Chroma 未初始化/无数据 | 检索返回空，prompt 仍走 LLM（提示无参考资料），不阻断 |
| JWT 校验失败 | Java 侧标准 401，不进入 RAG 链路 |
| 采集失败 | 记录 ERROR 日志（带 animeId 上下文），不中断整体采集批次 |

## 11. 测试策略

- **rag-service**：单测 retriever/chain 的 prompt 组装；集成测试用 mock LLM 验证 `/query` SSE 流与 `/ingest` 写入。
- **Java 侧**：`RagGatewayImpl` 用 MockWebServer 验证 SSE 解析与 token header；`RagChatController` 用 `@WebMvcTest` + MockMvc 验证鉴权与透传；`RagApplication` 单测验证采集编排。
- 首版不写端到端（需真实 LLM key），留作手动验证。

## 12. 后续扩展（首版不做）

- 用户追番/评论数据接入：结构化查询（看过/评分/统计类）走 Java 查 MySQL，语义类问题走 RAG，由 Agent 路由分流。
- 增量采集与定时任务。
- 向量库迁移到 Qdrant（若需更强的元数据过滤）。
- 检索质量优化（重排序、混合检索）。
