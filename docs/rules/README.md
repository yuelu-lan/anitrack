# anitrack 规则集说明

本目录是 anitrack 项目的 Claude Code 规则集，改写自原有 Cursor 规则集，适配 anitrack 的技术栈（Spring Boot 3 + Java 17 DDD）与 Claude Code 的加载机制。

## 加载机制说明

不使用 Claude Code 的 `.claude/rules/*.md` + `paths:` 原生机制，而是在 `anitrack/CLAUDE.md` 中维护索引，分两类：

- **`@` 引用（Always）**：CLAUDE.md 中用 `@docs/rules/xxx.md` 语法引用，内容无条件、始终注入上下文
- **路径+说明（Manual）**：CLAUDE.md 中只列出文件路径和使用场景说明，由 Claude Code 根据当前任务自行判断是否用 Read 工具读取

## 文件索引

| 文件 | 引用方式 | 用途 | 对应代码生成场景 |
| --- | --- | --- | --- |
| [anitrack-project-rules.md](anitrack-project-rules.md) | `@` Always | 总纲：技术栈、模块结构、命名规范 | 任何代码生成场景均适用 |
| [anitrack-domain-rules.md](anitrack-domain-rules.md) | 路径+说明 Manual | 追番领域知识：五个限界上下文、状态机、跨上下文规则、ACL隔离原则 | 涉及追番领域模型/状态机设计时 |
| [anitrack-model-rules.md](anitrack-model-rules.md) | 路径+说明 Manual | 领域层规范：充血模型、领域服务、仓储接口、领域事件 | 编写 `anitrack-domain` 模块代码时 |
| [anitrack-persist-rules.md](anitrack-persist-rules.md) | 路径+说明 Manual | 持久化层规范：MyBatis XML、PO、Mapper、表设计、数据源配置 | 编写 `anitrack-infrastructure` 模块持久化代码时 |
| [anitrack-application-rules.md](anitrack-application-rules.md) | 路径+说明 Manual | 应用层规范：编排职责、异常处理、事务管理 | 编写 `anitrack-application` 模块代码时 |
| [anitrack-web-rules.md](anitrack-web-rules.md) | 路径+说明 Manual | Web层规范：Controller、统一响应体、参数校验、全局异常处理、JWT鉴权 | 编写 `anitrack-starter` 模块 Controller 代码时 |
| [anitrack-unittest-rules.md](anitrack-unittest-rules.md) | 路径+说明 Manual | 单元测试规范：JUnit5+Mockito+AssertJ | 编写领域层/应用层单元测试时 |
| [anitrack-web-test-rules.md](anitrack-web-test-rules.md) | 路径+说明 Manual | Web层集成测试规范：`@WebMvcTest`+MockMvc | 编写 Controller 集成测试时 |
| [anitrack-init-dependency-rules.md](anitrack-init-dependency-rules.md) | 路径+说明 Manual | 依赖初始化规范：测试依赖坐标 | 初始化或调整 `pom.xml` 依赖时 |

## 使用提示词模板

- **编写Controller层代码**："基于 anitrack-web-rules.md 规范，为 XxxController 实现 xxx 接口"
- **编写应用层代码**："基于 anitrack-application-rules.md 规范，为 XxxApplication 实现 xxx 用例编排"
- **编写领域层代码**："基于 anitrack-model-rules.md 和 anitrack-domain-rules.md 规范，为 Xxx 聚合根实现 xxx 业务规则"
- **编写持久化层代码**："基于 anitrack-persist-rules.md 规范，为 Xxx 实现仓储层代码"
- **编写单元测试**："基于 anitrack-unittest-rules.md 规范，为 Xxx 生成单元测试"
- **编写Web层集成测试**："基于 anitrack-web-test-rules.md 规范，为 XxxController 生成集成测试"
- **初始化/调整依赖**："基于 anitrack-init-dependency-rules.md 规范，检查/调整 pom.xml 测试依赖"
