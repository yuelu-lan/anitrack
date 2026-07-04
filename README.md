# anitrack

基于 Spring Boot 3 + Java 17 的 DDD 追番管理练习项目，采用六边形架构思想的多模块设计。

当前进度：Phase 0（基础脚手架 + 用户注册登录）已完成。

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
| 单元测试 | JUnit5 + Mockito + AssertJ |

## 模块结构

```
anitrack/
├── anitrack-starter/          # Web启动层：controller/config/interceptor/filter
├── anitrack-application/      # 应用服务层：用例编排
├── anitrack-domain/           # 领域核心层：聚合根/领域服务/仓储接口
├── anitrack-infrastructure/   # 基础设施层：仓储实现/网关/持久化
└── anitrack-common/           # 公共组件层：dto/enums/utils
```

依赖方向：`starter → application → domain`，`infrastructure → domain`。

详细规范见 [`docs/rules/anitrack-project-rules.md`](docs/rules/anitrack-project-rules.md)。

## 快速开始

**环境要求**：JDK 17、Maven 3.6.3+、MySQL 8.x

**环境变量**：

| 变量 | 说明 |
| --- | --- |
| `DB_USERNAME` | MySQL 用户名 |
| `DB_PASSWORD` | MySQL 密码 |
| `JWT_SECRET` | JWT 签名密钥 |

**启动**：

```bash
mvn clean install
DB_USERNAME=xxx DB_PASSWORD=xxx JWT_SECRET=xxx mvn -pl anitrack-starter spring-boot:run
```

服务默认监听 `8080` 端口，数据库表结构由 Flyway 自动创建。

## 已实现接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/user/register` | 用户注册 |
| POST | `/api/user/login` | 用户登录，返回 JWT token |

其余接口需携带 `Authorization: Bearer <token>` 请求头。
