# anitrack 项目说明

Spring Boot 3 + Java 17 的 DDD 追番管理练习项目。详细设计见 `docs/superpowers/specs/2026-07-02-anitrack-design.md`。

## 代码规范

始终参考：
- @docs/rules/anitrack-project-rules.md

按需参考（写对应代码时主动读取）：
- docs/rules/anitrack-domain-rules.md —— 涉及追番领域模型/状态机/跨上下文规则时参考
- docs/rules/anitrack-model-rules.md —— 编写领域层代码（聚合根/领域服务/仓储接口）时参考
- docs/rules/anitrack-persist-rules.md —— 编写持久化层代码（PO/Mapper/XML/仓储实现）时参考
- docs/rules/anitrack-application-rules.md —— 编写应用服务编排代码时参考
- docs/rules/anitrack-web-rules.md —— 编写Controller/统一响应/异常处理/鉴权代码时参考
- docs/rules/anitrack-unittest-rules.md —— 编写单元测试时参考
- docs/rules/anitrack-web-test-rules.md —— 编写Web层集成测试时参考
- docs/rules/anitrack-init-dependency-rules.md —— 初始化/调整pom.xml依赖时参考
