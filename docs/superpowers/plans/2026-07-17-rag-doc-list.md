# RAG 已入库番剧列表 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增「查看 Chroma `anime_wiki` collection 已入库番剧」能力：rag-service 加 `GET /documents` 路由 → Java 四层透传 → webui 列表页（前端分页 + 标题搜索）。

**Architecture:** rag-service 用 langchain `Chroma` store 暴露的底层 `collection.get({include:["metadatas"],limit:1000})` 取 ids+metadatas，转成 `{documents:[{animeId,title}]}`。Java `RagGatewayImpl` 用 RestClient GET `/documents`（带 `X-Internal-Token`）解析为 `List<RagDocumentSummary>`（animeId `String→Long`）。`RagIngestController` 暴露 `GET /api/rag/documents`（全局 JWT），返回 `ResponseResult<List<RagDocumentSummaryResponse>>`。前端 antd Table 前端分页 + 标题 filter。

**Tech Stack:** Java 17 + Spring Boot 3.5.3（Maven 多模块）/ Node 24 + Fastify + LangChain.ts + chromadb 1.5.x / UmiJS Max + Ant Design 5 / JUnit5 + Mockito + AssertJ + MockWebServer / vitest + MockMvc。

## Global Constraints

- Java 17（`JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home`），Maven 用 homebrew `/opt/homebrew/bin/mvn`（3.9.16），默认 PATH 的 mvn 3.6.0 不可用
- 依赖方向：`starter → application → domain`，`infrastructure → domain`；domain 不依赖 Spring；仓储/网关接口在 domain，infra 实现
- 不改 ingest 链路，列表只返回 `animeId`+`title`（metadata 现有字段）
- Chroma collection 名 `anime_wiki`，端口 `8100`
- Java→rag-service 鉴权用 `X-Internal-Token`（`RagProperties.internalToken`）；starter controller 走全局 JWT（`JwtTokenProvider`）
- rag-service 每个 route 是 Fastify plugin，`index.ts` 用 `app.register(route, {prefix})` 挂载；decorated 函数测试时 mock（见 `query.test.ts`）
- chroma 的 `id` 是 string；Java `RagDocumentSummary.animeId` 为 `Long`，解析时 `Long.parseLong`
- 前端全局 `timeout: 10000`（`app.ts`），列表 GET 返回快，不覆盖 timeout；`responseInterceptors` 自动解包 `{status,message,data}`，`status===0` 抛 `BizError` 由全局 `errorHandler` `message.error` 兜底

---

### Task 1: rag-service `listDocuments` + `GET /documents` 路由

**Files:**
- Modify: `rag-service/src/rag/vectorStore.ts`（加 `listDocuments` export）
- Create: `rag-service/src/routes/documents.ts`
- Create: `rag-service/src/routes/documents.test.ts`
- Modify: `rag-service/src/index.ts`（register + decorate）

**Interfaces:**
- Consumes: `getVectorStore()`（现有，返回 langchain `Chroma` store）；langchain `Chroma` 类的 public `collection?: Collection`（chromadb `Collection`），`Collection.get({include,limit})` 返回 `GetResponse = {ids:string[], metadatas:(Metadata|null)[], ...}`
- Produces: `GET /documents` 响应 `{documents:[{animeId:string, title:string}, ...]}`；decorated `app.listDocuments()` 函数

- [ ] **Step 1: 写失败测试 `documents.test.ts`**

创建 `rag-service/src/routes/documents.test.ts`：

```ts
import { describe, it, expect, vi } from "vitest";
import Fastify from "fastify";

describe("documents route", () => {
  it("lists documents via decorated listDocuments", async () => {
    const app = Fastify();
    app.decorate("listDocuments", vi.fn().mockResolvedValue([
      { animeId: "1", title: "标题A" },
      { animeId: "2", title: "标题B" },
    ]));
    const { default: documentsRoute } = await import("./documents.js");
    await app.register(documentsRoute, { prefix: "/documents" });

    const res = await app.inject({
      method: "GET",
      url: "/documents",
      headers: { "x-internal-token": "t" },
    });
    expect(res.statusCode).toBe(200);
    expect(res.json()).toEqual({
      documents: [
        { animeId: "1", title: "标题A" },
        { animeId: "2", title: "标题B" },
      ],
    });
  });
});
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd rag-service && npx vitest run src/routes/documents.test.ts`
Expected: FAIL — `documents.js` 模块不存在（import 解析失败）

- [ ] **Step 3: 实现 `vectorStore.ts` 加 `listDocuments`**

在 `rag-service/src/rag/vectorStore.ts` 末尾追加（不改现有 `getVectorStore`/`ingestDocuments`）：

```ts
export async function listDocuments(): Promise<{ animeId: string; title: string }[]> {
  const store = await getVectorStore();
  const col = store.collection;
  if (!col) throw new Error("chroma collection not initialized");
  const res = await col.get({ include: ["metadatas"], limit: 1000 });
  const ids = res.ids ?? [];
  const metadatas = res.metadatas ?? [];
  return ids.map((id, i) => ({
    animeId: id,
    title: (metadatas[i] as { title?: string } | null)?.title ?? "",
  }));
}
```

说明：`col.get` 的 `include` 参数上下文类型为 `IncludeEnum[]`，字面量 `["metadatas"]` 会被上下文类型化，无需 cast。若 `tsc` 报错则改 `include: ["metadatas"] as ["metadatas"]`。

- [ ] **Step 4: 实现 `documents.ts` 路由**

创建 `rag-service/src/routes/documents.ts`：

```ts
import type { FastifyInstance } from "fastify";

export default async function documentsRoute(app: FastifyInstance) {
  app.get("/", async (_req, reply) => {
    try {
      const documents = await (app as any).listDocuments();
      return reply.send({ documents });
    } catch (e: any) {
      app.log.error(e);
      return reply.code(500).send({ error: e?.message ?? "list documents failed" });
    }
  });
}
```

- [ ] **Step 5: 修改 `index.ts` register + decorate**

在 `rag-service/src/index.ts`：
- 顶部 import 区加：`import { listDocuments } from "./rag/vectorStore.js";`
- 顶部 import 区加：`import documentsRoute from "./routes/documents.js";`
- 在 `app.decorate("createModel", ...)` 之后加：`app.decorate("listDocuments", listDocuments);`
- 在 `await app.register(queryRoute, { prefix: "/query" });` 之后加：`await app.register(documentsRoute, { prefix: "/documents" });`

- [ ] **Step 6: 跑测试确认通过**

Run: `cd rag-service && npx vitest run src/routes/documents.test.ts`
Expected: PASS（1 test）

- [ ] **Step 7: tsc 类型检查**

Run: `cd rag-service && npx tsc --noEmit`
Expected: 无错误

- [ ] **Step 8: commit**

```bash
git add rag-service/src/rag/vectorStore.ts rag-service/src/routes/documents.ts rag-service/src/routes/documents.test.ts rag-service/src/index.ts
git commit -m "feat(rag-service): 新增 GET /documents 列出已入库番剧"
```

---

### Task 2: Java domain 值对象 + `RagGateway.listDocuments` 接口 + infra 实现 + 测试

**Files:**
- Create: `anitrack-domain/src/main/java/com/anitrack/domain/rag/model/RagDocumentSummary.java`
- Modify: `anitrack-domain/src/main/java/com/anitrack/domain/rag/gateway/RagGateway.java`
- Modify: `anitrack-infrastructure/src/main/java/com/anitrack/infra/rag/gateway/RagGatewayImpl.java`
- Test: `anitrack-infrastructure/src/test/java/com/anitrack/infra/rag/gateway/RagGatewayImplTest.java`

**Interfaces:**
- Consumes: `RagProperties.getBaseUrl()`/`getInternalToken()`（现有）；`RestClient.Builder`（现有注入）
- Produces: `RagGateway.listDocuments(): List<RagDocumentSummary>`（供 Task 3 application 调用）；`RagDocumentSummary{animeId:Long, title:String}`

- [ ] **Step 1: 写失败测试**

在 `RagGatewayImplTest.java` 加 import 与测试方法。import 区加：

```java
import com.anitrack.domain.rag.model.RagDocumentSummary;
```

在 `streamQuery_returns_chunks` 测试方法后追加：

```java
    @Test
    void listDocuments_returns_summaries() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"documents\":[{\"animeId\":\"1\",\"title\":\"标题A\"},{\"animeId\":\"2\",\"title\":\"标题B\"}]}"));
        List<RagDocumentSummary> docs = gateway.listDocuments();
        assertThat(docs).hasSize(2);
        assertThat(docs.get(0).getAnimeId()).isEqualTo(1L);
        assertThat(docs.get(0).getTitle()).isEqualTo("标题A");
        var req = server.takeRequest();
        assertThat(req.getMethod()).isEqualTo("GET");
        assertThat(req.getPath()).isEqualTo("/documents");
        assertThat(req.getHeader("X-Internal-Token")).isEqualTo("token");
    }
```

- [ ] **Step 2: 跑测试确认失败**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home /opt/homebrew/bin/mvn -q -pl anitrack-infrastructure -am test -Dtest=RagGatewayImplTest`
Expected: 编译失败 — `RagGateway.listDocuments()` / `RagDocumentSummary` 不存在

- [ ] **Step 3: 建 `RagDocumentSummary` 值对象**

创建 `anitrack-domain/src/main/java/com/anitrack/domain/rag/model/RagDocumentSummary.java`：

```java
package com.anitrack.domain.rag.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RagDocumentSummary {
    private final Long animeId;
    private final String title;

    public static RagDocumentSummary of(Long animeId, String title) {
        return new RagDocumentSummary(animeId, title);
    }
}
```

- [ ] **Step 4: `RagGateway` 加方法**

在 `RagGateway.java` 接口加方法（import `java.util.List` 与 `RagDocumentSummary` 已在 `RagDocument` 同包，无需新 import；`List` 已 import）：

```java
    List<RagDocumentSummary> listDocuments();
```

- [ ] **Step 5: `RagGatewayImpl` 加实现与 record**

在 `RagGatewayImpl.java`：
- import 区加：`import com.anitrack.domain.rag.model.RagDocumentSummary;`
- 在 `streamQuery` 方法后、`IngestResponse` record 前，加实现：

```java
    @Override
    public List<RagDocumentSummary> listDocuments() {
        RestClient client = restClientBuilder.baseUrl(properties.getBaseUrl()).build();
        DocumentsResponse resp = client.get().uri("/documents")
                .header("X-Internal-Token", properties.getInternalToken())
                .retrieve()
                .body(DocumentsResponse.class);
        if (resp == null || resp.documents() == null) {
            return List.of();
        }
        return resp.documents().stream()
                .map(d -> RagDocumentSummary.of(Long.parseLong(d.animeId()), d.title()))
                .toList();
    }
```

- 在 `IngestResponse` record 旁加两个 record：

```java
    private record DocumentsResponse(List<DocumentItem> documents) {}
    private record DocumentItem(String animeId, String title) {}
```

- [ ] **Step 6: 跑测试确认通过**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home /opt/homebrew/bin/mvn -q -pl anitrack-infrastructure -am test -Dtest=RagGatewayImplTest`
Expected: PASS（3 tests）

- [ ] **Step 7: commit**

```bash
git add anitrack-domain/src/main/java/com/anitrack/domain/rag/model/RagDocumentSummary.java anitrack-domain/src/main/java/com/anitrack/domain/rag/gateway/RagGateway.java anitrack-infrastructure/src/main/java/com/anitrack/infra/rag/gateway/RagGatewayImpl.java anitrack-infrastructure/src/test/java/com/anitrack/infra/rag/gateway/RagGatewayImplTest.java
git commit -m "feat(rag): domain 加 RagDocumentSummary 与 listDocuments 网关实现"
```

---

### Task 3: Java application `RagApplication.listDocuments` + 测试

**Files:**
- Modify: `anitrack-application/src/main/java/com/anitrack/application/service/RagApplication.java`
- Test: `anitrack-application/src/test/java/com/anitrack/application/service/RagApplicationTest.java`

**Interfaces:**
- Consumes: `RagGateway.listDocuments()`（Task 2 产出）
- Produces: `RagApplication.listDocuments(): List<RagDocumentSummary>`（供 Task 4 controller 调用）

- [ ] **Step 1: 写失败测试**

在 `RagApplicationTest.java`：
- import 区加：`import com.anitrack.domain.rag.model.RagDocumentSummary;`
- 在 `ingestByCriteria_uses_gateway_filter_and_ingests` 方法后、`animeFixture` 前追加：

```java
    @Test
    void listDocuments_delegates_to_gateway() {
        BangumiGateway bangumi = mock(BangumiGateway.class);
        RagGateway rag = mock(RagGateway.class);
        when(rag.listDocuments()).thenReturn(List.of(RagDocumentSummary.of(1L, "标题A")));

        RagApplication app = new RagApplication(bangumi, rag);
        List<RagDocumentSummary> docs = app.listDocuments();

        assertThat(docs).hasSize(1);
        assertThat(docs.get(0).getAnimeId()).isEqualTo(1L);
        verify(rag).listDocuments();
    }
```

- [ ] **Step 2: 跑测试确认失败**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home /opt/homebrew/bin/mvn -q -pl anitrack-application -am test -Dtest=RagApplicationTest`
Expected: 编译失败 — `RagApplication.listDocuments()` 不存在

- [ ] **Step 3: 实现**

在 `RagApplication.java`：
- import 区加：`import com.anitrack.domain.rag.model.RagDocumentSummary;`（`List` 已 import）
- 在 `ingestByCriteria` 方法后、`toDocument` 前加：

```java
    public List<RagDocumentSummary> listDocuments() {
        return ragGateway.listDocuments();
    }
```

- [ ] **Step 4: 跑测试确认通过**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home /opt/homebrew/bin/mvn -q -pl anitrack-application -am test -Dtest=RagApplicationTest`
Expected: PASS

- [ ] **Step 5: commit**

```bash
git add anitrack-application/src/main/java/com/anitrack/application/service/RagApplication.java anitrack-application/src/test/java/com/anitrack/application/service/RagApplicationTest.java
git commit -m "feat(rag): application 加 listDocuments 委托"
```

---

### Task 4: Java starter `GET /api/rag/documents` + response + WebMvcTest

**Files:**
- Create: `anitrack-starter/src/main/java/com/anitrack/starter/response/RagDocumentSummaryResponse.java`
- Modify: `anitrack-starter/src/main/java/com/anitrack/starter/controller/RagIngestController.java`
- Create: `anitrack-starter/src/test/java/com/anitrack/starter/controller/RagIngestControllerTest.java`

**Interfaces:**
- Consumes: `RagApplication.listDocuments()`（Task 3 产出）；`RagDocumentSummary.getAnimeId()/getTitle()`
- Produces: `GET /api/rag/documents` → `ResponseResult<List<RagDocumentSummaryResponse>>`（前端 Task 5 调用契约）

- [ ] **Step 1: 写失败测试**

创建 `anitrack-starter/src/test/java/com/anitrack/starter/controller/RagIngestControllerTest.java`：

```java
package com.anitrack.starter.controller;

import com.anitrack.application.service.RagApplication;
import com.anitrack.domain.rag.model.RagDocumentSummary;
import com.anitrack.infra.auth.JwtTokenProvider;
import com.anitrack.starter.converter.HttpConverter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RagIngestController.class)
class RagIngestControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean RagApplication ragApplication;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean HttpConverter httpConverter;

    @Test
    void documents_returns_list() throws Exception {
        when(jwtTokenProvider.validateToken(any())).thenReturn(true);
        when(jwtTokenProvider.getUserId(any())).thenReturn(1L);
        when(ragApplication.listDocuments()).thenReturn(List.of(
                RagDocumentSummary.of(1L, "标题A"),
                RagDocumentSummary.of(2L, "标题B")));

        mockMvc.perform(get("/api/rag/documents")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(1))
                .andExpect(jsonPath("$.data[0].animeId").value(1))
                .andExpect(jsonPath("$.data[0].title").value("标题A"))
                .andExpect(jsonPath("$.data[1].animeId").value(2));
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home /opt/homebrew/bin/mvn -q -pl anitrack-starter -am test -Dtest=RagIngestControllerTest`
Expected: 失败 — `GET /api/rag/documents` 不存在（404）

- [ ] **Step 3: 建 response 类**

创建 `anitrack-starter/src/main/java/com/anitrack/starter/response/RagDocumentSummaryResponse.java`：

```java
package com.anitrack.starter.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RagDocumentSummaryResponse {
    private final Long animeId;
    private final String title;
}
```

- [ ] **Step 4: `RagIngestController` 加 `GET /documents`**

在 `RagIngestController.java`：
- import 区加：`import com.anitrack.domain.rag.model.RagDocumentSummary;`
- import 区加：`import com.anitrack.starter.response.RagDocumentSummaryResponse;`
- import 区加：`import org.springframework.web.bind.annotation.GetMapping;`（`PostMapping` 已有）
- 在 `ingest` 方法后加：

```java
    @GetMapping("/documents")
    public ResponseResult<List<RagDocumentSummaryResponse>> documents() {
        List<RagDocumentSummaryResponse> list = ragApplication.listDocuments().stream()
                .map(s -> new RagDocumentSummaryResponse(s.getAnimeId(), s.getTitle()))
                .toList();
        return ResponseResult.success(list);
    }
```

- [ ] **Step 5: 跑测试确认通过**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home /opt/homebrew/bin/mvn -q -pl anitrack-starter -am test -Dtest=RagIngestControllerTest`
Expected: PASS

- [ ] **Step 6: 全量测试回归**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home /opt/homebrew/bin/mvn -q clean install`
Expected: BUILD SUCCESS，全模块测试 0 失败

- [ ] **Step 7: commit**

```bash
git add anitrack-starter/src/main/java/com/anitrack/starter/response/RagDocumentSummaryResponse.java anitrack-starter/src/main/java/com/anitrack/starter/controller/RagIngestController.java anitrack-starter/src/test/java/com/anitrack/starter/controller/RagIngestControllerTest.java
git commit -m "feat(rag): starter 暴露 GET /api/rag/documents"
```

---

### Task 5: webui service + 列表页 + 路由 + 菜单

**Files:**
- Modify: `webui/src/services/rag.ts`
- Create: `webui/src/pages/rag/documents.tsx`
- Modify: `webui/.umirc.ts`
- Modify: `webui/src/layouts/index.tsx`

**Interfaces:**
- Consumes: `GET /api/rag/documents` → `{status,data:[{animeId:number,title:string}]}`（前端拦截器已解包，`request` 返回 `ApiResult<T>`）
- Produces: `/rag/documents` 页面（antd Table 前端分页 + 标题 filter）

- [ ] **Step 1: service 加 `listDocuments`**

在 `webui/src/services/rag.ts` 顶部 import 后追加类型与方法（不改现有 `ingestByCriteria`）：

```ts
export interface RagDocumentSummary {
  animeId: number;
  title: string;
}

export async function listDocuments() {
  const res = await request<ApiResult<RagDocumentSummary[]>>('/api/rag/documents', {
    method: 'GET',
  });
  return res.data;
}
```

- [ ] **Step 2: 建列表页 `documents.tsx`**

创建 `webui/src/pages/rag/documents.tsx`：

```tsx
import { useEffect, useState } from 'react';
import { Typography, Table, Input, Space } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { listDocuments, type RagDocumentSummary } from '@/services/rag';

export default function RagDocumentsPage() {
  const [data, setData] = useState<RagDocumentSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');

  const fetchData = async () => {
    setLoading(true);
    try {
      setData(await listDocuments());
    } catch {
      // 全局 errorHandler 兜底 message.error
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const filtered = keyword
    ? data.filter((d) => d.title.toLowerCase().includes(keyword.toLowerCase()))
    : data;

  const columns: ColumnsType<RagDocumentSummary> = [
    { title: 'animeId', dataIndex: 'animeId', width: 120 },
    { title: '标题', dataIndex: 'title' },
  ];

  return (
    <div style={{ maxWidth: 900, margin: '0 auto', padding: 24 }}>
      <Typography.Title level={3}>已入库番剧</Typography.Title>
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Input.Search
          placeholder="按标题搜索"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          allowClear
        />
        <Table
          rowKey="animeId"
          columns={columns}
          dataSource={filtered}
          loading={loading}
          pagination={{ pageSize: 10 }}
          size="middle"
        />
      </Space>
    </div>
  );
}
```

- [ ] **Step 3: `.umirc.ts` 加路由**

在 `webui/.umirc.ts` 的 `routes` 数组中，`/rag/ingest` 行之后加一行：

```ts
    { path: '/rag/documents', component: '@/pages/rag/documents', wrappers: ['@/wrappers/auth'] },
```

- [ ] **Step 4: `layouts/index.tsx` 加菜单项**

在 `webui/src/layouts/index.tsx` 的 `MENU_ITEMS` 数组中，「RAG 采集」项（`key: '/rag/ingest'`）之后、「番剧问答」项（`key: '/rag'`）之前，插入：

```tsx
  { key: '/rag/documents', label: <Link to="/rag/documents">已入库</Link> },
```

顺序为 `/rag/ingest` → `/rag/documents` → `/rag`（更长 key 在 `/rag` 前，避免 `startsWith` 误高亮「番剧问答」）。

- [ ] **Step 5: dev 热更新验证编译**

Run: `cd webui && npx tsc --no-emit`（或确认 dev server 控制台无类型错误）
Expected: 无错误

- [ ] **Step 6: commit**

```bash
git add webui/src/services/rag.ts webui/src/pages/rag/documents.tsx webui/.umirc.ts webui/src/layouts/index.tsx
git commit -m "feat(webui): 新增已入库番剧列表页"
```

---

### Task 6: 端到端验证

**Files:** 无（仅验证）

**Interfaces:**
- Consumes: Task 1-5 全部产出

- [ ] **Step 1: 启动 Chroma**

Run: `cd rag-service && chroma run --path ./chroma_data --port 8100`（后台）
Expected: 日志含 `Anonymized telemetry enabled` / `Listening on`，端口 8100

- [ ] **Step 2: 启动 rag-service**

Run: `cd rag-service && npm run dev`（后台，需 `.env` 含 `INTERNAL_TOKEN`/`CHROMA_URL`/LLM/embedding 变量）
Expected: `Server listening` on 8081，`/health` 返回 `{"status":"ok"}`

- [ ] **Step 3: 启动后端**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home /opt/homebrew/bin/mvn -pl anitrack-starter -am package -DskipTests && java -jar anitrack-starter/target/anitrack-starter-*.jar --spring.profiles.active=local`（后台）
Expected: 启动日志 `Started AnitrackApplication`，端口 8080

- [ ] **Step 4: 启动 webui**

Run: `cd webui && npm run dev`（后台）
Expected: `Compiled successfully`，端口 8000

- [ ] **Step 5: curl 验证后端端点**

登录拿 JWT 后：
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' -d '{"username":"alice","password":"111111"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["token"])')
curl -s http://localhost:8080/api/rag/documents -H "Authorization: Bearer $TOKEN"
```
Expected: `{"status":1,"message":null,"data":[{"animeId":...,"title":"..."},...]}`，含 2026≥7.0 入库的 54 条左右

- [ ] **Step 6: 浏览器端到端**

浏览器开 `http://localhost:8000`，登录 `alice`/`111111` → 侧栏「已入库」→ 表格列出番剧（animeId+标题）→ 标题搜索框输入关键词验证 filter → 翻页验证分页。

Expected: Table 展示 ~54 行，分页+搜索可用

- [ ] **Step 7: 异常路径**

停 rag-service → 刷新 `/rag/documents` → 前端 toast 报错（全局 errorHandler 兜底），Table loading 结束空状态。

Expected: 不白屏，有错误提示

---

## Self-Review 结果

**1. Spec coverage:** spec 第 5 节落点表 8 行 → Task 1（rag-service 路由）、Task 2（RagGateway+RagDocumentSummary+RagGatewayImpl）、Task 3（RagApplication）、Task 4（RagIngestController+response）、Task 5（webui service/页/路由/菜单）全覆盖。spec 第 7 节测试 → Task 1 documents.test.ts、Task 2 RagGatewayImplTest、Task 4 RagIngestControllerTest、Task 3 RagApplicationTest 全覆盖。spec 第 8 节验证 → Task 6。无遗漏。

**2. Placeholder scan:** 无 TBD/TODO；错误处理沿用 ingest 模式不特殊兜底（spec 第 6 节）；每步含完整代码。

**3. Type consistency:** `listDocuments` 签名 domain `List<RagDocumentSummary>` → infra `List<RagDocumentSummary>` → application `List<RagDocumentSummary>` → controller map 成 `List<RagDocumentSummaryResponse>`，全程一致；`RagDocumentSummary.of(Long,String)` / `RagDocumentSummaryResponse(Long,String)`；前端 `RagDocumentSummary{animeId:number,title:string}` 与后端 JSON `animeId`(Long→number) 一致。
