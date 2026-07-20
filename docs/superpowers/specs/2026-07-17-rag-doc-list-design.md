# anitrack RAG 已入库番剧列表 设计

> 日期：2026-07-17
> 范围：为 RAG 知识库新增「查看已入库番剧」能力：后端列表端点 + 前端列表页
> 依赖：`2026-07-15-rag-design.md`（rag-service、Chroma collection `anime_wiki`、`RagGateway`、`POST /api/rag/ingest`）、`2026-07-17-rag-ingest-by-criteria-design.md`（按条件批量采集，已入库 54 部）

## 1. 背景与目标

RAG 知识库已能采集（手动传 ids、按条件批量），但入库后无任何查看入口，用户无法知道 Chroma `anime_wiki` collection 里到底索引了哪些动画。目标：

- 后端新增列表端点，返回已入库番剧的 `animeId` + 标题
- 前端新增列表页，表格分页 + 标题搜索
- 不改 ingest 链路（metadata 现有 `animeId`+`title` 已够），不引入新 Maven 依赖

## 2. 关键决策

| 维度 | 决策 |
|---|---|
| 数据来源 | rag-service 加 `GET /documents` 路由（Chroma client get metadatas）→ Java 透传 → 前端页。不绕过 rag-service 直连 Chroma（违背「rag-service 承载引擎」约定 + 暴露端口） |
| 返回字段 | 只 `animeId` + `title`（metadata 现有，不改 ingest）。不扩 metadata 加评分/放送日期（改动会扩大到 ingest 链路 + 重新采集，YAGNI） |
| 分页 | 前端分页。后端一次返回全部（chroma get 带 `limit` 上限兜底，如 1000），前端 antd Table pagination + 标题前端 filter。当前 54 条，将来上百上千再考虑后端分页 |
| 鉴权 | Java controller 复用全局 JWT；Java→rag-service 用现有 `X-Internal-Token` |
| rag-service 取 metadatas | 实现时确认：langchain Chroma 底层 collection 对象的 `get`，或显式装 `chromadb` npm 包用 HttpClient（已通过 `@langchain/community` 间接依赖） |

## 3. 数据流

```
前端 Table 加载
   ▼ GET /api/rag/documents (Authorization: Bearer <JWT>)
RagIngestController
   ▼
RagApplication.listDocuments()
   ▼
RagGatewayImpl.listDocuments()
   ▼ GET rag-service /documents (X-Internal-Token)
getVectorStore() → Chroma get(include=['metadatas'])
   ▼ chroma 原始 {ids:[...], metadatas:[{animeId,title}...]}
rag-service 转换为 {documents:[{animeId,title}]}
   ▼
Java 解析 → List<RagDocumentSummary>
   ▼ ResponseResult<List<RagDocumentSummaryResponse>>
前端 Table 前端分页 + 标题搜索
```

## 4. 接口契约

### `GET /api/rag/documents`

```
Header: Authorization: Bearer <JWT>
Response: ResponseResult<List<RagDocumentSummaryResponse>>
  {"status":1,"message":null,"data":[{"animeId":1,"title":"第一次的亲密接触"},...]}
```

### rag-service `GET /documents`（内部）

```
Header: X-Internal-Token: <token>
Response: {"documents":[{"animeId":"1","title":"..."},...]}
```

注：chroma 的 id 是 string，rag-service 返回的 `animeId` 为字符串；Java `RagDocumentSummary.animeId` 为 `Long`，解析时 `Long.parseLong`。

## 5. 落点（按 DDD 分层，不新增 Maven 模块）

| 层 | 改动 | 说明 |
|---|---|---|
| rag-service `src/routes/documents.ts` | 新建路由 `GET /`（prefix `/documents`） | 调 `getVectorStore()` 取 Chroma store，get metadatas+ids，返回 `{documents:[{animeId,title}]}`；`index.ts` register |
| domain/rag/gateway `RagGateway` | 加 `List<RagDocumentSummary> listDocuments()` | |
| domain/rag/model | 新值对象 `RagDocumentSummary`（animeId:Long, title:String） | |
| infra/rag/gateway `RagGatewayImpl` | 实现 listDocuments：RestClient GET `/documents`，带 `X-Internal-Token`，解响应 | 复用 `restClientBuilder` + `RagProperties` |
| application `RagApplication` | `listDocuments()` 委托 gateway | |
| starter `RagIngestController` | 加 `GET /documents` | 复用 `/api/rag` + 全局 JWT，返回 `ResponseResult<List<RagDocumentSummaryResponse>>` |
| webui `src/services/rag.ts` | 加 `listDocuments()` | `request<ApiResult<...>>`，解包 `res.data` |
| webui `src/pages/rag/documents.tsx` | 新页：antd `Table`（animeId/标题列）+ 前端 `pagination` + 标题前端 filter | |
| webui `.umirc.ts` + `layouts/index.tsx` | 加 `/rag/documents` 路由 + 菜单「已入库」项（放「RAG 采集」后） | `startsWith` 匹配，更长 key 放前避免误高亮 |

## 6. 错误处理

- Chroma 不可达 / collection 不存在：rag-service 返回 5xx，Java gateway 异常抛出，由全局异常处理器转错误响应（沿用 ingest 同模式，不特殊兜底）。
- 前端：全局 errorHandler 已 `message.error` 兜底，Table 空状态展示（loading/empty）。

## 7. 测试

- rag-service `documents.test.ts`：mock chroma client 返回固定 metadatas，验证响应结构。
- Java `RagGatewayImplTest` 加 `listDocuments`：MockWebServer 模拟 `/documents` 响应，验证解析 + `X-Internal-Token` header。
- Java `RagIngestController` WebMvcTest 加 `GET /documents`：mock `RagApplication`，验证响应结构 + JWT 401。
- webui：不写测试（沿用现状）。

## 8. 验证

- 启动 Chroma（8100）+ rag-service + 后端 + webui
- 浏览器 `/rag/documents` → 表格列出 54 部（2026≥7.0 入库的），分页 + 标题搜索可用
- `curl GET /api/rag/documents`（带 JWT）→ `data` 含 animeId+title 列表
- rag-service 不在 / Chroma 无 collection → 前端 Table 错误提示（全局兜底）

## 9. 后续扩展（不做）

- 后端分页/搜索：数据量大时改 chroma get 的 `limit`/`offset` + `where` title 过滤
- 返回评分/放送日期：需扩 ingest metadata 字段 + 重新采集
- 删除/管理已入库条目：chroma delete by id
