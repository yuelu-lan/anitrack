# anitrack Phase 0 实施记录

日期：2026-07-03 ~ 2026-07-04
计划文档：`docs/superpowers/plans/2026-07-03-anitrack-phase0-scaffold-and-auth.md`

## 目标

搭建 anitrack 5 模块 Maven 骨架（domain/infrastructure/application/starter/common），
实现用户注册/登录功能（Spring Security + JWT），作为后续 Phase 1-4 的地基。

## 做了什么

按 10 个 Task 逐步实施（TDD，每个 Task 独立 commit）：

1. Maven 多模块骨架
2. `User` 聚合根 + `UserRole` 枚举 + `UserRepo` 仓储接口 + 领域异常基类
3. `t_user` 表 Flyway 脚本 + `UserPO` + `UserMapper`
4. `UserConverter`（MapStruct）+ `UserRepoImpl`
5. 应用层统一异常（`AnitrackAppException` + `AppExceptionEnum`）
6. `UserApplication`（注册/登录用例编排）
7. `JwtTokenProvider`（jjwt 签发/校验）
8. 统一响应体 + 全局异常处理
9. `UserController`（注册/登录接口）+ Web 层集成测试
10. `UserContextHolder` + JWT 拦截器 + Security 配置 + 应用启动 + 端到端联调

全部完成后做了一次全分支最终 review，修复发现的问题，合并到 main（分支
`feature/phase0-scaffold-and-auth`，17 个 commit）。

## 遇到的问题及解决

**1. 聚合根强封装 vs MapStruct 反射构造（Task 2/4）**

`User` 按规则设为 `@Builder(access = PRIVATE)` 防止绕过 `register()` 工厂方法，导致
MapStruct 无法反射构造 `User`。解决：新增 `User.reconstitute(...)` 工厂方法专供基础设施层
从 PO 重建领域对象，`UserConverter.toDomain()` 改为显式调用该方法而非依赖 MapStruct 自动生成。

**2. `@MapperScan` 与 `@WebMvcTest` 切片测试冲突（Task 9/10）**

`@MapperScan` 通过 `@Import` 生效，不受 `@WebMvcTest` 的 `TypeExcludeFilter` 约束。若直接加在
作为 `@SpringBootConfiguration` 根类的 `ApplicationLoader` 上，会导致所有以它为上下文源的切片
测试无条件注册 `UserMapper` bean，因无数据源报 `BeanCreationException`（已复现：8 个用例全部
失败）。解决：把 `@MapperScan` 抽到独立的 `MyBatisConfig` 配置类，不影响真实启动时的组件扫描。

**3. 全分支最终 review 发现 2 项 Important（10 个 Task 完成后）**

- `UserApplication.register()` 是写操作但缺少 `@Transactional`——补上，同时给
  `anitrack-application` 模块补充 `spring-tx` 依赖
- `JwtAuthInterceptor`（所有 `/api/**` 接口的鉴权入口）零测试覆盖——新增
  `JwtAuthInterceptorTest`，覆盖缺 header/无 Bearer 前缀/token 非法/token 合法/清理 ThreadLocal
  5 个场景

修复后 25 个测试全过，判定 Ready to merge，合并到 main。

**4. 本地 MySQL 端到端联调时发现真实 bug（Task 10 Step 11 遗留项，本次补齐）**

此前 Step 11 因本地无可用 MySQL 被跳过。真正跑起来后发现 `POST /api/user/register` 返回的
`id` 恒为 `null`——根因是 `UserRepoImpl.save()` 把 `User` 转成一次性临时 `UserPO` 再 insert，
MyBatis `useGeneratedKeys` 生成的自增 id 只回写到这个临时对象，从未传播回调用方持有的
`User` 引用。此前全部单测都 mock 掉了 `UserRepo`，从未真正触发过 insert，完全没捕获到。

修复（未破坏聚合根封装）：`UserRepo.save(User)` 签名由 `void` 改为返回 `User`，
`UserRepoImpl.save()` insert 后用 `toDomain(po)` 重建带生成 id 的新 `User` 返回，
`UserApplication.register()` 改用返回值构造 `UserBO`，同步调整了对应单测。

顺带解决了本地 Maven/JDK 环境问题（默认 PATH 命中不满足版本要求的 Maven 3.6.0 + JDK 1.8，
改用 Homebrew 的 Maven 3.9.16 + Temurin 17）。

## 最终结果

- `mvn test`：5 模块全过，25 个测试 0 失败
- 端到端 curl 验证：注册（id 正确回写）、登录（JWT token）、无 token 401、带 token 放行、
  重复用户名/密码错误的业务异常场景，全部符合预期
- Phase 0 全部完成并已合并到 main，本次 bug 修复单独 commit（`b900b8e`），未 push
