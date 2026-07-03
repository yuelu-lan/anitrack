# anitrack 领域层规范

## 1. 领域模型设计（充血模型）

- 聚合根、实体类使用充血模型：业务规则、状态转移逻辑写在领域模型内部的方法中，而非外部的 Service 类里
- 例如 `WatchlistItem.changeStatus(WatchStatus newStatus)` 内部完成合法性校验并变更状态，而不是由外部代码先查询状态、再调用 setter 强行赋值
- 聚合根内部字段通过构造方法或工厂方法保证初始状态合法，不暴露无校验的 setter

```java
public class WatchlistItem {
    private Long id;
    private Long userId;
    private Long animeId;
    private WatchStatus status;
    private Integer currentEpisode;

    private static final Map<WatchStatus, Set<WatchStatus>> TRANSITIONS = Map.of(
        WatchStatus.WANT_TO_WATCH, Set.of(WatchStatus.WATCHING, WatchStatus.DROPPED),
        WatchStatus.WATCHING, Set.of(WatchStatus.WATCHED, WatchStatus.DROPPED),
        WatchStatus.WATCHED, Set.of(WatchStatus.DROPPED),
        WatchStatus.DROPPED, Set.of(WatchStatus.WANT_TO_WATCH, WatchStatus.WATCHING)
    );

    public WatchStatusChangedEvent changeStatus(WatchStatus newStatus) {
        if (!TRANSITIONS.getOrDefault(this.status, Set.of()).contains(newStatus)) {
            throw new IllegalWatchStatusTransitionException(this.status, newStatus);
        }
        WatchStatus oldStatus = this.status;
        this.status = newStatus;
        return new WatchStatusChangedEvent(this.userId, this.animeId, oldStatus, newStatus);
    }
}
```

## 2. 领域服务（XxxDomainService）

- 用于承载**跨聚合、跨上下文**的业务规则，单个聚合根内部能完成的逻辑不需要领域服务
- 命名规范：`Xxx` + `DomainService`，如 `WatchlistDomainService`、`ReviewDomainService`
- 领域服务只依赖 `domain` 层定义的仓储接口（`XxxRepo`），不依赖 `infrastructure` 或 `application`
- 领域服务方法本身不声明事务，跨聚合写操作的原子性由调用方 `XxxApplication` 方法上的 `@Transactional` 保证（详见 `anitrack-persist-rules.md` 事务管理一节）

```java
public class WatchlistDomainService {
    private final WatchlistRepo watchlistRepo;
    private final AnimeRepo animeRepo;

    public void updateProgress(Long userId, Long animeId, Integer episode) {
        WatchlistItem item = watchlistRepo.getByUserAndAnime(userId, animeId);
        Anime anime = animeRepo.getById(animeId);
        item.updateProgress(episode, anime.getTotalEpisodes());
        watchlistRepo.save(item);
    }
}
```

## 3. 仓储接口（XxxRepo）

- 仓储接口定义在 `domain` 层对应上下文的 `repo/` 包下，只暴露领域语言的方法（如 `getByUserAndAnime`），不暴露持久化细节（如 SQL、分页参数对象）
- 命名规范：`Xxx` + `Repo`，如 `WatchlistRepo`、`AnimeRepo`
- 返回值与参数类型必须是领域模型（如 `WatchlistItem`），不能是 PO 对象

```java
public interface WatchlistRepo {
    WatchlistItem getByUserAndAnime(Long userId, Long animeId);
    void save(WatchlistItem item);
    List<WatchlistItem> listByUser(Long userId);
}
```

## 4. 领域事件

- 领域事件类命名规范：`Xxx` + `Event`，如 `WatchStatusChangedEvent`
- 事件对象是不可变值对象，只包含发生了什么，不包含业务逻辑
- 聚合根内部方法**只产生事件对象并返回**，不直接发布事件；事件的实际发布（如通过 `ApplicationEventPublisher`）由 `application` 层完成，监听器使用 `@TransactionalEventListener` 确保在事务提交后才执行，避免 `domain` 层依赖 Spring 类型

```java
public record WatchStatusChangedEvent(
    Long userId,
    Long animeId,
    WatchStatus oldStatus,
    WatchStatus newStatus
) {}
```

## 5. 枚举与值对象

- 状态类字段用枚举建模（如 `WatchStatus`、`PostStatus`），不使用魔法字符串或整数
- 枚举需要携带状态转移表时，转移表定义为枚举类或聚合根内部的 `static final` 字段，不散落在多处
- 值对象（如金额、坐标等无独立生命周期的概念）使用不可变类或 record 建模，不使用可变的普通 JavaBean

---

本规范只覆盖领域层（`anitrack-domain` 模块）。持久化层规范（PO/Mapper/XML/仓储实现）请参考 `anitrack-persist-rules.md`。
