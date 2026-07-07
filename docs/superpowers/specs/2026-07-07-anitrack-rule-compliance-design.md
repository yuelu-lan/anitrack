# anitrack：规则合规修复设计

## 背景与范围

针对规则审计发现的 4 项中等级别偏差进行修复，使后端代码与 `docs/rules/` 对齐。不涉及功能变更，不新增 API 端点，不调整持久化层。

4 项偏差：

1. 应用层缺 MapStruct Converter，4 个 Application 各自手写 `toBO()`
2. Web 响应枚举用原生 `WatchStatus`，不符合规则 10.7「枚举统一返回 `{code, name}` 结构」
3. 时间字段用 `LocalDateTime` 未配置 ISO-8601 序列化，不符合规则 10.7
4. `UserController` 直接注入 `JwtTokenProvider` 签发 token，业务编排越界（规则 1.1）

## 修复 1：应用层 MapStruct Converter

### 现状

`WatchlistApplication`/`AnimeApplication`/`ReviewApplication`/`UserApplication` 各自持有 `private toBO()` 手写转换方法。

### 方案

按上下文在 `anitrack-application/converter/` 下拆分 4 个 MapStruct Converter 接口：

- `AnimeConverter`：`Anime anime2BO(Anime anime)` 等
- `WatchlistConverter`：`WatchlistItemBO watchlistItem2BO(WatchlistItem item)` 等
- `ReviewConverter`：`ReviewBO review2BO(Review review)` 等
- `UserConverter`：`UserBO user2BO(User user)` 等

均标注 `@Mapper(componentModel = "spring")`，作为 Spring Bean 注入对应 Application。各 Application 删除手写 `toBO()`，改为注入对应 Converter 调用。

### 聚合根强封装处理

聚合根（`Anime`/`WatchlistItem`/`Review`/`User`）均为 `@Builder(access = PRIVATE)`，MapStruct 无法反射构造。沿用 `infra` 层既有模式：Converter 内手写 `default` 方法显式调用聚合根的 `reconstitute(...)` 工厂方法（仅 toBO 方向，BO 是普通 `@Builder` 可正常映射）。

> `toBO` 方向：源是聚合根（有 getter），目标是 BO（`@Builder` 公开），MapStruct 可自动映射字段，无需手写。仅当字段名/类型不一致时才写 `default` 方法。

### 命名

方法命名遵循 `xxx2Yyy`（如 `watchlistItem2BO`、`user2BO`），与项目规则方法命名一致。

## 修复 2：Web 响应枚举 `{code, name}`

### 现状

`WatchlistItemResponse.status` 为 `WatchStatus` 枚举，Jackson 序列化为 `name()` 字符串。

### 方案

新增 `anitrack-starter/response/vo/EnumVO.java`：

```java
@Getter
@Builder
public class EnumVO {
    private final Integer code;
    private final String name;
}
```

`WatchStatus` 枚举当前无 `code` 字段（仅 `name()`），需新增 `Integer code`（`WANT_TO_WATCH=1/WATCHING=2/WATCHED=3/DROPPED=4`），构造方式改为 `WatchStatus(int code)` + 字段 + getter。

`WatchlistItemResponse` 改为：

```java
private EnumVO status;  // {code, name}
```

由 `HttpConverter` 或 starter 层 Converter 负责把 `WatchStatus` 转成 `EnumVO`。

### 涉及范围

- 仅 `WatchlistItemResponse`（其他 Response 暂无枚举字段）
- 若 `ReviewResponse`/`UserInfoResponse` 后续有枚举字段再复用 `EnumVO`

> 字段名 `status` 保留（规则 10.8「code 后缀」针对的是平铺的枚举编码字段，`{code,name}` 嵌套对象字段名用枚举语义名即可）。

## 修复 3：全局 Jackson ISO-8601 时间序列化

### 现状

多个 Response 含 `LocalDateTime` 字段，未配置 Jackson 序列化，默认输出 `[年,月,日,时,分,秒]` 数组。

### 方案

在 `anitrack-starter/config/` 新增 `JacksonConfig.java`：

```java
@Configuration
public class JacksonConfig {
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> builder
            .modulesToInstall(new JavaTimeModule())
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
```

效果：所有 `LocalDateTime` 序列化为 ISO-8601 字符串（如 `2026-07-07T12:34:56`）。

### 验证

- 现有 Web 集成测试中涉及时间字段的断言需调整为字符串断言
- `@WebMvcTest` 切片下 JacksonConfig 需被加载（`@Import(JacksonConfig.class)` 或确认组件扫描覆盖）

## 修复 4：JwtTokenProvider 下沉

### 现状

`UserController.login()` 注入 `JwtTokenProvider` 调用 `generateToken(userId)`。

### 方案

`UserApplication.login()` 返回值由 `UserBO` 改为 `LoginBO`：

```java
@Getter
@Builder
public class LoginBO {
    private final UserBO user;
    private final String token;
}
```

`UserApplication` 注入 `JwtTokenProvider`，在 `login()` 内完成密码校验后签发 token，封装进 `LoginBO` 返回。

`UserController.login()` 改为：

```java
public ResponseResult<LoginResponse> login(@Valid @RequestBody UserLoginReq req) {
    LoginBO loginBO = userApplication.login(httpConverter.req2BO(req));
    return ResponseResult.success(httpConverter.toLoginResponse(loginBO));
}
```

`HttpConverter.toLoginResponse` 改签名接收 `LoginBO`（替代当前的 `(String token, UserBO user)`）。

`UserController` 移除 `JwtTokenProvider` 依赖。

### 依赖方向

`JwtTokenProvider` 位于 `infra.auth`，`UserApplication` 在 `application` 层。注入后 `application` 模块需在 pom 中可见 `infra` 的 `JwtTokenProvider` 类。

> **问题**：项目规则要求 `application` 不直接依赖 `infrastructure`（依赖倒置）。`JwtTokenProvider` 是基础设施实现类，应用层直接注入会违反依赖方向。

### 解决：在 domain 层定义 TokenProvider 接口

在 `domain.user`（或 `domain.common`）定义 `TokenProvider` 接口：

```java
public interface TokenProvider {
    String generateToken(Long userId);
}
```

`JwtTokenProvider`（infra）实现该接口。`UserApplication` 依赖 `TokenProvider` 接口而非实现类。依赖方向合规。

> 规则要求「领域层不依赖 Spring 框架类型」，`TokenProvider` 是纯接口无 Spring 依赖，放 domain 层合规。

## 涉及修改的文件

### 新增

- `anitrack-application/converter/AnimeConverter.java`
- `anitrack-application/converter/WatchlistConverter.java`
- `anitrack-application/converter/ReviewConverter.java`
- `anitrack-application/converter/UserConverter.java`
- `anitrack-application/model/LoginBO.java`
- `anitrack-domain/src/main/java/com/anitrack/domain/user/service/TokenProvider.java`（或 `domain/common`）
- `anitrack-starter/response/vo/EnumVO.java`
- `anitrack-starter/config/JacksonConfig.java`

### 修改

- `WatchStatus.java` —— 补 `code` 字段（若无）
- `WatchlistItemResponse.java` —— `status` 改 `EnumVO`
- 4 个 Application —— 删 `toBO()`，注入 Converter；`UserApplication.login` 返回 `LoginBO`，注入 `TokenProvider`
- `UserController.java` —— 移除 `JwtTokenProvider`，改用 `LoginBO`
- `HttpConverter.java` —— `toLoginResponse` 改签名；新增 `WatchStatus` → `EnumVO` 转换；`req2BO` 命名审视（见下）
- `JwtTokenProvider.java` —— 实现 `TokenProvider` 接口

### 测试

- `WatchlistApplicationTest`/`AnimeApplicationTest`/`ReviewApplicationTest`/`UserApplicationTest` —— mock Converter 替代原 toBO 断言
- `WatchlistControllerTest` —— 时间字段断言改 ISO-8601 字符串；status 断言改 `{code,name}`
- `UserControllerTest` —— login 测试改断言 `LoginBO`
- 新增 `JacksonConfig` 下时间序列化测试（可选）

## 提交策略

分批提交（用户选择）：

1. commit 1：修复 1（应用层 Converter）—— 4 Converter + 4 Application 改造 + 测试
2. commit 2：修复 2 + 3（Web 枚举 + 时间序列化）—— EnumVO + WatchStatus.code + WatchlistItemResponse + JacksonConfig + 测试
3. commit 3：修复 4（JWT 下沉）—— TokenProvider 接口 + LoginBO + UserApplication + UserController + JwtTokenProvider 实现 + 测试

每个 commit 独立可编译、测试通过。

## 不在本次范围

- 不处理低级别偏差（`AnitrackAppException.build()`、`UserDomainService`、日志、`@Valid`/`@Slf4j`/`@Data` 命名、MyBatis `jdbcType` 等）
- 不调整持久化层
- 不新增 API 端点或改请求响应结构（除 `status` 字段值形态从字符串变 `{code,name}`）
- `HttpConverter.req2BO` 命名不符 `xxx2Yyy` 的问题：本次修复 4 涉及 `HttpConverter` 改动，顺手把 `req2BO` 重命名为 `userRegisterReq2BO`/`userLoginReq2BO`，纳入 commit 3
