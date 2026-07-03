# anitrack 应用层规范

## 一、应用层职责与定位

应用服务（`XxxApplication`）只做编排：组装领域对象、调用领域服务/仓储、控制事务边界，**不写业务规则**。业务规则应下沉到领域层（聚合根方法或 `XxxDomainService`）。

## 二、应用层类结构规范

### 2.1 应用服务类

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class WatchlistApplication {
    private final WatchlistRepo watchlistRepo;
    private final WatchlistDomainService watchlistDomainService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void changeStatus(Long userId, Long animeId, WatchStatus newStatus) {
        WatchlistItem item = watchlistRepo.getByUserAndAnime(userId, animeId);
        WatchStatusChangedEvent event = item.changeStatus(newStatus);
        watchlistRepo.save(item);
        eventPublisher.publishEvent(event);
    }
}
```

`publishEvent` 调用本身发生在事务方法体内（提交前），但监听器的实际执行需要等到事务提交后才发生：监听器方法使用 `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` 而不是普通 `@EventListener`，由 Spring 保证在事务提交成功后才回调，避免监听器读到未提交的数据或在事务回滚后仍被误触发。

### 2.2 应用层模型（XxxBO）

应用层内部流转的数据结构使用 `Xxx` + `BO` 命名，与领域模型、请求/响应对象区分开：

```java
@Data
@Builder
public class WatchlistBO {
    private Long id;
    private Long userId;
    private Long animeId;
    private WatchStatus status;
    private Integer currentEpisode;
}
```

### 2.3 请求响应模型

跨模块传递的请求/响应对象命名为 `Xxx` + `ReqBO` / `Xxx` + `RespBO`，与 Web 层的 `XxxReq`/`XxxResponse` 区分（Web层对象在 `anitrack-starter` 模块，应用层对象在 `anitrack-application` 模块）。

### 2.4 转换器（ApplicationConverter）

使用 MapStruct 生成应用层对象与领域模型之间的转换代码：

```java
@Mapper(componentModel = "spring")
public interface ApplicationConverter {
    WatchlistBO toBO(WatchlistItem item);
}
```

### 2.5 组装器（XxxAssembler）

组装跨聚合的复杂返回结构，例如"我的追番列表"需要拼接 `WatchlistItem` + `Anime` 标题封面：

```java
@Component
@RequiredArgsConstructor
public class WatchlistAssembler {
    public List<WatchlistDetailBO> assemble(List<WatchlistItem> items, Map<Long, Anime> animeMap) {
        return items.stream()
            .map(item -> WatchlistDetailBO.builder()
                .watchlistItem(item)
                .animeTitle(animeMap.get(item.getAnimeId()).getTitle())
                .animeCoverUrl(animeMap.get(item.getAnimeId()).getCoverUrl())
                .build())
            .toList();
    }
}
```

## 三、应用层方法规范

### 3.1 命名规范

方法命名遵循 `anitrack-project-rules.md` 中定义的通用命名规范（`getXxx`/`listXxx`/`addXxx`/`updateXxx` 等）。

### 3.2 参数校验

应用层方法入参的业务性校验（非 Bean Validation 覆盖的格式校验）失败时，抛出 `AnitrackAppException`：

```java
if (watchStatus == null) {
    throw AnitrackAppException.build("追番状态不能为空");
}
```

### 3.3 事务管理

写操作方法统一使用 `@Transactional` 声明事务边界，事务范围只覆盖数据库写操作，不包含外部 HTTP 调用（如 Bangumi API）。

### 3.4 日志记录

关键编排步骤记录 INFO 日志，携带 userId、animeId 等业务标识，便于问题排查。

## 四、异常处理规范

### 4.1 异常类型

统一使用 `AnitrackAppException`（继承 `RuntimeException`），不允许应用层直接抛出领域异常之外的自定义异常类型。

### 4.2 异常定义

```java
@Getter
@AllArgsConstructor
public enum AppExceptionEnum {
    WATCHLIST_ITEM_NOT_FOUND(40001, "追番记录不存在"),
    INVALID_WATCH_STATUS(40002, "非法的追番状态");

    private final int code;
    private final String message;
}
```

### 4.3 异常抛出

```java
public class AnitrackAppException extends RuntimeException {
    private final int code;

    private AnitrackAppException(int code, String message) {
        super(message);
        this.code = code;
    }

    public static AnitrackAppException build(String messageFormat, Object... args) {
        return new AnitrackAppException(50000, String.format(messageFormat, args));
    }

    public static AnitrackAppException build(AppExceptionEnum exceptionEnum) {
        return new AnitrackAppException(exceptionEnum.getCode(), exceptionEnum.getMessage());
    }
}
```

## 五、业务逻辑实现规范

应用层不实现业务规则判断（如状态转移是否合法），只负责调用领域层暴露的方法并处理调用结果。

## 六、代码质量规范

### 单元测试

应用层单元测试使用 JUnit5 + Mockito，详见 `anitrack-unittest-rules.md`。

## 七、依赖注入规范

统一使用构造器注入（Lombok `@RequiredArgsConstructor` + `final` 字段），不使用字段注入（`@Autowired` 直接标注在字段上）。

## 八、配置管理规范

配置项统一使用 Spring 标准注解读取，不引入自定义配置注解：

```java
@Value("${anitrack.bangumi.api-base-url}")
private String bangumiApiBaseUrl;
```

多个相关配置项使用 `@ConfigurationProperties` 聚合：

```java
@ConfigurationProperties(prefix = "anitrack.bangumi")
@Data
public class BangumiProperties {
    private String apiBaseUrl;
    private Integer timeoutMs;
}
```

## 九、应用层与其他层交互规范

- 应用层依赖 `domain` 层定义的仓储/网关接口完成编排，不直接依赖 `infrastructure` 层的实现类
- 应用层不直接操作 Web 层的请求/响应对象（`XxxReq`/`XxxResponse`），由 `starter` 模块的 `HttpConverter` 负责转换
- 应用层发布领域事件时使用 Spring `ApplicationEventPublisher`，事件监听器（`@TransactionalEventListener`）同样放在 `application` 层或 `starter` 层，不放在 `domain` 层
