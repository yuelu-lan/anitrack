# anitrack Web层规范

## 一、Controller层职责与定位

### 1.1 基本职责

- 接收 HTTP 请求，做参数校验（`@Valid`）
- 调用应用层（`XxxApplication`）完成业务编排
- 将应用层返回结果转换为响应对象并包装为统一响应体

### 1.2 不应承担的职责

- 不写业务规则判断
- 不直接访问仓储或领域层对象
- 不在 Controller 中做数据库事务控制

## 二、Controller类结构规范

### 2.1 类定义

```java
@RestController
@RequestMapping("/api/watchlist")
@Slf4j
@RequiredArgsConstructor
public class WatchlistController {
    private final WatchlistApplication watchlistApplication;
    private final HttpConverter httpConverter;
}
```

### 2.2 URL路径规范

统一前缀 `/api/{模块}`，如 `/api/watchlist/add`、`/api/watchlist/change_status`、`/api/anime/search`。除文件下载类接口使用 GET 外，其余接口统一使用 POST。

### 2.3 请求处理方法

```java
@PostMapping("/add")
public ResponseResult<Void> add(@Valid @RequestBody WatchlistAddReq req) {
    WatchlistAddBO bo = httpConverter.watchlistAddReq2BO(req, UserContextHolder.getUserId());
    watchlistApplication.addToWatchlist(bo);
    return ResponseResult.success();
}
```

## 三、请求响应对象规范

### 3.1 请求对象

命名规范：`Xxx` + `Req`，字段使用 Jakarta Bean Validation 注解校验：

```java
@Data
public class WatchlistAddReq {
    @NotNull(message = "番剧ID不能为空")
    private Long animeId;
}
```

### 3.2 分页请求对象

```java
@Data
public class PageInfoRequest {
    @NotNull
    @Min(1)
    private Integer pageNum;

    @NotNull
    @Min(1)
    @Max(100)
    private Integer pageSize;
}
```

### 3.3 响应对象

统一使用 `ResponseResult` 包装：

```java
@Data
public class ResponseResult<T> {
    private Integer status;
    private T data;
    private String message;

    public static <T> ResponseResult<T> success(T data) {
        ResponseResult<T> result = new ResponseResult<>();
        result.setStatus(1);
        result.setData(data);
        return result;
    }

    public static <T> ResponseResult<T> success() {
        return success(null);
    }

    public static <T> ResponseResult<T> fail(String message) {
        ResponseResult<T> result = new ResponseResult<>();
        result.setStatus(0);
        result.setMessage(message);
        return result;
    }
}
```

## 四、参数校验规范

### 4.1 请求对象校验

Controller 方法参数使用 `@Valid` 触发 Bean Validation：

```java
public ResponseResult<Void> add(@Valid @RequestBody WatchlistAddReq req) { ... }
```

### 4.2 常用校验注解

`@NotNull`、`@NotBlank`、`@NotEmpty`、`@Min`/`@Max`、`@Size`、`@Pattern`。

### 4.3 自定义校验逻辑

跨字段校验通过自定义 `ConstraintValidator` 实现，简单场景直接在应用层做业务校验并抛 `AnitrackAppException`。

## 五、异常处理规范

### 5.1 全局异常处理

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(AnitrackAppException.class)
    public ResponseResult<Void> handleAppException(AnitrackAppException e) {
        log.warn("应用层异常: {}", e.getMessage());
        return ResponseResult.fail(e.getMessage());
    }

    @ExceptionHandler(AnitrackDomainException.class)
    public ResponseResult<Void> handleDomainException(AnitrackDomainException e) {
        log.warn("领域层异常: {}", e.getMessage());
        return ResponseResult.fail(e.getMessage());
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseResult<Void> handleValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldError().getDefaultMessage();
        return ResponseResult.fail(message);
    }

    @ExceptionHandler(Exception.class)
    public ResponseResult<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return ResponseResult.fail("系统异常，请稍后重试");
    }
}
```

### 5.2 异常类型处理

按四类分别处理：应用层异常（`AnitrackAppException`）、领域层异常（`AnitrackDomainException` 及其子类如 `IllegalWatchStatusTransitionException`）、系统异常（兜底 `Exception`）统一返回 HTTP 200 + 业务状态码；参数校验异常（`MethodArgumentNotValidException`）请求本身不合法，通过 `@ResponseStatus(HttpStatus.BAD_REQUEST)` 返回 HTTP 400 + 业务状态码。

## 六、数据转换规范

### 6.1 请求转换

```java
@Component
public class HttpConverter {
    public WatchlistAddBO watchlistAddReq2BO(WatchlistAddReq req, Long userId) {
        return WatchlistAddBO.builder()
            .userId(userId)
            .animeId(req.getAnimeId())
            .build();
    }
}
```

### 6.2 响应转换

```java
public WatchlistResponse watchlistBO2Response(WatchlistBO bo) {
    return WatchlistResponse.builder()
        .id(bo.getId())
        .status(bo.getStatus().name())
        .currentEpisode(bo.getCurrentEpisode())
        .build();
}
```

## 七、用户认证与授权规范

anitrack 使用 Spring Security + jjwt 实现无状态 JWT 鉴权。

### 7.1 UserContextHolder（ThreadLocal）

```java
public class UserContextHolder {
    private static final ThreadLocal<Long> USER_ID_HOLDER = new ThreadLocal<>();

    public static void setUserId(Long userId) {
        USER_ID_HOLDER.set(userId);
    }

    public static Long getUserId() {
        Long userId = USER_ID_HOLDER.get();
        if (userId == null) {
            throw new IllegalStateException("当前上下文中不存在登录用户");
        }
        return userId;
    }

    public static void clear() {
        USER_ID_HOLDER.remove();
    }
}
```

### 7.2 JWT鉴权拦截器

```java
@Component
@RequiredArgsConstructor
public class JwtAuthInterceptor implements HandlerInterceptor {
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = request.getHeader("Authorization");
        if (token == null || !jwtTokenProvider.validate(token)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        }
        Long userId = jwtTokenProvider.getUserId(token);
        UserContextHolder.setUserId(userId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContextHolder.clear();
    }
}
```

### 7.3 Controller 中获取当前用户

```java
Long userId = UserContextHolder.getUserId();
```

不允许在 Controller 之外的其他层（如领域层）直接调用 `UserContextHolder`；应用层如需当前用户信息，由 Controller 层取出后作为参数传入应用服务方法。

### 7.4 密码加密

用户注册/登录密码统一使用 `BCryptPasswordEncoder` 加密存储与校验，不使用 MD5 或明文。

### 7.5 权限校验

anitrack 使用简单 RBAC（`user` + `role` 字段），角色校验通过拦截器中的角色判断实现。

## 八、日志记录规范

### 8.1 日志级别使用

- INFO：正常业务流程关键节点
- WARN：预期内的业务异常（如参数不合法）
- ERROR：未预期的系统异常

### 8.2 日志内容

日志需携带 traceId（通过 MDC）、userId 等关键上下文，避免打印完整请求体中的敏感字段（如密码）。

## 九、代码质量规范

### 9.1 代码风格

遵循 Google Java Style 或阿里巴巴 Java 开发手册风格，统一使用 4 空格缩进。

### 9.2 命名规范

遵循 `anitrack-project-rules.md` 中定义的通用命名规范。

### 9.3 注释规范

只在业务规则非显而易见时添加注释，不写描述"做了什么"的注释。

## 十、HTTP 返回规范

### 10.1 统一响应格式

`ResponseResult { status, data, message }`，`status` 为 1 表示成功、0 表示失败；`message` 仅在失败时携带错误信息，成功时为 `null`；`data` 仅在成功时携带业务数据，失败时为 `null`。鉴权失败由 `JwtAuthInterceptor` 直接返回 HTTP 401（`response.setStatus(HttpStatus.UNAUTHORIZED.value())`），不经过 `ResponseResult`。

### 10.2 请求方式规范

文件下载类接口使用 GET，其余接口统一使用 POST。

### 10.3 成功响应规范

- 返回数据对象：`ResponseResult.success(data)`
- 返回成功消息：`ResponseResult.success()`（`data` 为 null）
- 返回成功字符串：`ResponseResult.success("操作成功")`

### 10.4 失败响应规范

- 返回错误消息：`ResponseResult.fail("番剧不存在")`
- 返回带错误码的失败消息：结合 `AppExceptionEnum` 携带 `code`

### 10.5 分页响应规范

```java
@Data
@Builder
public class PageResponse<T> {
    private Integer pageNum;
    private Integer pageSize;
    private Long total;
    private List<T> list;
}
```

### 10.6 下拉列表响应规范

```java
@Data
@Builder
public class OptionResponse {
    private String code;
    private String name;
}
```

例如返回 `WatchStatus` 的可选项列表：`[{code: "WATCHING", name: "在看"}, ...]`。

### 10.7 数据类型规范

- **时间类型**：统一使用 UTC 时间戳（毫秒）或 ISO-8601 字符串，前后端约定一致
- **枚举类型**：统一返回 `{code, name}` 结构
- **数字类型**：超过 15 位的数字（如雪花ID）转为字符串返回，避免前端精度丢失
- **人员信息**：仅返回 anitrack User 上下文实际存在的字段（`id`、`username`、`nickname`、`avatarUrl`）

```java
@Data
@Builder
public class UserInfoResponse {
    private Long id;
    private String username;
    private String nickname;
    private String avatarUrl;
}
```

- **空值处理**：对象类型字段返回 null；列表类型字段返回空数组 `[]` 而非 null；字符串类型字段返回空字符串 `""` 而非 null

### 10.8 系统一致性规范

- 字段命名一致性：枚举编码字段统一以 `Code` 结尾，枚举名称字段统一以 `Name` 结尾，如 `statusCode`/`statusName`
- 不同数据类型字段命名需能从名称推断类型，如时间字段以 `Time` 结尾，数量字段以 `Count` 结尾
- 枚举值一旦发布上线，不允许修改其 `code` 含义，只能新增

### 10.9 搜索参数处理规范

搜索类参数统一处理空字符串、null、参数不存在三种情况为"不过滤该条件"，不视为查询异常。
