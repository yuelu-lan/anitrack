# anitrack

基于 Spring Boot 3 + Java 17 的 DDD 追番管理练习项目，采用六边形架构思想的多模块设计。

当前进度（均已完成）：

- Phase 0：基础脚手架 + 用户注册登录
- Phase 1a：番剧目录（Bangumi ACL）
- Phase 1b：追番核心（状态机/进度）
- Phase 2：评价（评分/评论）
- RAG：番剧百科问答（独立 rag-service + Chroma 向量库）——支持手动/按条件批量采集（年份+评分）入库、已入库番剧列表查看、前端 ai-sdk 流式问答
- 前端：`webui/`（UmiJS Max + Ant Design，覆盖追番/评价/RAG 采集/已入库列表/番剧问答操作界面）

## 技术栈

| 分类 | 选型 |
| --- | --- |
| 语言/框架 | Java 17 + Spring Boot 3.5.3 |
| Web | Spring MVC |
| 构建工具 | Maven（多模块） |
| 数据库 | MySQL 8.x |
| ORM | MyBatis 原生 XML Mapper |
| 数据库版本管理 | Flyway |
| 认证 | Spring Security + JWT（jjwt） |
| 密码加密 | BCryptPasswordEncoder |
| Bean 拷贝 | MapStruct |
| 外部数据源 | Bangumi API（番剧元数据，通过防腐层接入） |
| 单元测试 | JUnit5 + Mockito + AssertJ |

RAG 引擎（`rag-service/`，独立 Node/TS 子项目）：

| 分类 | 选型 |
| --- | --- |
| 语言/框架 | TypeScript + Fastify 5 |
| RAG 框架 | LangChain.ts（`@langchain/openai` / `@langchain/community`） |
| 向量库 | Chroma（自部署） |
| LLM/embedding | 硅基流动（SiliconFlow，OpenAI 兼容接口），可切换国内模型 |
| 服务间鉴权 | 共享密钥（`X-Internal-Token` header） |

前端（`webui/`）：

| 分类 | 选型 |
| --- | --- |
| 框架 | UmiJS Max（`@umijs/max` 4.6.71） |
| UI 组件库 | Ant Design 5 |
| 语言 | TypeScript |
| 包管理 | npm |
| AI 流式 | Vercel ai-sdk（`useChat` + `TextStreamChatTransport`） |

## 模块结构

```
anitrack/
├── anitrack-starter/          # Web启动层：controller/config/interceptor/filter
├── anitrack-application/      # 应用服务层：用例编排
├── anitrack-domain/           # 领域核心层：聚合根/领域服务/仓储接口
├── anitrack-infrastructure/   # 基础设施层：仓储实现/网关/持久化
├── anitrack-common/           # 公共组件层：dto/enums/utils
├── rag-service/               # RAG 引擎：独立 Node/TS 工程，Fastify + LangChain.ts + Chroma
└── webui/                     # 前端：独立 npm 工程，UmiJS Max + Ant Design
```

- 后端模块依赖方向：`starter → application → domain`，`infrastructure → domain`
- `webui/` 是独立的前端工程，不属于 Maven 多模块，通过 dev server 代理调用后端 `/api`
- `rag-service/` 是独立的 RAG 引擎工程，由 Java 后端经共享密钥调用（`/ingest` 入库、`/query` 流式问答），不直接暴露给前端

详细规范见 [`docs/rules/anitrack-project-rules.md`](docs/rules/anitrack-project-rules.md)。

## 环境配置文件

项目涉及多套运行环境（Java 后端、rag-service、前端），配置文件分散在几处，说明如下。所有 `.env` 和 `application-local.yml` 均已被 `.gitignore` 忽略，**不会提交**；提交的是对应的 `.example` 模板。

| 文件 | 提交 | 作用 | 谁读取 |
| --- | --- | --- | --- |
| `.env.example` | ✅ | 根目录环境变量模板（MySQL/JWT/RAG 相关） | 复制为 `.env` 供 docker-compose 读取 |
| `.env` | ❌ | 根目录实际环境变量，**需从 `.env.example` 复制并填值** | docker-compose |
| `rag-service/.env.example` | ✅ | rag-service 环境变量模板（端口/Chroma/LLM/embedding） | 复制为 `rag-service/.env` |
| `rag-service/.env` | ❌ | rag-service 实际环境变量，**需从 `.env.example` 复制并填值** | rag-service（`dotenv`） |
| `application-local.yml.example` | ✅ | Java 后端 local profile 模板（本地 MySQL 密码/JWT/RAG） | 复制为 `application-local.yml` |
| `application-local.yml` | ❌ | Java 后端 local profile 实际配置，**需从 `.example` 复制并填值** | Spring Boot（`-Dspring.profiles.active=local`） |
| `application-docker.yml` | ✅ | Java 后端 docker profile 配置，密码通过根 `.env` 注入 | Spring Boot（docker profile） |

**关键点**：

- **本地启动（IDEA）**：需准备 `application-local.yml`（Java 侧）+ `rag-service/.env`（rag-service 侧），两者独立。Java 侧 local profile 不读根 `.env`，密码写在 `application-local.yml` 里。
- **Docker 启动**：只需准备根 `.env`——docker-compose 把它注入 Java 后端（docker profile 读环境变量）和 rag-service 容器（env 映射）。Java 后端的 `application-docker.yml` 用 `${DB_PASSWORD}` 等占位符读取。
- **共享密钥对齐**：Java ↔ rag-service 的共享密钥必须一致——根 `.env` 的 `RAG_INTERNAL_TOKEN` 与 `rag-service/.env` 的 `INTERNAL_TOKEN` 要填同一个值。Docker 启动时 docker-compose 会用根 `.env` 的值统一注入两侧，无需手动对齐。
- **密钥安全**：`.env` / `application-local.yml` 含真实密钥，已被 gitignore，切勿提交；`.env.example` 仅为占位模板。

## 快速开始

支持两种启动方式：**本地启动**（IDEA 开发）和 **Docker 启动**（一键起后端 + MySQL）。

### 方式一：本地启动（IDEA）

**环境要求**：JDK 17、Maven 3.6.3+、MySQL 8.x、Node 20+（rag-service）

本地启动涉及两份配置文件，各自独立填值：

**① Java 后端** —— 复制 `anitrack-starter/src/main/resources/application-local.yml.example` 为 `application-local.yml`，填入：

| 配置项（`application-local.yml`） | 说明 |
| --- | --- |
| `spring.datasource.username` / `password` | 本地 MySQL 账号密码 |
| `anitrack.jwt.secret` | JWT 签名密钥（Base64，至少 32 字节） |
| `anitrack.rag.internal-token` | Java ↔ rag-service 共享密钥，**需与下方 rag-service 的 `INTERNAL_TOKEN` 一致**，否则鉴权 401 |
| `anitrack.rag.base-url` | rag-service 地址（默认 `http://localhost:8081`） |

> local profile 不读根 `.env`，DB/JWT/RAG 全部写在 `application-local.yml` 里。`DB_USERNAME`/`DB_PASSWORD`/`JWT_SECRET` 环境变量仅在命令行启动时作为 `application.yml` 占位符的来源，IDEA 启动则直接读 yml。

**② rag-service** —— 复制 `rag-service/.env.example` 为 `rag-service/.env`，填入：

| 变量（`rag-service/.env`） | 说明 |
| --- | --- |
| `INTERNAL_TOKEN` | 与 Java 侧 `anitrack.rag.internal-token` 一致 |
| `LLM_API_KEY` / `EMBEDDING_API_KEY` | 硅基流动 API key（LLM + embedding，通常同一个） |
| `LLM_BASE_URL` / `EMBEDDING_BASE_URL` | 模型接口地址（默认硅基流动） |
| `LLM_MODEL` / `EMBEDDING_MODEL` | 模型名（默认 `deepseek-ai/DeepSeek-V4-Flash` / `Qwen/Qwen3-Embedding-4B`） |
| `CHROMA_URL` | Chroma 地址（默认 `http://localhost:8100`） |

**启动 Chroma**（向量库，先于 rag-service）：

```bash
pip install chromadb && chroma run --port 8100
# 或 docker compose up chroma
```

**启动 rag-service**：

```bash
cd rag-service
npm install --legacy-peer-deps
npm run dev            # 默认监听 8081
```

**启动 Java 后端**：

```bash
mvn clean install
mvn -pl anitrack-starter spring-boot:run -Dspring-boot.run.profiles=local
```

或在 IDEA 启动配置中设置 `Active profiles: local`。

### 方式二：Docker 启动

**环境要求**：Docker（含 Compose v2）

只需准备根目录一份 `.env`，docker-compose 会把变量注入 Java 后端（docker profile）和 rag-service 容器，无需手动准备 `application-local.yml` 或 `rag-service/.env`。

复制 `.env.example` 为 `.env`，填入：

| 变量（根 `.env`） | 说明 | 谁读取 |
| --- | --- | --- |
| `MYSQL_ROOT_PASSWORD` | MySQL root 密码 | MySQL 容器 |
| `MYSQL_DATABASE` | 数据库名（默认 `anitrack`） | MySQL 容器 |
| `DB_USERNAME` / `DB_PASSWORD` | 后端连接 MySQL 的账号密码 | Java 后端（`application-docker.yml`） |
| `JWT_SECRET` | JWT 签名密钥 | Java 后端 |
| `RAG_INTERNAL_TOKEN` | Java ↔ rag-service 共享密钥（两侧自动对齐） | Java 后端 + rag-service |
| `SILICONFLOW_API_KEY` | 硅基流动 API key | rag-service（映射为 `LLM_API_KEY`/`EMBEDDING_API_KEY`） |

**启动**：

```bash
cp .env.example .env
# 编辑 .env，填入上表各项
docker compose up --build
```

`docker-compose.yml` 会启动 MySQL 8（带数据卷持久化）+ Chroma 向量库 + rag-service + 后端应用，应用容器自动激活 `docker` profile。

### 启动说明

- 服务默认监听 `8080` 端口，数据库表结构由 Flyway 自动创建（`db/migration/V1~V5`）
- `local` / `docker` 两个 profile 会额外加载 `db/migration-dev/R__seed_demo_data.sql`，灌入演示数据（2 用户、4 番剧、5 追番、3 评价，覆盖全部追番状态）。演示账号：`alice` / `bob`，密码均为 `111111`
- 不激活任何 profile（生产部署）时，仅执行建表脚本，不灌演示数据
- MySQL 数据持久化在 `anitrack-mysql-data` 卷，`docker compose down` 保留数据，`docker compose down -v` 删除数据重新初始化

**网络访问说明**：番剧搜索接口实时调用 [Bangumi API](https://github.com/bangumi/api)（`api.bgm.tv`），该域名在国内通常无法直连，需开启代理（如 Clash 的 TUN/系统代理模式）才能正常访问。

### 启动前端

```bash
cd webui
npm install
npm run dev
```

前端默认监听 `8000` 端口，已配置代理将 `/api` 转发到后端 `http://localhost:8080`，无需后端配置 CORS。浏览器打开 `http://localhost:8000` 即可使用。

## 已实现接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/user/register` | 用户注册 |
| POST | `/api/user/login` | 用户登录，返回 JWT token |
| POST | `/api/anime/search` | 番剧搜索（实时调用 Bangumi API，回填本地缓存） |
| POST | `/api/anime/detail` | 番剧详情（只读本地缓存） |
| POST | `/api/watchlist/add` | 加入追番 |
| POST | `/api/watchlist/change_status` | 变更追番状态 |
| POST | `/api/watchlist/update_progress` | 更新观看进度 |
| POST | `/api/watchlist/detail` | 查询单条追番详情 |
| POST | `/api/watchlist/list` | 查询我的追番列表（可选按状态筛选） |
| POST | `/api/review/add` | 新增评价（仅 `WATCHED` 状态番剧可评） |
| POST | `/api/review/update` | 修改评价 |
| POST | `/api/review/detail` | 查看我对某番剧的评价详情 |
| POST | `/api/review/list_by_anime` | 查询某番剧的评价列表（分页，含评论人昵称/头像） |
| POST | `/api/review/my_list` | 查询我的评价列表（含番剧标题/封面） |
| POST | `/api/rag/ingest` | 触发番剧百科采集入库（参数 `bangumiIds`，拉 Bangumi 详情写入 Chroma） |
| POST | `/api/rag/ingest_by_criteria` | 按条件批量采集入库（参数 `year`/`minRating`，拉 Bangumi 年度动画过滤评分后写入 Chroma） |
| GET | `/api/rag/documents` | 查询已入库番剧列表（返回 animeId/标题/原文名/放送/评分） |
| POST | `/api/rag/chat` | 番剧百科问答（流式返回，`text/plain` 逐 token 输出） |

除注册/登录外，其余接口均需携带 `Authorization: Bearer <token>` 请求头。
