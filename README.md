# anitrack

基于 Spring Boot 3 + Java 17 的 DDD 追番管理练习项目，采用六边形架构思想的多模块设计。

当前进度（均已完成）：

- Phase 0：基础脚手架 + 用户注册登录
- Phase 1a：番剧目录（Bangumi ACL）
- Phase 1b：追番核心（状态机/进度）
- Phase 2：评价（评分/评论）
- 前端：`webui/`（UmiJS Max + Ant Design，覆盖全部 14 个接口的操作界面）

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

前端（`webui/`）：

| 分类 | 选型 |
| --- | --- |
| 框架 | UmiJS Max（`@umijs/max` 4.6.71） |
| UI 组件库 | Ant Design 5 |
| 语言 | TypeScript |
| 包管理 | npm |

## 模块结构

```
anitrack/
├── anitrack-starter/          # Web启动层：controller/config/interceptor/filter
├── anitrack-application/      # 应用服务层：用例编排
├── anitrack-domain/           # 领域核心层：聚合根/领域服务/仓储接口
├── anitrack-infrastructure/   # 基础设施层：仓储实现/网关/持久化
├── anitrack-common/           # 公共组件层：dto/enums/utils
└── webui/                     # 前端：独立 npm 工程，UmiJS Max + Ant Design
```

- 后端模块依赖方向：`starter → application → domain`，`infrastructure → domain`
- `webui/` 是独立的前端工程，不属于 Maven 多模块，通过 dev server 代理调用后端 `/api`

详细规范见 [`docs/rules/anitrack-project-rules.md`](docs/rules/anitrack-project-rules.md)。

## 快速开始

支持两种启动方式：**本地启动**（IDEA 开发）和 **Docker 启动**（一键起后端 + MySQL）。

### 方式一：本地启动（IDEA）

**环境要求**：JDK 17、Maven 3.6.3+、MySQL 8.x（本机或远程实例）

**环境变量**：

| 变量 | 说明 |
| --- | --- |
| `DB_USERNAME` | MySQL 用户名 |
| `DB_PASSWORD` | MySQL 密码 |
| `JWT_SECRET` | JWT 签名密钥（Base64，至少 32 字节） |

**配置文件**：复制 `anitrack-starter/src/main/resources/application-local.yml.example` 为 `application-local.yml`，填入本地数据库密码与 JWT 密钥。

**启动**：

```bash
mvn clean install
DB_USERNAME=xxx DB_PASSWORD=xxx JWT_SECRET=xxx \
  mvn -pl anitrack-starter spring-boot:run -Dspring-boot.run.profiles=local
```

或在 IDEA 启动配置中设置 `Active profiles: local`。

### 方式二：Docker 启动

**环境要求**：Docker（含 Compose v2）

**步骤**：

```bash
cp .env.example .env
# 编辑 .env，设置 MySQL 密码与 JWT_SECRET
docker compose up --build
```

`docker-compose.yml` 会启动 MySQL 8（带数据卷持久化）+ 后端应用，应用容器自动激活 `docker` profile。

**配置文件**：`application-docker.yml` 已提交，密码通过 `.env` 注入环境变量，无需手动修改。

### 启动说明

- 服务默认监听 `8080` 端口，数据库表结构由 Flyway 自动创建（`db/migration/V1~V4`）
- `local` / `docker` 两个 profile 会额外加载 `db/migration-dev/R__seed_demo_data.sql`，灌入演示数据（2 用户、4 番剧、5 追番、3 评价，覆盖全部追番状态）。演示账号：`alice` / `bob`，密码均为 `password123`
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

除注册/登录外，其余接口均需携带 `Authorization: Bearer <token>` 请求头。
