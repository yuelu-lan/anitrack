# anitrack Phase 1b：追番核心（Watchlist，状态机）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现追番核心（`Watchlist`）上下文：用户对番剧的追看状态机（`WANT_TO_WATCH/WATCHING/WATCHED/DROPPED`）、观看进度管理（含跨上下文的 `totalEpisodes` 校验），并暴露加入追番/变更状态/更新进度/查询详情/查询列表 5 个接口。

**Architecture:** `anitrack-domain.watchlist` 定义 `WatchlistItem` 聚合根（内置状态转移表与进度校验）、`WatchlistRepo` 仓储接口、`WatchlistDomainService`（跨上下文编排：查询 `domain.anime.repo.AnimeRepo` 校验番剧存在性/取总集数）与相关领域异常；`anitrack-infrastructure` 用 MyBatis 原生 XML 实现 `WatchlistRepo`，并给已有 `AnimeRepo` 补充 `listByIds` 批量查询；`anitrack-application` 的 `WatchlistApplication` 编排 5 个用例、把领域异常转译为 `AnitrackAppException`、通过 `ApplicationEventPublisher` 发布 `WatchStatusChangedEvent`（本期无监听器），`WatchlistAssembler` 拼接 `WatchlistItem + Anime` 生成列表展示模型；`anitrack-starter` 承载 `WatchlistController` 与请求/响应对象。

**Tech Stack:** Java 17、Spring Boot 3.5.3、MyBatis 原生 XML（沿用 `t_anime`/`t_user` 同款结构）、MapStruct 1.6.3（`WatchlistItemPO <-> WatchlistItem`）、Lombok、Spring `ApplicationEventPublisher`（领域事件发布）、JUnit5 + Mockito + AssertJ、`@WebMvcTest` + MockMvc。

## Global Constraints

- 依赖方向：`starter → application → domain`；`infrastructure → domain`；仓储接口定义在 `domain`，`infrastructure` 实现；`application` 不直接依赖 `infrastructure`（`docs/rules/anitrack-project-rules.md`）
- 聚合根内部字段通过构造方法或工厂方法保证初始状态合法，不暴露无校验的 setter；状态转移逻辑写在聚合根方法内部（`docs/rules/anitrack-model-rules.md`）
- 跨聚合、跨上下文的业务规则放在 `XxxDomainService` 里，只依赖 `domain` 层仓储接口，不依赖 `infrastructure`/`application`；单个聚合根内部能完成的逻辑不需要领域服务（`docs/rules/anitrack-model-rules.md`）
- 领域层不依赖任何 Spring 框架类型；`WatchlistDomainService` 是不带任何 Spring 注解的纯类，其 Bean 注册通过 `anitrack-application` 层的 `@Configuration` 类完成（本 spec 新增架构约定，因为它是本项目第一个 `XxxDomainService`）
- 领域事件对象由聚合根方法产生并返回，不在聚合内部发布；实际发布由 `application` 层通过 `ApplicationEventPublisher` 完成（`docs/rules/anitrack-model-rules.md`）
- 状态类字段用枚举建模，不使用魔法字符串/整数；状态转移表定义为聚合根内部 `static final` 字段（`docs/rules/anitrack-model-rules.md`）
- `WatchlistItem` 字段：`id, userId, animeId, status, currentEpisode, updateTime`；合法状态转移表：`WANT_TO_WATCH→WATCHING`、`WATCHING→WATCHED/DROPPED`、`DROPPED→WATCHING`，`WATCHED` 为终态（本 spec：`docs/superpowers/specs/2026-07-06-anitrack-phase1b-watchlist-design.md`，下称"本 spec"）
- `updateProgress` 校验：仅 `WATCHING` 状态允许调用；`episode` 必须 > 0；`episode` 不能小于当前 `currentEpisode`（不允许倒退）；`totalEpisodes > 0` 时 `episode` 不能超过它，`totalEpisodes == 0` 视为未知，不做上限校验（本 spec）
- "从追番列表移除"不是独立接口，等同于 `changeStatus(DROPPED)`；不做分页；不给 `WatchlistItem` 加评分字段；不实现 `WatchStatusChangedEvent` 的监听器（本 spec）
- 一个用户对同一番剧只能有一条 `WatchlistItem`（`userId + animeId` 唯一），由 `t_watchlist_item` 表的 `uk_user_anime` 唯一索引与领域层的重复校验双重兜底（本 spec）
- 不使用 MyBatis-Plus，不允许联表查询，不允许使用外键（`docs/rules/anitrack-project-rules.md`）
- 断言统一使用 AssertJ `assertThat()`，禁止 `Assertions.assertEquals()`；Mock 交互验证必须明确指定次数（`times(1)` 等），不 Mock 值对象/DTO（`docs/rules/anitrack-unittest-rules.md`）
- 应用层统一异常使用 `AnitrackAppException(AppExceptionEnum)` 构造函数；`AppExceptionEnum` 新增常量延续 40xxx 编码段，Watchlist 上下文使用 402xx（沿用已有 `AnimeApplication`/`UserApplication` 实现）
- 版本号统一由根 `pom.xml` 的 `<dependencyManagement>` 管理，子模块 `<dependency>` 不单独指定 `<version>`（`docs/rules/anitrack-init-dependency-rules.md`）
- 涉及 `git commit` 等提交操作，必须先向用户说明，由用户决定是否执行，不得自行提交（项目 `CLAUDE.md`）
- 本机默认 `PATH` 上的 `mvn` 解析到 `/Users/ywy/opt/apache-maven-3.6.0/bin/mvn`（绑定 JDK 8），无法编译本项目（要求 JDK 17 + Maven ≥3.6.3）。执行本计划所有 `mvn` 命令前需先执行：
  ```bash
  export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
  export PATH=/opt/homebrew/opt/maven/bin:$PATH
  ```
  （已验证 `mvn -v` 输出 `Apache Maven 3.9.16`／`Java version: 17.0.10` 即为正确环境）

---

## File Structure

```
anitrack-domain/src/main/java/com/anitrack/domain/
├── anime/exception/AnimeNotFoundException.java                  # Create: 跨上下文校验番剧不存在时抛出
└── watchlist/
    ├── enums/WatchStatus.java
    ├── model/WatchlistItem.java                                 # 聚合根：create()/reconstitute()/changeStatus()/updateProgress()
    ├── model/WatchStatusChangedEvent.java                       # record 事件
    ├── repo/WatchlistRepo.java                                  # 接口：getByUserAndAnime/listByUser/add/update
    ├── service/WatchlistDomainService.java                      # 跨上下文：addToWatchlist/updateProgress
    └── exception/
        ├── IllegalWatchStatusTransitionException.java
        ├── IllegalWatchProgressException.java
        ├── WatchlistItemAlreadyExistsException.java
        └── WatchlistItemNotFoundException.java
anitrack-domain/src/test/java/com/anitrack/domain/watchlist/
├── model/WatchlistItemTest.java
└── service/WatchlistDomainServiceTest.java

anitrack-starter/src/main/resources/db/migration/V3__create_watchlist_item_table.sql
anitrack-infrastructure/src/main/java/com/anitrack/infra/
├── dal/po/WatchlistItemPO.java
├── dal/mapper/WatchlistItemMapper.java                          # + resources/mapper/WatchlistItemMapper.xml
├── dal/mapper/AnimeMapper.java                                  # Modify: 新增 selectByIds
├── converter/WatchlistItemConverter.java                        # MapStruct: WatchlistItemPO <-> WatchlistItem
├── repo/WatchlistRepoImpl.java                                  # implements WatchlistRepo
└── repo/AnimeRepoImpl.java                                      # Modify: 新增 listByIds 实现
anitrack-infrastructure/src/main/resources/mapper/AnimeMapper.xml # Modify: 新增 selectByIds
anitrack-infrastructure/src/test/java/com/anitrack/infra/repo/
├── WatchlistRepoImplTest.java
└── AnimeRepoImplTest.java                                       # Modify: 新增 listByIds 测试

anitrack-application/src/main/java/com/anitrack/application/
├── exception/AppExceptionEnum.java                               # Modify: 新增 WATCHLIST_* 四个常量
├── model/WatchlistItemBO.java
├── model/WatchlistItemViewBO.java
├── assembler/WatchlistAssembler.java
├── config/DomainServiceConfig.java                               # @Bean 注册 WatchlistDomainService
└── service/WatchlistApplication.java
anitrack-application/src/test/java/com/anitrack/application/service/WatchlistApplicationTest.java

anitrack-starter/src/main/java/com/anitrack/starter/
├── request/WatchlistAddReq.java
├── request/WatchlistChangeStatusReq.java
├── request/WatchlistUpdateProgressReq.java
├── request/WatchlistDetailReq.java
├── request/WatchlistListReq.java
├── response/WatchlistItemResponse.java
├── response/WatchlistItemViewResponse.java
├── converter/HttpConverter.java                                  # Modify: 新增 watchlist 相关转换方法
└── controller/WatchlistController.java
anitrack-starter/src/test/java/com/anitrack/starter/controller/WatchlistControllerTest.java
```

---

### Task 1: WatchlistItem 聚合根 + WatchStatus + WatchStatusChangedEvent + 状态机/进度领域异常

**Files:**
- Create: `anitrack-domain/src/main/java/com/anitrack/domain/watchlist/enums/WatchStatus.java`
- Create: `anitrack-domain/src/main/java/com/anitrack/domain/watchlist/model/WatchStatusChangedEvent.java`
- Create: `anitrack-domain/src/main/java/com/anitrack/domain/watchlist/exception/IllegalWatchStatusTransitionException.java`
- Create: `anitrack-domain/src/main/java/com/anitrack/domain/watchlist/exception/IllegalWatchProgressException.java`
- Create: `anitrack-domain/src/main/java/com/anitrack/domain/watchlist/model/WatchlistItem.java`
- Test: `anitrack-domain/src/test/java/com/anitrack/domain/watchlist/model/WatchlistItemTest.java`

**Interfaces:**
- Produces：`WatchlistItem.create(Long userId, Long animeId): WatchlistItem`；`WatchlistItem.reconstitute(Long id, Long userId, Long animeId, WatchStatus status, Integer currentEpisode, LocalDateTime updateTime): WatchlistItem`；`WatchlistItem.changeStatus(WatchStatus newStatus): WatchStatusChangedEvent`；`WatchlistItem.updateProgress(Integer episode, Integer totalEpisodes): void`；`WatchStatusChangedEvent(Long userId, Long animeId, WatchStatus oldStatus, WatchStatus newStatus)`（record）

- [ ] **Step 1: 编写 WatchStatus 枚举**

```java
package com.anitrack.domain.watchlist.enums;

public enum WatchStatus {
    WANT_TO_WATCH,
    WATCHING,
    WATCHED,
    DROPPED
}
```

- [ ] **Step 2: 编写 WatchStatusChangedEvent**

```java
package com.anitrack.domain.watchlist.model;

import com.anitrack.domain.watchlist.enums.WatchStatus;

public record WatchStatusChangedEvent(Long userId, Long animeId, WatchStatus oldStatus, WatchStatus newStatus) {
}
```

- [ ] **Step 3: 编写状态机/进度领域异常**

```java
package com.anitrack.domain.watchlist.exception;

import com.anitrack.domain.common.AnitrackDomainException;
import com.anitrack.domain.watchlist.enums.WatchStatus;

public class IllegalWatchStatusTransitionException extends AnitrackDomainException {

    public IllegalWatchStatusTransitionException(WatchStatus from, WatchStatus to) {
        super("不允许从" + from + "状态转移至" + to + "状态");
    }
}
```

```java
package com.anitrack.domain.watchlist.exception;

import com.anitrack.domain.common.AnitrackDomainException;

public class IllegalWatchProgressException extends AnitrackDomainException {

    public IllegalWatchProgressException(String message) {
        super(message);
    }
}
```

- [ ] **Step 4: 编写失败的 WatchlistItemTest**

```java
package com.anitrack.domain.watchlist.model;

import com.anitrack.domain.watchlist.enums.WatchStatus;
import com.anitrack.domain.watchlist.exception.IllegalWatchProgressException;
import com.anitrack.domain.watchlist.exception.IllegalWatchStatusTransitionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WatchlistItemTest {

    @Test
    void create_whenCalled_shouldInitializeAsWantToWatchWithZeroProgress() {
        // when
        WatchlistItem item = WatchlistItem.create(1L, 100L);

        // then
        assertThat(item.getId()).isNull();
        assertThat(item.getUserId()).isEqualTo(1L);
        assertThat(item.getAnimeId()).isEqualTo(100L);
        assertThat(item.getStatus()).isEqualTo(WatchStatus.WANT_TO_WATCH);
        assertThat(item.getCurrentEpisode()).isZero();
    }

    @Test
    void reconstitute_whenCalled_shouldRestoreAllFields() {
        // when
        WatchlistItem item = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WATCHING, 5, null);

        // then
        assertThat(item.getId()).isEqualTo(10L);
        assertThat(item.getStatus()).isEqualTo(WatchStatus.WATCHING);
        assertThat(item.getCurrentEpisode()).isEqualTo(5);
    }

    @Test
    void changeStatus_whenWantToWatchToWatching_shouldSucceedAndReturnEvent() {
        // given
        WatchlistItem item = WatchlistItem.create(1L, 100L);

        // when
        WatchStatusChangedEvent event = item.changeStatus(WatchStatus.WATCHING);

        // then
        assertThat(item.getStatus()).isEqualTo(WatchStatus.WATCHING);
        assertThat(event.userId()).isEqualTo(1L);
        assertThat(event.animeId()).isEqualTo(100L);
        assertThat(event.oldStatus()).isEqualTo(WatchStatus.WANT_TO_WATCH);
        assertThat(event.newStatus()).isEqualTo(WatchStatus.WATCHING);
    }

    @Test
    void changeStatus_whenWatchingToWatched_shouldSucceed() {
        // given
        WatchlistItem item = WatchlistItem.create(1L, 100L);
        item.changeStatus(WatchStatus.WATCHING);

        // when
        item.changeStatus(WatchStatus.WATCHED);

        // then
        assertThat(item.getStatus()).isEqualTo(WatchStatus.WATCHED);
    }

    @Test
    void changeStatus_whenWatchingToDropped_shouldSucceed() {
        // given
        WatchlistItem item = WatchlistItem.create(1L, 100L);
        item.changeStatus(WatchStatus.WATCHING);

        // when
        item.changeStatus(WatchStatus.DROPPED);

        // then
        assertThat(item.getStatus()).isEqualTo(WatchStatus.DROPPED);
    }

    @Test
    void changeStatus_whenDroppedToWatching_shouldSucceedAndKeepProgress() {
        // given
        WatchlistItem item = WatchlistItem.create(1L, 100L);
        item.changeStatus(WatchStatus.WATCHING);
        item.updateProgress(5, 12);
        item.changeStatus(WatchStatus.DROPPED);

        // when
        item.changeStatus(WatchStatus.WATCHING);

        // then
        assertThat(item.getStatus()).isEqualTo(WatchStatus.WATCHING);
        assertThat(item.getCurrentEpisode()).isEqualTo(5);
    }

    @Test
    void changeStatus_whenWantToWatchToWatched_shouldThrowException() {
        // given
        WatchlistItem item = WatchlistItem.create(1L, 100L);

        // when & then
        assertThatThrownBy(() -> item.changeStatus(WatchStatus.WATCHED))
            .isInstanceOf(IllegalWatchStatusTransitionException.class);
    }

    @Test
    void changeStatus_whenWatchedToWatching_shouldThrowException() {
        // given
        WatchlistItem item = WatchlistItem.create(1L, 100L);
        item.changeStatus(WatchStatus.WATCHING);
        item.changeStatus(WatchStatus.WATCHED);

        // when & then
        assertThatThrownBy(() -> item.changeStatus(WatchStatus.WATCHING))
            .isInstanceOf(IllegalWatchStatusTransitionException.class);
    }

    @Test
    void changeStatus_whenWatchingToWantToWatch_shouldThrowException() {
        // given
        WatchlistItem item = WatchlistItem.create(1L, 100L);
        item.changeStatus(WatchStatus.WATCHING);

        // when & then
        assertThatThrownBy(() -> item.changeStatus(WatchStatus.WANT_TO_WATCH))
            .isInstanceOf(IllegalWatchStatusTransitionException.class);
    }

    @Test
    void updateProgress_whenStatusIsNotWatching_shouldThrowException() {
        // given
        WatchlistItem item = WatchlistItem.create(1L, 100L);

        // when & then
        assertThatThrownBy(() -> item.updateProgress(1, 12))
            .isInstanceOf(IllegalWatchProgressException.class);
    }

    @Test
    void updateProgress_whenEpisodeIsZero_shouldThrowException() {
        // given
        WatchlistItem item = WatchlistItem.create(1L, 100L);
        item.changeStatus(WatchStatus.WATCHING);

        // when & then
        assertThatThrownBy(() -> item.updateProgress(0, 12))
            .isInstanceOf(IllegalWatchProgressException.class);
    }

    @Test
    void updateProgress_whenEpisodeRegresses_shouldThrowException() {
        // given
        WatchlistItem item = WatchlistItem.create(1L, 100L);
        item.changeStatus(WatchStatus.WATCHING);
        item.updateProgress(5, 12);

        // when & then
        assertThatThrownBy(() -> item.updateProgress(3, 12))
            .isInstanceOf(IllegalWatchProgressException.class);
    }

    @Test
    void updateProgress_whenEpisodeExceedsTotalEpisodes_shouldThrowException() {
        // given
        WatchlistItem item = WatchlistItem.create(1L, 100L);
        item.changeStatus(WatchStatus.WATCHING);

        // when & then
        assertThatThrownBy(() -> item.updateProgress(13, 12))
            .isInstanceOf(IllegalWatchProgressException.class);
    }

    @Test
    void updateProgress_whenTotalEpisodesIsZero_shouldAllowAnyEpisode() {
        // given
        WatchlistItem item = WatchlistItem.create(1L, 100L);
        item.changeStatus(WatchStatus.WATCHING);

        // when
        item.updateProgress(999, 0);

        // then
        assertThat(item.getCurrentEpisode()).isEqualTo(999);
    }

    @Test
    void updateProgress_whenValid_shouldUpdateCurrentEpisode() {
        // given
        WatchlistItem item = WatchlistItem.create(1L, 100L);
        item.changeStatus(WatchStatus.WATCHING);

        // when
        item.updateProgress(5, 12);

        // then
        assertThat(item.getCurrentEpisode()).isEqualTo(5);
    }
}
```

- [ ] **Step 5: 运行测试，确认失败**

Run: `mvn -q -pl anitrack-domain -am test -Dtest=WatchlistItemTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL（`WatchlistItem` 类不存在，编译错误）

- [ ] **Step 6: 编写 WatchlistItem 聚合根**

```java
package com.anitrack.domain.watchlist.model;

import com.anitrack.domain.watchlist.enums.WatchStatus;
import com.anitrack.domain.watchlist.exception.IllegalWatchProgressException;
import com.anitrack.domain.watchlist.exception.IllegalWatchStatusTransitionException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WatchlistItem {

    private static final Map<WatchStatus, Set<WatchStatus>> TRANSITIONS = Map.of(
        WatchStatus.WANT_TO_WATCH, Set.of(WatchStatus.WATCHING),
        WatchStatus.WATCHING, Set.of(WatchStatus.WATCHED, WatchStatus.DROPPED),
        WatchStatus.WATCHED, Set.of(),
        WatchStatus.DROPPED, Set.of(WatchStatus.WATCHING)
    );

    private Long id;
    private Long userId;
    private Long animeId;
    private WatchStatus status;
    private Integer currentEpisode;
    private LocalDateTime updateTime;

    public static WatchlistItem create(Long userId, Long animeId) {
        return WatchlistItem.builder()
            .userId(userId)
            .animeId(animeId)
            .status(WatchStatus.WANT_TO_WATCH)
            .currentEpisode(0)
            .build();
    }

    public static WatchlistItem reconstitute(Long id, Long userId, Long animeId, WatchStatus status,
                                              Integer currentEpisode, LocalDateTime updateTime) {
        return WatchlistItem.builder()
            .id(id)
            .userId(userId)
            .animeId(animeId)
            .status(status)
            .currentEpisode(currentEpisode)
            .updateTime(updateTime)
            .build();
    }

    public WatchStatusChangedEvent changeStatus(WatchStatus newStatus) {
        if (!TRANSITIONS.getOrDefault(this.status, Set.of()).contains(newStatus)) {
            throw new IllegalWatchStatusTransitionException(this.status, newStatus);
        }
        WatchStatus oldStatus = this.status;
        this.status = newStatus;
        return new WatchStatusChangedEvent(this.userId, this.animeId, oldStatus, newStatus);
    }

    public void updateProgress(Integer episode, Integer totalEpisodes) {
        if (this.status != WatchStatus.WATCHING) {
            throw new IllegalWatchProgressException("只有观看中的番剧才能更新进度");
        }
        if (episode == null || episode <= 0) {
            throw new IllegalWatchProgressException("观看进度必须大于0");
        }
        if (episode < this.currentEpisode) {
            throw new IllegalWatchProgressException("观看进度不能倒退");
        }
        if (totalEpisodes != null && totalEpisodes > 0 && episode > totalEpisodes) {
            throw new IllegalWatchProgressException("观看进度不能超过总集数");
        }
        this.currentEpisode = episode;
    }
}
```

- [ ] **Step 7: 运行测试，确认通过**

Run: `mvn -q -pl anitrack-domain -am test -Dtest=WatchlistItemTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS（15 个测试通过）

- [ ] **Step 8: Commit（需先征得用户同意后再执行）**

```bash
git add anitrack-domain/src/main/java/com/anitrack/domain/watchlist/enums anitrack-domain/src/main/java/com/anitrack/domain/watchlist/model anitrack-domain/src/main/java/com/anitrack/domain/watchlist/exception anitrack-domain/src/test/java/com/anitrack/domain/watchlist/model
git commit -m "feat: 新增WatchlistItem聚合根，实现追番状态机与进度校验"
```

---

### Task 2: WatchlistRepo 接口 + AnimeNotFoundException + WatchlistDomainService（跨上下文编排）

**Files:**
- Create: `anitrack-domain/src/main/java/com/anitrack/domain/anime/exception/AnimeNotFoundException.java`
- Create: `anitrack-domain/src/main/java/com/anitrack/domain/watchlist/exception/WatchlistItemAlreadyExistsException.java`
- Create: `anitrack-domain/src/main/java/com/anitrack/domain/watchlist/exception/WatchlistItemNotFoundException.java`
- Create: `anitrack-domain/src/main/java/com/anitrack/domain/watchlist/repo/WatchlistRepo.java`
- Create: `anitrack-domain/src/main/java/com/anitrack/domain/watchlist/service/WatchlistDomainService.java`
- Test: `anitrack-domain/src/test/java/com/anitrack/domain/watchlist/service/WatchlistDomainServiceTest.java`

**Interfaces:**
- Consumes：`WatchlistItem`（Task 1）、`Anime`/`AnimeRepo`（已存在，`domain.anime.model.Anime`/`domain.anime.repo.AnimeRepo`）
- Produces：`WatchlistRepo.getByUserAndAnime(Long, Long): WatchlistItem`（查不到返回 `null`）／`listByUser(Long, WatchStatus): List<WatchlistItem>`／`add(WatchlistItem): WatchlistItem`／`update(WatchlistItem): void`；`WatchlistDomainService.addToWatchlist(Long userId, Long animeId): WatchlistItem`／`updateProgress(Long userId, Long animeId, Integer episode): WatchlistItem`，供 Task 4 的 `WatchlistRepoImpl` 与 Task 5 的 `WatchlistApplication` 使用；`AnimeNotFoundException(Long animeId)`

- [ ] **Step 1: 编写 AnimeNotFoundException（新增到已有的 anime 上下文）**

```java
package com.anitrack.domain.anime.exception;

import com.anitrack.domain.common.AnitrackDomainException;

public class AnimeNotFoundException extends AnitrackDomainException {

    public AnimeNotFoundException(Long animeId) {
        super("番剧不存在: " + animeId);
    }
}
```

- [ ] **Step 2: 编写 Watchlist 相关领域异常**

```java
package com.anitrack.domain.watchlist.exception;

import com.anitrack.domain.common.AnitrackDomainException;

public class WatchlistItemAlreadyExistsException extends AnitrackDomainException {

    public WatchlistItemAlreadyExistsException(Long userId, Long animeId) {
        super("用户" + userId + "已经追番" + animeId);
    }
}
```

```java
package com.anitrack.domain.watchlist.exception;

import com.anitrack.domain.common.AnitrackDomainException;

public class WatchlistItemNotFoundException extends AnitrackDomainException {

    public WatchlistItemNotFoundException(Long userId, Long animeId) {
        super("用户" + userId + "未追番" + animeId);
    }
}
```

- [ ] **Step 3: 编写 WatchlistRepo 接口**

```java
package com.anitrack.domain.watchlist.repo;

import com.anitrack.domain.watchlist.enums.WatchStatus;
import com.anitrack.domain.watchlist.model.WatchlistItem;

import java.util.List;

public interface WatchlistRepo {

    WatchlistItem getByUserAndAnime(Long userId, Long animeId);

    List<WatchlistItem> listByUser(Long userId, WatchStatus status);

    WatchlistItem add(WatchlistItem item);

    void update(WatchlistItem item);
}
```

- [ ] **Step 4: 编写失败的 WatchlistDomainServiceTest**

```java
package com.anitrack.domain.watchlist.service;

import com.anitrack.domain.anime.exception.AnimeNotFoundException;
import com.anitrack.domain.anime.model.Anime;
import com.anitrack.domain.anime.repo.AnimeRepo;
import com.anitrack.domain.watchlist.enums.WatchStatus;
import com.anitrack.domain.watchlist.exception.WatchlistItemAlreadyExistsException;
import com.anitrack.domain.watchlist.exception.WatchlistItemNotFoundException;
import com.anitrack.domain.watchlist.model.WatchlistItem;
import com.anitrack.domain.watchlist.repo.WatchlistRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WatchlistDomainServiceTest {

    @Mock
    private WatchlistRepo mockWatchlistRepo;

    @Mock
    private AnimeRepo mockAnimeRepo;

    @InjectMocks
    private WatchlistDomainService sut;

    private Anime createTestAnime() {
        return Anime.reconstitute(100L, 999L, "中文名", "Original Title", "http://cover.jpg", 12,
            LocalDate.of(2024, 1, 1), "简介");
    }

    @Test
    void addToWatchlist_whenAnimeExistsAndNotDuplicate_shouldCreateAndAdd() {
        // given
        when(mockAnimeRepo.getById(100L)).thenReturn(createTestAnime());
        when(mockWatchlistRepo.getByUserAndAnime(1L, 100L)).thenReturn(null);
        WatchlistItem saved = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WANT_TO_WATCH, 0, null);
        when(mockWatchlistRepo.add(any(WatchlistItem.class))).thenReturn(saved);

        // when
        WatchlistItem result = sut.addToWatchlist(1L, 100L);

        // then
        assertThat(result).isEqualTo(saved);
        verify(mockWatchlistRepo, times(1)).add(any(WatchlistItem.class));
    }

    @Test
    void addToWatchlist_whenAnimeNotFound_shouldThrowException() {
        // given
        when(mockAnimeRepo.getById(100L)).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> sut.addToWatchlist(1L, 100L))
            .isInstanceOf(AnimeNotFoundException.class);

        verify(mockWatchlistRepo, never()).add(any());
    }

    @Test
    void addToWatchlist_whenAlreadyExists_shouldThrowException() {
        // given
        when(mockAnimeRepo.getById(100L)).thenReturn(createTestAnime());
        WatchlistItem existing = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WANT_TO_WATCH, 0, null);
        when(mockWatchlistRepo.getByUserAndAnime(1L, 100L)).thenReturn(existing);

        // when & then
        assertThatThrownBy(() -> sut.addToWatchlist(1L, 100L))
            .isInstanceOf(WatchlistItemAlreadyExistsException.class);

        verify(mockWatchlistRepo, never()).add(any());
    }

    @Test
    void updateProgress_whenItemAndAnimeExist_shouldDelegateToAggregateAndUpdate() {
        // given
        WatchlistItem item = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WATCHING, 0, null);
        when(mockWatchlistRepo.getByUserAndAnime(1L, 100L)).thenReturn(item);
        when(mockAnimeRepo.getById(100L)).thenReturn(createTestAnime());

        // when
        WatchlistItem result = sut.updateProgress(1L, 100L, 5);

        // then
        assertThat(result.getCurrentEpisode()).isEqualTo(5);
        verify(mockWatchlistRepo, times(1)).update(item);
    }

    @Test
    void updateProgress_whenItemNotFound_shouldThrowException() {
        // given
        when(mockWatchlistRepo.getByUserAndAnime(1L, 100L)).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> sut.updateProgress(1L, 100L, 5))
            .isInstanceOf(WatchlistItemNotFoundException.class);

        verify(mockWatchlistRepo, never()).update(any());
    }

    @Test
    void updateProgress_whenAnimeNotFound_shouldThrowException() {
        // given
        WatchlistItem item = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WATCHING, 0, null);
        when(mockWatchlistRepo.getByUserAndAnime(1L, 100L)).thenReturn(item);
        when(mockAnimeRepo.getById(100L)).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> sut.updateProgress(1L, 100L, 5))
            .isInstanceOf(AnimeNotFoundException.class);

        verify(mockWatchlistRepo, never()).update(any());
    }
}
```

- [ ] **Step 5: 运行测试，确认失败**

Run: `mvn -q -pl anitrack-domain -am test -Dtest=WatchlistDomainServiceTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL（`WatchlistDomainService` 类不存在，编译错误）

- [ ] **Step 6: 编写 WatchlistDomainService**

```java
package com.anitrack.domain.watchlist.service;

import com.anitrack.domain.anime.exception.AnimeNotFoundException;
import com.anitrack.domain.anime.model.Anime;
import com.anitrack.domain.anime.repo.AnimeRepo;
import com.anitrack.domain.watchlist.exception.WatchlistItemAlreadyExistsException;
import com.anitrack.domain.watchlist.exception.WatchlistItemNotFoundException;
import com.anitrack.domain.watchlist.model.WatchlistItem;
import com.anitrack.domain.watchlist.repo.WatchlistRepo;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class WatchlistDomainService {

    private final WatchlistRepo watchlistRepo;
    private final AnimeRepo animeRepo;

    public WatchlistItem addToWatchlist(Long userId, Long animeId) {
        Anime anime = animeRepo.getById(animeId);
        if (anime == null) {
            throw new AnimeNotFoundException(animeId);
        }
        if (watchlistRepo.getByUserAndAnime(userId, animeId) != null) {
            throw new WatchlistItemAlreadyExistsException(userId, animeId);
        }
        WatchlistItem item = WatchlistItem.create(userId, animeId);
        return watchlistRepo.add(item);
    }

    public WatchlistItem updateProgress(Long userId, Long animeId, Integer episode) {
        WatchlistItem item = watchlistRepo.getByUserAndAnime(userId, animeId);
        if (item == null) {
            throw new WatchlistItemNotFoundException(userId, animeId);
        }
        Anime anime = animeRepo.getById(animeId);
        if (anime == null) {
            throw new AnimeNotFoundException(animeId);
        }
        item.updateProgress(episode, anime.getTotalEpisodes());
        watchlistRepo.update(item);
        return item;
    }
}
```

`WatchlistDomainService` 不带任何 Spring 注解（领域层不依赖 Spring 框架类型），其 Bean 注册留到 Task 5（`anitrack-application` 层的 `DomainServiceConfig`）完成——本任务的单测直接 `new WatchlistDomainService(mock, mock)`（通过 Mockito `@InjectMocks`），不依赖 Spring 容器。

- [ ] **Step 7: 运行测试，确认通过**

Run: `mvn -q -pl anitrack-domain -am test -Dtest=WatchlistDomainServiceTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS（6 个测试通过）

- [ ] **Step 8: Commit（需先征得用户同意后再执行）**

```bash
git add anitrack-domain/src/main/java/com/anitrack/domain/anime/exception/AnimeNotFoundException.java anitrack-domain/src/main/java/com/anitrack/domain/watchlist/exception anitrack-domain/src/main/java/com/anitrack/domain/watchlist/repo anitrack-domain/src/main/java/com/anitrack/domain/watchlist/service anitrack-domain/src/test/java/com/anitrack/domain/watchlist/service
git commit -m "feat: 新增WatchlistRepo接口与WatchlistDomainService跨上下文编排"
```

---

### Task 3: t_watchlist_item 表 + WatchlistItemPO + WatchlistItemMapper + AnimeMapper.selectByIds

**Files:**
- Create: `anitrack-starter/src/main/resources/db/migration/V3__create_watchlist_item_table.sql`
- Create: `anitrack-infrastructure/src/main/java/com/anitrack/infra/dal/po/WatchlistItemPO.java`
- Create: `anitrack-infrastructure/src/main/java/com/anitrack/infra/dal/mapper/WatchlistItemMapper.java`
- Create: `anitrack-infrastructure/src/main/resources/mapper/WatchlistItemMapper.xml`
- Modify: `anitrack-infrastructure/src/main/java/com/anitrack/infra/dal/mapper/AnimeMapper.java`
- Modify: `anitrack-infrastructure/src/main/resources/mapper/AnimeMapper.xml`

**Interfaces:**
- Consumes：无（本任务不依赖 Task 1/2 的 Java 类型）
- Produces：`WatchlistItemMapper.selectByUserAndAnime(Long, Long)`／`selectByUser(Long, String)`／`insert(WatchlistItemPO)`／`updateById(WatchlistItemPO)`，供 Task 4 的 `WatchlistRepoImpl` 调用；`AnimeMapper.selectByIds(List<Long>)`，供 Task 4 的 `AnimeRepoImpl.listByIds` 调用

- [ ] **Step 1: 编写 Flyway 建表脚本**

```sql
CREATE TABLE t_watchlist_item (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    anime_id BIGINT NOT NULL COMMENT '番剧ID',
    status VARCHAR(20) NOT NULL COMMENT '追番状态：WANT_TO_WATCH/WATCHING/WATCHED/DROPPED',
    current_episode INT NOT NULL DEFAULT 0 COMMENT '当前观看进度',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_anime (user_id, anime_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户追番记录表';
```

- [ ] **Step 2: 编写 WatchlistItemPO**

```java
package com.anitrack.infra.dal.po;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WatchlistItemPO {

    private Long id;
    private Long userId;
    private Long animeId;
    private String status;
    private Integer currentEpisode;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

- [ ] **Step 3: 编写 WatchlistItemMapper 接口**

```java
package com.anitrack.infra.dal.mapper;

import com.anitrack.infra.dal.po.WatchlistItemPO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface WatchlistItemMapper {

    WatchlistItemPO selectByUserAndAnime(@Param("userId") Long userId, @Param("animeId") Long animeId);

    List<WatchlistItemPO> selectByUser(@Param("userId") Long userId, @Param("status") String status);

    int insert(WatchlistItemPO po);

    int updateById(WatchlistItemPO po);
}
```

- [ ] **Step 4: 编写 WatchlistItemMapper.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.anitrack.infra.dal.mapper.WatchlistItemMapper">

    <resultMap id="BaseResultMap" type="com.anitrack.infra.dal.po.WatchlistItemPO">
        <id column="id" property="id"/>
        <result column="user_id" property="userId"/>
        <result column="anime_id" property="animeId"/>
        <result column="status" property="status"/>
        <result column="current_episode" property="currentEpisode"/>
        <result column="create_time" property="createTime"/>
        <result column="update_time" property="updateTime"/>
    </resultMap>

    <sql id="Base_Column_List">
        id, user_id, anime_id, status, current_episode, create_time, update_time
    </sql>

    <select id="selectByUserAndAnime" resultMap="BaseResultMap">
        SELECT
        <include refid="Base_Column_List"/>
        FROM t_watchlist_item
        WHERE user_id = #{userId} AND anime_id = #{animeId}
    </select>

    <select id="selectByUser" resultMap="BaseResultMap">
        SELECT
        <include refid="Base_Column_List"/>
        FROM t_watchlist_item
        WHERE user_id = #{userId}
        <if test="status != null">
            AND status = #{status}
        </if>
    </select>

    <insert id="insert" parameterType="com.anitrack.infra.dal.po.WatchlistItemPO" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO t_watchlist_item (user_id, anime_id, status, current_episode)
        VALUES (#{userId}, #{animeId}, #{status}, #{currentEpisode})
    </insert>

    <update id="updateById" parameterType="com.anitrack.infra.dal.po.WatchlistItemPO">
        UPDATE t_watchlist_item
        SET status = #{status},
            current_episode = #{currentEpisode}
        WHERE id = #{id}
    </update>

</mapper>
```

- [ ] **Step 5: 给 AnimeMapper 新增 selectByIds**

修改 `anitrack-infrastructure/src/main/java/com/anitrack/infra/dal/mapper/AnimeMapper.java`，新增方法并补充 import：

```java
package com.anitrack.infra.dal.mapper;

import com.anitrack.infra.dal.po.AnimePO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AnimeMapper {

    AnimePO selectById(@Param("id") Long id);

    AnimePO selectByBangumiId(@Param("bangumiId") Long bangumiId);

    List<AnimePO> selectByIds(@Param("ids") List<Long> ids);

    int insert(AnimePO po);

    int updateById(AnimePO po);
}
```

- [ ] **Step 6: 给 AnimeMapper.xml 新增 selectByIds**

在现有 `AnimeMapper.xml` 的 `</select>`（`selectByBangumiId` 之后）与 `<insert>` 之间插入：

```xml
    <select id="selectByIds" resultMap="BaseResultMap">
        SELECT
        <include refid="Base_Column_List"/>
        FROM t_anime
        WHERE id IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">
            #{id}
        </foreach>
    </select>
```

- [ ] **Step 7: 验证模块编译通过**

Run: `mvn -q -pl anitrack-infrastructure -am compile`
Expected: `BUILD SUCCESS`（此阶段无法验证 SQL/XML 与真实数据库的一致性，需等 Task 7 端到端联调时用真实 MySQL 验证）

- [ ] **Step 8: Commit（需先征得用户同意后再执行）**

```bash
git add anitrack-starter/src/main/resources/db/migration/V3__create_watchlist_item_table.sql anitrack-infrastructure/src/main/java/com/anitrack/infra/dal/po/WatchlistItemPO.java anitrack-infrastructure/src/main/java/com/anitrack/infra/dal/mapper/WatchlistItemMapper.java anitrack-infrastructure/src/main/resources/mapper/WatchlistItemMapper.xml anitrack-infrastructure/src/main/java/com/anitrack/infra/dal/mapper/AnimeMapper.java anitrack-infrastructure/src/main/resources/mapper/AnimeMapper.xml
git commit -m "feat: 新增t_watchlist_item表结构与WatchlistItemPO/Mapper，AnimeMapper补充selectByIds"
```

---

### Task 4: WatchlistItemConverter + WatchlistRepoImpl + AnimeRepo.listByIds 实现

**Files:**
- Create: `anitrack-infrastructure/src/main/java/com/anitrack/infra/converter/WatchlistItemConverter.java`
- Create: `anitrack-infrastructure/src/main/java/com/anitrack/infra/repo/WatchlistRepoImpl.java`
- Test: `anitrack-infrastructure/src/test/java/com/anitrack/infra/repo/WatchlistRepoImplTest.java`
- Modify: `anitrack-domain/src/main/java/com/anitrack/domain/anime/repo/AnimeRepo.java`
- Modify: `anitrack-infrastructure/src/main/java/com/anitrack/infra/repo/AnimeRepoImpl.java`
- Modify: `anitrack-infrastructure/src/test/java/com/anitrack/infra/repo/AnimeRepoImplTest.java`

**Interfaces:**
- Consumes：`WatchlistItem`/`WatchlistRepo`（Task 2）、`WatchlistItemPO`/`WatchlistItemMapper`/`AnimeMapper.selectByIds`（Task 3）、`Anime`/`AnimeRepo`（已存在）
- Produces：`WatchlistRepoImpl implements WatchlistRepo`（`@Repository`）；`AnimeRepo.listByIds(List<Long>): List<Anime>`，供 Task 5 的 `WatchlistApplication` 使用

- [ ] **Step 1: 给 AnimeRepo 接口新增 listByIds**

```java
package com.anitrack.domain.anime.repo;

import com.anitrack.domain.anime.model.Anime;

import java.util.List;

public interface AnimeRepo {

    Anime getById(Long id);

    Anime getByBangumiId(Long bangumiId);

    List<Anime> listByIds(List<Long> ids);

    Anime upsert(Anime anime);
}
```

- [ ] **Step 2: 编写失败的 AnimeRepoImplTest 新增测试（追加到现有文件末尾，`}` 之前）**

在 `anitrack-infrastructure/src/test/java/com/anitrack/infra/repo/AnimeRepoImplTest.java` 现有 `getById_whenNotFound_shouldReturnNull` 测试方法之后、类结尾 `}` 之前，新增：

```java
    @Test
    void listByIds_whenIdsProvided_shouldReturnConvertedList() {
        // given
        AnimePO po = new AnimePO();
        Anime anime = Anime.reconstitute(1L, 100L, "中文名", "Original Title", "http://cover.jpg", 12,
            LocalDate.of(2024, 1, 1), "简介");
        when(mockAnimeMapper.selectByIds(List.of(1L))).thenReturn(List.of(po));
        when(mockAnimeConverter.toDomain(po)).thenReturn(anime);

        // when
        List<Anime> result = sut.listByIds(List.of(1L));

        // then
        assertThat(result).containsExactly(anime);
    }

    @Test
    void listByIds_whenIdsEmpty_shouldReturnEmptyListWithoutQuerying() {
        // when
        List<Anime> result = sut.listByIds(List.of());

        // then
        assertThat(result).isEmpty();
        verify(mockAnimeMapper, never()).selectByIds(any());
    }
```

并在文件顶部 import 区新增 `import java.util.List;`（其余 import 保持不变）。

- [ ] **Step 3: 运行测试，确认失败**

Run: `mvn -q -pl anitrack-infrastructure -am test -Dtest=AnimeRepoImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL（`AnimeRepoImpl` 未实现 `listByIds`，编译错误）

- [ ] **Step 4: 给 AnimeRepoImpl 新增 listByIds 实现**

在 `anitrack-infrastructure/src/main/java/com/anitrack/infra/repo/AnimeRepoImpl.java` 的 `getByBangumiId` 方法之后、`upsert` 方法之前插入：

```java
    @Override
    public List<Anime> listByIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return animeMapper.selectByIds(ids).stream()
            .map(animeConverter::toDomain)
            .toList();
    }
```

并在文件顶部新增 `import java.util.List;`（其余 import 保持不变）。

- [ ] **Step 5: 运行测试，确认通过**

Run: `mvn -q -pl anitrack-infrastructure -am test -Dtest=AnimeRepoImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS（5 个测试通过）

- [ ] **Step 6: 编写 WatchlistItemConverter**

```java
package com.anitrack.infra.converter;

import com.anitrack.domain.watchlist.enums.WatchStatus;
import com.anitrack.domain.watchlist.model.WatchlistItem;
import com.anitrack.infra.dal.po.WatchlistItemPO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface WatchlistItemConverter {

    WatchlistItemPO toPO(WatchlistItem item);

    default WatchlistItem toDomain(WatchlistItemPO po) {
        if (po == null) {
            return null;
        }
        return WatchlistItem.reconstitute(po.getId(), po.getUserId(), po.getAnimeId(),
            WatchStatus.valueOf(po.getStatus()), po.getCurrentEpisode(), po.getUpdateTime());
    }
}
```

`toPO` 由 MapStruct 自动生成：`id/userId/animeId/currentEpisode` 字段名直接对应，`status` 字段通过 MapStruct 内置的枚举<->String 隐式转换（`WatchStatus.name()`）完成；`toDomain` 因 `WatchlistItem` 构造器私有，手写 `default` 方法调用 `reconstitute(...)`（与 `AnimeConverter`/`UserConverter` 的既有模式一致）。

- [ ] **Step 7: 编写失败的 WatchlistRepoImplTest**

```java
package com.anitrack.infra.repo;

import com.anitrack.domain.watchlist.enums.WatchStatus;
import com.anitrack.domain.watchlist.model.WatchlistItem;
import com.anitrack.infra.converter.WatchlistItemConverter;
import com.anitrack.infra.dal.mapper.WatchlistItemMapper;
import com.anitrack.infra.dal.po.WatchlistItemPO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WatchlistRepoImplTest {

    @Mock
    private WatchlistItemMapper mockWatchlistItemMapper;

    @Mock
    private WatchlistItemConverter mockWatchlistItemConverter;

    @InjectMocks
    private WatchlistRepoImpl sut;

    @Test
    void getByUserAndAnime_whenNotFound_shouldReturnNull() {
        // given
        when(mockWatchlistItemMapper.selectByUserAndAnime(1L, 100L)).thenReturn(null);

        // when
        WatchlistItem result = sut.getByUserAndAnime(1L, 100L);

        // then
        assertThat(result).isNull();
    }

    @Test
    void add_whenCalled_shouldInsertAndReturnConvertedItem() {
        // given
        WatchlistItem item = WatchlistItem.create(1L, 100L);
        WatchlistItemPO po = new WatchlistItemPO();
        WatchlistItem persisted = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WANT_TO_WATCH, 0, null);
        when(mockWatchlistItemConverter.toPO(item)).thenReturn(po);
        when(mockWatchlistItemConverter.toDomain(po)).thenReturn(persisted);

        // when
        WatchlistItem result = sut.add(item);

        // then
        verify(mockWatchlistItemMapper, times(1)).insert(po);
        assertThat(result).isEqualTo(persisted);
    }

    @Test
    void update_whenCalled_shouldUpdateById() {
        // given
        WatchlistItem item = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WATCHING, 5, null);
        WatchlistItemPO po = new WatchlistItemPO();
        when(mockWatchlistItemConverter.toPO(item)).thenReturn(po);

        // when
        sut.update(item);

        // then
        verify(mockWatchlistItemMapper, times(1)).updateById(po);
    }

    @Test
    void listByUser_whenStatusIsNull_shouldQueryWithNullStatus() {
        // given
        WatchlistItemPO po = new WatchlistItemPO();
        WatchlistItem converted = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WANT_TO_WATCH, 0, null);
        when(mockWatchlistItemMapper.selectByUser(1L, null)).thenReturn(List.of(po));
        when(mockWatchlistItemConverter.toDomain(po)).thenReturn(converted);

        // when
        List<WatchlistItem> result = sut.listByUser(1L, null);

        // then
        assertThat(result).containsExactly(converted);
        verify(mockWatchlistItemMapper, times(1)).selectByUser(1L, null);
    }

    @Test
    void listByUser_whenStatusIsProvided_shouldQueryWithStatusName() {
        // given
        WatchlistItemPO po = new WatchlistItemPO();
        WatchlistItem converted = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WATCHING, 5, null);
        when(mockWatchlistItemMapper.selectByUser(1L, "WATCHING")).thenReturn(List.of(po));
        when(mockWatchlistItemConverter.toDomain(po)).thenReturn(converted);

        // when
        List<WatchlistItem> result = sut.listByUser(1L, WatchStatus.WATCHING);

        // then
        assertThat(result).containsExactly(converted);
        verify(mockWatchlistItemMapper, times(1)).selectByUser(1L, "WATCHING");
    }
}
```

- [ ] **Step 8: 运行测试，确认失败**

Run: `mvn -q -pl anitrack-infrastructure -am test -Dtest=WatchlistRepoImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL（`WatchlistRepoImpl` 类不存在，编译错误）

- [ ] **Step 9: 编写 WatchlistRepoImpl**

```java
package com.anitrack.infra.repo;

import com.anitrack.domain.watchlist.enums.WatchStatus;
import com.anitrack.domain.watchlist.model.WatchlistItem;
import com.anitrack.domain.watchlist.repo.WatchlistRepo;
import com.anitrack.infra.converter.WatchlistItemConverter;
import com.anitrack.infra.dal.mapper.WatchlistItemMapper;
import com.anitrack.infra.dal.po.WatchlistItemPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class WatchlistRepoImpl implements WatchlistRepo {

    private final WatchlistItemMapper watchlistItemMapper;
    private final WatchlistItemConverter watchlistItemConverter;

    @Override
    public WatchlistItem getByUserAndAnime(Long userId, Long animeId) {
        WatchlistItemPO po = watchlistItemMapper.selectByUserAndAnime(userId, animeId);
        return po == null ? null : watchlistItemConverter.toDomain(po);
    }

    @Override
    public List<WatchlistItem> listByUser(Long userId, WatchStatus status) {
        String statusValue = status == null ? null : status.name();
        return watchlistItemMapper.selectByUser(userId, statusValue).stream()
            .map(watchlistItemConverter::toDomain)
            .toList();
    }

    @Override
    public WatchlistItem add(WatchlistItem item) {
        WatchlistItemPO po = watchlistItemConverter.toPO(item);
        watchlistItemMapper.insert(po);
        return watchlistItemConverter.toDomain(po);
    }

    @Override
    public void update(WatchlistItem item) {
        watchlistItemMapper.updateById(watchlistItemConverter.toPO(item));
    }
}
```

- [ ] **Step 10: 运行测试，确认通过**

Run: `mvn -q -pl anitrack-infrastructure -am test -Dtest=WatchlistRepoImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS（5 个测试通过），且 `target/generated-sources/annotations` 下生成 `WatchlistItemConverterImpl`

- [ ] **Step 11: Commit（需先征得用户同意后再执行）**

```bash
git add anitrack-domain/src/main/java/com/anitrack/domain/anime/repo/AnimeRepo.java anitrack-infrastructure/src/main/java/com/anitrack/infra/repo/AnimeRepoImpl.java anitrack-infrastructure/src/test/java/com/anitrack/infra/repo/AnimeRepoImplTest.java anitrack-infrastructure/src/main/java/com/anitrack/infra/converter/WatchlistItemConverter.java anitrack-infrastructure/src/main/java/com/anitrack/infra/repo/WatchlistRepoImpl.java anitrack-infrastructure/src/test/java/com/anitrack/infra/repo/WatchlistRepoImplTest.java
git commit -m "feat: 新增WatchlistItemConverter/WatchlistRepoImpl，AnimeRepo补充listByIds批量查询"
```

---

### Task 5: AppExceptionEnum 扩展 + WatchlistItemBO/ViewBO + WatchlistAssembler + WatchlistApplication

**Files:**
- Modify: `anitrack-application/src/main/java/com/anitrack/application/exception/AppExceptionEnum.java`
- Create: `anitrack-application/src/main/java/com/anitrack/application/model/WatchlistItemBO.java`
- Create: `anitrack-application/src/main/java/com/anitrack/application/model/WatchlistItemViewBO.java`
- Create: `anitrack-application/src/main/java/com/anitrack/application/assembler/WatchlistAssembler.java`
- Create: `anitrack-application/src/main/java/com/anitrack/application/config/DomainServiceConfig.java`
- Create: `anitrack-application/src/main/java/com/anitrack/application/service/WatchlistApplication.java`
- Test: `anitrack-application/src/test/java/com/anitrack/application/service/WatchlistApplicationTest.java`

**Interfaces:**
- Consumes：`WatchlistItem`/`WatchlistRepo`/`WatchlistDomainService`/相关领域异常（Task 1-2）、`WatchlistItemConverter` 无需直接依赖、`Anime`/`AnimeRepo`（已存在，含 Task 4 新增的 `listByIds`）、`AnitrackAppException`（已存在）
- Produces：`WatchlistApplication.addToWatchlist(Long, Long): WatchlistItemBO`／`changeStatus(Long, Long, WatchStatus): WatchlistItemBO`／`updateProgress(Long, Long, Integer): WatchlistItemBO`／`getWatchlistItem(Long, Long): WatchlistItemBO`／`listMyWatchlist(Long, WatchStatus): List<WatchlistItemViewBO>`，供 Task 6 的 `WatchlistController` 调用

- [ ] **Step 1: 给 AppExceptionEnum 新增常量**

```java
package com.anitrack.application.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AppExceptionEnum {

    USERNAME_ALREADY_EXISTS(40001, "用户名已存在"),
    LOGIN_FAILED(40002, "用户名或密码错误"),
    BANGUMI_SERVICE_UNAVAILABLE(40101, "番剧信息服务暂时不可用，请稍后重试"),
    ANIME_NOT_FOUND(40102, "番剧不存在"),
    WATCHLIST_ITEM_ALREADY_EXISTS(40201, "该番剧已在追番列表中"),
    WATCHLIST_ITEM_NOT_FOUND(40202, "追番记录不存在"),
    ILLEGAL_WATCH_STATUS_TRANSITION(40203, "非法的追番状态转移"),
    ILLEGAL_WATCH_PROGRESS(40204, "追番进度更新不合法");

    private final int code;
    private final String message;
}
```

- [ ] **Step 2: 编写 WatchlistItemBO / WatchlistItemViewBO**

```java
package com.anitrack.application.model;

import com.anitrack.domain.watchlist.enums.WatchStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class WatchlistItemBO {

    private Long id;
    private Long animeId;
    private WatchStatus status;
    private Integer currentEpisode;
    private LocalDateTime updateTime;
}
```

```java
package com.anitrack.application.model;

import com.anitrack.domain.watchlist.enums.WatchStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class WatchlistItemViewBO {

    private Long id;
    private Long animeId;
    private String animeTitleCn;
    private String animeTitleOriginal;
    private String animeCoverUrl;
    private WatchStatus status;
    private Integer currentEpisode;
    private LocalDateTime updateTime;
}
```

- [ ] **Step 3: 编写 WatchlistAssembler**

```java
package com.anitrack.application.assembler;

import com.anitrack.application.model.WatchlistItemViewBO;
import com.anitrack.domain.anime.model.Anime;
import com.anitrack.domain.watchlist.model.WatchlistItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class WatchlistAssembler {

    public List<WatchlistItemViewBO> assemble(List<WatchlistItem> items, List<Anime> animes) {
        Map<Long, Anime> animeById = animes.stream()
            .collect(Collectors.toMap(Anime::getId, Function.identity()));
        return items.stream()
            .map(item -> toViewBO(item, animeById.get(item.getAnimeId())))
            .filter(Objects::nonNull)
            .toList();
    }

    private WatchlistItemViewBO toViewBO(WatchlistItem item, Anime anime) {
        if (anime == null) {
            log.warn("追番记录关联的番剧不存在, animeId={}", item.getAnimeId());
            return null;
        }
        return WatchlistItemViewBO.builder()
            .id(item.getId())
            .animeId(item.getAnimeId())
            .animeTitleCn(anime.getTitleCn())
            .animeTitleOriginal(anime.getTitleOriginal())
            .animeCoverUrl(anime.getCoverUrl())
            .status(item.getStatus())
            .currentEpisode(item.getCurrentEpisode())
            .updateTime(item.getUpdateTime())
            .build();
    }
}
```

- [ ] **Step 4: 编写 DomainServiceConfig（注册 WatchlistDomainService 为 Spring Bean）**

```java
package com.anitrack.application.config;

import com.anitrack.domain.anime.repo.AnimeRepo;
import com.anitrack.domain.watchlist.repo.WatchlistRepo;
import com.anitrack.domain.watchlist.service.WatchlistDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainServiceConfig {

    @Bean
    public WatchlistDomainService watchlistDomainService(WatchlistRepo watchlistRepo, AnimeRepo animeRepo) {
        return new WatchlistDomainService(watchlistRepo, animeRepo);
    }
}
```

- [ ] **Step 5: 编写失败的 WatchlistApplicationTest**

```java
package com.anitrack.application.service;

import com.anitrack.application.assembler.WatchlistAssembler;
import com.anitrack.application.exception.AnitrackAppException;
import com.anitrack.application.model.WatchlistItemBO;
import com.anitrack.application.model.WatchlistItemViewBO;
import com.anitrack.domain.anime.exception.AnimeNotFoundException;
import com.anitrack.domain.anime.model.Anime;
import com.anitrack.domain.anime.repo.AnimeRepo;
import com.anitrack.domain.watchlist.enums.WatchStatus;
import com.anitrack.domain.watchlist.exception.IllegalWatchProgressException;
import com.anitrack.domain.watchlist.exception.WatchlistItemAlreadyExistsException;
import com.anitrack.domain.watchlist.exception.WatchlistItemNotFoundException;
import com.anitrack.domain.watchlist.model.WatchlistItem;
import com.anitrack.domain.watchlist.repo.WatchlistRepo;
import com.anitrack.domain.watchlist.service.WatchlistDomainService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WatchlistApplicationTest {

    @Mock
    private WatchlistDomainService mockWatchlistDomainService;

    @Mock
    private WatchlistRepo mockWatchlistRepo;

    @Mock
    private AnimeRepo mockAnimeRepo;

    @Mock
    private WatchlistAssembler mockWatchlistAssembler;

    @Mock
    private ApplicationEventPublisher mockEventPublisher;

    @InjectMocks
    private WatchlistApplication sut;

    @Test
    void addToWatchlist_whenSucceeds_shouldReturnBO() {
        // given
        WatchlistItem item = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WANT_TO_WATCH, 0, null);
        when(mockWatchlistDomainService.addToWatchlist(1L, 100L)).thenReturn(item);

        // when
        WatchlistItemBO result = sut.addToWatchlist(1L, 100L);

        // then
        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getStatus()).isEqualTo(WatchStatus.WANT_TO_WATCH);
    }

    @Test
    void addToWatchlist_whenAnimeNotFound_shouldThrowAppException() {
        // given
        when(mockWatchlistDomainService.addToWatchlist(1L, 100L))
            .thenThrow(new AnimeNotFoundException(100L));

        // when & then
        assertThatThrownBy(() -> sut.addToWatchlist(1L, 100L))
            .isInstanceOf(AnitrackAppException.class)
            .hasMessageContaining("番剧不存在");
    }

    @Test
    void addToWatchlist_whenAlreadyExists_shouldThrowAppException() {
        // given
        when(mockWatchlistDomainService.addToWatchlist(1L, 100L))
            .thenThrow(new WatchlistItemAlreadyExistsException(1L, 100L));

        // when & then
        assertThatThrownBy(() -> sut.addToWatchlist(1L, 100L))
            .isInstanceOf(AnitrackAppException.class)
            .hasMessageContaining("该番剧已在追番列表中");
    }

    @Test
    void changeStatus_whenItemExists_shouldUpdateAndPublishEvent() {
        // given
        WatchlistItem item = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WANT_TO_WATCH, 0, null);
        when(mockWatchlistRepo.getByUserAndAnime(1L, 100L)).thenReturn(item);

        // when
        WatchlistItemBO result = sut.changeStatus(1L, 100L, WatchStatus.WATCHING);

        // then
        assertThat(result.getStatus()).isEqualTo(WatchStatus.WATCHING);
        verify(mockWatchlistRepo, times(1)).update(item);
        verify(mockEventPublisher, times(1)).publishEvent(any());
    }

    @Test
    void changeStatus_whenItemNotFound_shouldThrowAppException() {
        // given
        when(mockWatchlistRepo.getByUserAndAnime(1L, 100L)).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> sut.changeStatus(1L, 100L, WatchStatus.WATCHING))
            .isInstanceOf(AnitrackAppException.class)
            .hasMessageContaining("追番记录不存在");

        verify(mockEventPublisher, never()).publishEvent(any());
    }

    @Test
    void changeStatus_whenTransitionIllegal_shouldThrowAppException() {
        // given
        WatchlistItem item = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WANT_TO_WATCH, 0, null);
        when(mockWatchlistRepo.getByUserAndAnime(1L, 100L)).thenReturn(item);

        // when & then
        assertThatThrownBy(() -> sut.changeStatus(1L, 100L, WatchStatus.WATCHED))
            .isInstanceOf(AnitrackAppException.class)
            .hasMessageContaining("非法的追番状态转移");

        verify(mockWatchlistRepo, never()).update(any());
        verify(mockEventPublisher, never()).publishEvent(any());
    }

    @Test
    void updateProgress_whenSucceeds_shouldReturnBO() {
        // given
        WatchlistItem item = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WATCHING, 5, null);
        when(mockWatchlistDomainService.updateProgress(1L, 100L, 5)).thenReturn(item);

        // when
        WatchlistItemBO result = sut.updateProgress(1L, 100L, 5);

        // then
        assertThat(result.getCurrentEpisode()).isEqualTo(5);
    }

    @Test
    void updateProgress_whenItemNotFound_shouldThrowAppException() {
        // given
        when(mockWatchlistDomainService.updateProgress(1L, 100L, 5))
            .thenThrow(new WatchlistItemNotFoundException(1L, 100L));

        // when & then
        assertThatThrownBy(() -> sut.updateProgress(1L, 100L, 5))
            .isInstanceOf(AnitrackAppException.class)
            .hasMessageContaining("追番记录不存在");
    }

    @Test
    void updateProgress_whenAnimeNotFound_shouldThrowAppException() {
        // given
        when(mockWatchlistDomainService.updateProgress(1L, 100L, 5))
            .thenThrow(new AnimeNotFoundException(100L));

        // when & then
        assertThatThrownBy(() -> sut.updateProgress(1L, 100L, 5))
            .isInstanceOf(AnitrackAppException.class)
            .hasMessageContaining("番剧不存在");
    }

    @Test
    void updateProgress_whenProgressIllegal_shouldThrowAppException() {
        // given
        when(mockWatchlistDomainService.updateProgress(1L, 100L, -1))
            .thenThrow(new IllegalWatchProgressException("观看进度必须大于0"));

        // when & then
        assertThatThrownBy(() -> sut.updateProgress(1L, 100L, -1))
            .isInstanceOf(AnitrackAppException.class)
            .hasMessageContaining("追番进度更新不合法");
    }

    @Test
    void getWatchlistItem_whenExists_shouldReturnBO() {
        // given
        WatchlistItem item = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WATCHING, 5, null);
        when(mockWatchlistRepo.getByUserAndAnime(1L, 100L)).thenReturn(item);

        // when
        WatchlistItemBO result = sut.getWatchlistItem(1L, 100L);

        // then
        assertThat(result.getId()).isEqualTo(10L);
    }

    @Test
    void getWatchlistItem_whenNotFound_shouldThrowAppException() {
        // given
        when(mockWatchlistRepo.getByUserAndAnime(1L, 100L)).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> sut.getWatchlistItem(1L, 100L))
            .isInstanceOf(AnitrackAppException.class)
            .hasMessageContaining("追番记录不存在");
    }

    @Test
    void listMyWatchlist_whenCalled_shouldFetchAnimesAndAssemble() {
        // given
        WatchlistItem item = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WATCHING, 5, null);
        Anime anime = Anime.reconstitute(100L, 999L, "中文名", "Original Title", "http://cover.jpg", 12,
            LocalDate.of(2024, 1, 1), "简介");
        WatchlistItemViewBO viewBO = WatchlistItemViewBO.builder().id(10L).animeId(100L).build();
        when(mockWatchlistRepo.listByUser(1L, WatchStatus.WATCHING)).thenReturn(List.of(item));
        when(mockAnimeRepo.listByIds(List.of(100L))).thenReturn(List.of(anime));
        when(mockWatchlistAssembler.assemble(List.of(item), List.of(anime))).thenReturn(List.of(viewBO));

        // when
        List<WatchlistItemViewBO> result = sut.listMyWatchlist(1L, WatchStatus.WATCHING);

        // then
        assertThat(result).containsExactly(viewBO);
    }
}
```

- [ ] **Step 6: 运行测试，确认失败**

Run: `mvn -q -pl anitrack-application -am test -Dtest=WatchlistApplicationTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL（`WatchlistApplication` 类不存在，编译错误）

- [ ] **Step 7: 编写 WatchlistApplication**

```java
package com.anitrack.application.service;

import com.anitrack.application.assembler.WatchlistAssembler;
import com.anitrack.application.exception.AnitrackAppException;
import com.anitrack.application.exception.AppExceptionEnum;
import com.anitrack.application.model.WatchlistItemBO;
import com.anitrack.application.model.WatchlistItemViewBO;
import com.anitrack.domain.anime.exception.AnimeNotFoundException;
import com.anitrack.domain.anime.model.Anime;
import com.anitrack.domain.anime.repo.AnimeRepo;
import com.anitrack.domain.watchlist.enums.WatchStatus;
import com.anitrack.domain.watchlist.exception.IllegalWatchProgressException;
import com.anitrack.domain.watchlist.exception.IllegalWatchStatusTransitionException;
import com.anitrack.domain.watchlist.exception.WatchlistItemAlreadyExistsException;
import com.anitrack.domain.watchlist.exception.WatchlistItemNotFoundException;
import com.anitrack.domain.watchlist.model.WatchStatusChangedEvent;
import com.anitrack.domain.watchlist.model.WatchlistItem;
import com.anitrack.domain.watchlist.repo.WatchlistRepo;
import com.anitrack.domain.watchlist.service.WatchlistDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WatchlistApplication {

    private final WatchlistDomainService watchlistDomainService;
    private final WatchlistRepo watchlistRepo;
    private final AnimeRepo animeRepo;
    private final WatchlistAssembler watchlistAssembler;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public WatchlistItemBO addToWatchlist(Long userId, Long animeId) {
        WatchlistItem item;
        try {
            item = watchlistDomainService.addToWatchlist(userId, animeId);
        } catch (AnimeNotFoundException e) {
            throw new AnitrackAppException(AppExceptionEnum.ANIME_NOT_FOUND);
        } catch (WatchlistItemAlreadyExistsException e) {
            throw new AnitrackAppException(AppExceptionEnum.WATCHLIST_ITEM_ALREADY_EXISTS);
        }
        return toBO(item);
    }

    @Transactional
    public WatchlistItemBO changeStatus(Long userId, Long animeId, WatchStatus newStatus) {
        WatchlistItem item = watchlistRepo.getByUserAndAnime(userId, animeId);
        if (item == null) {
            throw new AnitrackAppException(AppExceptionEnum.WATCHLIST_ITEM_NOT_FOUND);
        }
        WatchStatusChangedEvent event;
        try {
            event = item.changeStatus(newStatus);
        } catch (IllegalWatchStatusTransitionException e) {
            throw new AnitrackAppException(AppExceptionEnum.ILLEGAL_WATCH_STATUS_TRANSITION);
        }
        watchlistRepo.update(item);
        eventPublisher.publishEvent(event);
        return toBO(item);
    }

    @Transactional
    public WatchlistItemBO updateProgress(Long userId, Long animeId, Integer episode) {
        WatchlistItem item;
        try {
            item = watchlistDomainService.updateProgress(userId, animeId, episode);
        } catch (WatchlistItemNotFoundException e) {
            throw new AnitrackAppException(AppExceptionEnum.WATCHLIST_ITEM_NOT_FOUND);
        } catch (AnimeNotFoundException e) {
            throw new AnitrackAppException(AppExceptionEnum.ANIME_NOT_FOUND);
        } catch (IllegalWatchProgressException e) {
            throw new AnitrackAppException(AppExceptionEnum.ILLEGAL_WATCH_PROGRESS);
        }
        return toBO(item);
    }

    public WatchlistItemBO getWatchlistItem(Long userId, Long animeId) {
        WatchlistItem item = watchlistRepo.getByUserAndAnime(userId, animeId);
        if (item == null) {
            throw new AnitrackAppException(AppExceptionEnum.WATCHLIST_ITEM_NOT_FOUND);
        }
        return toBO(item);
    }

    public List<WatchlistItemViewBO> listMyWatchlist(Long userId, WatchStatus status) {
        List<WatchlistItem> items = watchlistRepo.listByUser(userId, status);
        List<Long> animeIds = items.stream().map(WatchlistItem::getAnimeId).toList();
        List<Anime> animes = animeRepo.listByIds(animeIds);
        return watchlistAssembler.assemble(items, animes);
    }

    private WatchlistItemBO toBO(WatchlistItem item) {
        return WatchlistItemBO.builder()
            .id(item.getId())
            .animeId(item.getAnimeId())
            .status(item.getStatus())
            .currentEpisode(item.getCurrentEpisode())
            .updateTime(item.getUpdateTime())
            .build();
    }
}
```

- [ ] **Step 8: 运行测试，确认通过**

Run: `mvn -q -pl anitrack-application -am test -Dtest=WatchlistApplicationTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS（12 个测试通过）

- [ ] **Step 9: Commit（需先征得用户同意后再执行）**

```bash
git add anitrack-application/src/main/java/com/anitrack/application/exception/AppExceptionEnum.java anitrack-application/src/main/java/com/anitrack/application/model/WatchlistItemBO.java anitrack-application/src/main/java/com/anitrack/application/model/WatchlistItemViewBO.java anitrack-application/src/main/java/com/anitrack/application/assembler/WatchlistAssembler.java anitrack-application/src/main/java/com/anitrack/application/config/DomainServiceConfig.java anitrack-application/src/main/java/com/anitrack/application/service/WatchlistApplication.java anitrack-application/src/test/java/com/anitrack/application/service/WatchlistApplicationTest.java
git commit -m "feat: 新增WatchlistApplication编排追番5个用例并接入事件发布"
```

---

### Task 6: WatchlistController（5个接口）+ Web 层集成测试

**Files:**
- Create: `anitrack-starter/src/main/java/com/anitrack/starter/request/WatchlistAddReq.java`
- Create: `anitrack-starter/src/main/java/com/anitrack/starter/request/WatchlistChangeStatusReq.java`
- Create: `anitrack-starter/src/main/java/com/anitrack/starter/request/WatchlistUpdateProgressReq.java`
- Create: `anitrack-starter/src/main/java/com/anitrack/starter/request/WatchlistDetailReq.java`
- Create: `anitrack-starter/src/main/java/com/anitrack/starter/request/WatchlistListReq.java`
- Create: `anitrack-starter/src/main/java/com/anitrack/starter/response/WatchlistItemResponse.java`
- Create: `anitrack-starter/src/main/java/com/anitrack/starter/response/WatchlistItemViewResponse.java`
- Modify: `anitrack-starter/src/main/java/com/anitrack/starter/converter/HttpConverter.java`
- Create: `anitrack-starter/src/main/java/com/anitrack/starter/controller/WatchlistController.java`
- Test: `anitrack-starter/src/test/java/com/anitrack/starter/controller/WatchlistControllerTest.java`

**Interfaces:**
- Consumes：`WatchlistApplication`（Task 5）、`ResponseResult`/`UserContextHolder`（已存在）
- Produces：`POST /api/watchlist/add`、`POST /api/watchlist/change_status`、`POST /api/watchlist/update_progress`、`POST /api/watchlist/detail`、`POST /api/watchlist/list`

- [ ] **Step 1: 编写请求对象**

```java
package com.anitrack.starter.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class WatchlistAddReq {

    @NotNull(message = "番剧ID不能为空")
    private Long animeId;
}
```

```java
package com.anitrack.starter.request;

import com.anitrack.domain.watchlist.enums.WatchStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class WatchlistChangeStatusReq {

    @NotNull(message = "番剧ID不能为空")
    private Long animeId;

    @NotNull(message = "追番状态不能为空")
    private WatchStatus status;
}
```

```java
package com.anitrack.starter.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class WatchlistUpdateProgressReq {

    @NotNull(message = "番剧ID不能为空")
    private Long animeId;

    @NotNull(message = "观看进度不能为空")
    private Integer episode;
}
```

```java
package com.anitrack.starter.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class WatchlistDetailReq {

    @NotNull(message = "番剧ID不能为空")
    private Long animeId;
}
```

```java
package com.anitrack.starter.request;

import com.anitrack.domain.watchlist.enums.WatchStatus;
import lombok.Getter;

@Getter
public class WatchlistListReq {

    private WatchStatus status;
}
```

- [ ] **Step 2: 编写响应对象**

```java
package com.anitrack.starter.response;

import com.anitrack.domain.watchlist.enums.WatchStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class WatchlistItemResponse {

    private Long id;
    private Long animeId;
    private WatchStatus status;
    private Integer currentEpisode;
    private LocalDateTime updateTime;
}
```

```java
package com.anitrack.starter.response;

import com.anitrack.domain.watchlist.enums.WatchStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class WatchlistItemViewResponse {

    private Long id;
    private Long animeId;
    private String animeTitleCn;
    private String animeTitleOriginal;
    private String animeCoverUrl;
    private WatchStatus status;
    private Integer currentEpisode;
    private LocalDateTime updateTime;
}
```

- [ ] **Step 3: 给 HttpConverter 新增转换方法**

在现有 `HttpConverter` 类中新增以下三个方法（保留已有 User/Anime 相关方法不变），并新增对应 import：`com.anitrack.application.model.WatchlistItemBO`、`com.anitrack.application.model.WatchlistItemViewBO`、`com.anitrack.starter.response.WatchlistItemResponse`、`com.anitrack.starter.response.WatchlistItemViewResponse`：

```java
public WatchlistItemResponse watchlistItemBO2Response(WatchlistItemBO bo) {
    return WatchlistItemResponse.builder()
        .id(bo.getId())
        .animeId(bo.getAnimeId())
        .status(bo.getStatus())
        .currentEpisode(bo.getCurrentEpisode())
        .updateTime(bo.getUpdateTime())
        .build();
}

public WatchlistItemViewResponse watchlistItemViewBO2Response(WatchlistItemViewBO bo) {
    return WatchlistItemViewResponse.builder()
        .id(bo.getId())
        .animeId(bo.getAnimeId())
        .animeTitleCn(bo.getAnimeTitleCn())
        .animeTitleOriginal(bo.getAnimeTitleOriginal())
        .animeCoverUrl(bo.getAnimeCoverUrl())
        .status(bo.getStatus())
        .currentEpisode(bo.getCurrentEpisode())
        .updateTime(bo.getUpdateTime())
        .build();
}

public List<WatchlistItemViewResponse> watchlistItemViewBOList2Response(List<WatchlistItemViewBO> boList) {
    return boList.stream().map(this::watchlistItemViewBO2Response).toList();
}
```

- [ ] **Step 4: 编写失败的 WatchlistControllerTest**

```java
package com.anitrack.starter.controller;

import com.anitrack.application.model.WatchlistItemBO;
import com.anitrack.application.model.WatchlistItemViewBO;
import com.anitrack.application.service.WatchlistApplication;
import com.anitrack.domain.watchlist.enums.WatchStatus;
import com.anitrack.infra.auth.JwtTokenProvider;
import com.anitrack.starter.converter.HttpConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WatchlistController.class)
class WatchlistControllerTest {

    private static final String AUTH_HEADER_VALUE = "Bearer test-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WatchlistApplication mockWatchlistApplication;

    @MockBean
    private HttpConverter mockHttpConverter;

    @MockBean
    private JwtTokenProvider mockJwtTokenProvider;

    private void stubValidToken() {
        when(mockJwtTokenProvider.validateToken(anyString())).thenReturn(true);
        when(mockJwtTokenProvider.getUserId(anyString())).thenReturn(1L);
    }

    private WatchlistItemBO createTestItemBO() {
        return WatchlistItemBO.builder()
            .id(10L)
            .animeId(100L)
            .status(WatchStatus.WANT_TO_WATCH)
            .currentEpisode(0)
            .build();
    }

    @Test
    void add_whenNoAuthorizationHeader_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/watchlist/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L))))
            .andExpect(status().isUnauthorized());

        verify(mockWatchlistApplication, never()).addToWatchlist(any(), any());
    }

    @Test
    void add_whenAnimeIdMissing_shouldReturnBadRequest() throws Exception {
        stubValidToken();

        mockMvc.perform(post("/api/watchlist/add")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void add_whenRequestIsValid_shouldReturnWatchlistItem() throws Exception {
        // given
        stubValidToken();
        WatchlistItemBO bo = createTestItemBO();
        when(mockWatchlistApplication.addToWatchlist(1L, 100L)).thenReturn(bo);
        when(mockHttpConverter.watchlistItemBO2Response(bo)).thenCallRealMethod();

        // when & then
        mockMvc.perform(post("/api/watchlist/add")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(1))
            .andExpect(jsonPath("$.data.status").value("WANT_TO_WATCH"));

        verify(mockWatchlistApplication, times(1)).addToWatchlist(1L, 100L);
    }

    @Test
    void add_whenAlreadyExists_shouldReturnBusinessError() throws Exception {
        // given
        stubValidToken();
        doThrow(new com.anitrack.application.exception.AnitrackAppException(
                com.anitrack.application.exception.AppExceptionEnum.WATCHLIST_ITEM_ALREADY_EXISTS))
            .when(mockWatchlistApplication).addToWatchlist(1L, 100L);

        // when & then
        mockMvc.perform(post("/api/watchlist/add")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(0));
    }

    @Test
    void changeStatus_whenNoAuthorizationHeader_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/watchlist/change_status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L, "status", "WATCHING"))))
            .andExpect(status().isUnauthorized());

        verify(mockWatchlistApplication, never()).changeStatus(any(), any(), any());
    }

    @Test
    void changeStatus_whenStatusMissing_shouldReturnBadRequest() throws Exception {
        stubValidToken();

        mockMvc.perform(post("/api/watchlist/change_status")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void changeStatus_whenRequestIsValid_shouldReturnWatchlistItem() throws Exception {
        // given
        stubValidToken();
        WatchlistItemBO bo = WatchlistItemBO.builder()
            .id(10L).animeId(100L).status(WatchStatus.WATCHING).currentEpisode(0).build();
        when(mockWatchlistApplication.changeStatus(1L, 100L, WatchStatus.WATCHING)).thenReturn(bo);
        when(mockHttpConverter.watchlistItemBO2Response(bo)).thenCallRealMethod();

        // when & then
        mockMvc.perform(post("/api/watchlist/change_status")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L, "status", "WATCHING"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(1))
            .andExpect(jsonPath("$.data.status").value("WATCHING"));

        verify(mockWatchlistApplication, times(1)).changeStatus(1L, 100L, WatchStatus.WATCHING);
    }

    @Test
    void changeStatus_whenIllegalTransition_shouldReturnBusinessError() throws Exception {
        // given
        stubValidToken();
        doThrow(new com.anitrack.application.exception.AnitrackAppException(
                com.anitrack.application.exception.AppExceptionEnum.ILLEGAL_WATCH_STATUS_TRANSITION))
            .when(mockWatchlistApplication).changeStatus(1L, 100L, WatchStatus.WATCHED);

        // when & then
        mockMvc.perform(post("/api/watchlist/change_status")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L, "status", "WATCHED"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(0));
    }

    @Test
    void updateProgress_whenNoAuthorizationHeader_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/watchlist/update_progress")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L, "episode", 5))))
            .andExpect(status().isUnauthorized());

        verify(mockWatchlistApplication, never()).updateProgress(any(), any(), any());
    }

    @Test
    void updateProgress_whenEpisodeMissing_shouldReturnBadRequest() throws Exception {
        stubValidToken();

        mockMvc.perform(post("/api/watchlist/update_progress")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void updateProgress_whenRequestIsValid_shouldReturnWatchlistItem() throws Exception {
        // given
        stubValidToken();
        WatchlistItemBO bo = WatchlistItemBO.builder()
            .id(10L).animeId(100L).status(WatchStatus.WATCHING).currentEpisode(5).build();
        when(mockWatchlistApplication.updateProgress(1L, 100L, 5)).thenReturn(bo);
        when(mockHttpConverter.watchlistItemBO2Response(bo)).thenCallRealMethod();

        // when & then
        mockMvc.perform(post("/api/watchlist/update_progress")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L, "episode", 5))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(1))
            .andExpect(jsonPath("$.data.currentEpisode").value(5));

        verify(mockWatchlistApplication, times(1)).updateProgress(1L, 100L, 5);
    }

    @Test
    void updateProgress_whenIllegalProgress_shouldReturnBusinessError() throws Exception {
        // given
        stubValidToken();
        doThrow(new com.anitrack.application.exception.AnitrackAppException(
                com.anitrack.application.exception.AppExceptionEnum.ILLEGAL_WATCH_PROGRESS))
            .when(mockWatchlistApplication).updateProgress(1L, 100L, -1);

        // when & then
        mockMvc.perform(post("/api/watchlist/update_progress")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L, "episode", -1))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(0));
    }

    @Test
    void detail_whenNoAuthorizationHeader_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/watchlist/detail")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L))))
            .andExpect(status().isUnauthorized());

        verify(mockWatchlistApplication, never()).getWatchlistItem(any(), any());
    }

    @Test
    void detail_whenAnimeIdMissing_shouldReturnBadRequest() throws Exception {
        stubValidToken();

        mockMvc.perform(post("/api/watchlist/detail")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void detail_whenRequestIsValid_shouldReturnWatchlistItem() throws Exception {
        // given
        stubValidToken();
        WatchlistItemBO bo = createTestItemBO();
        when(mockWatchlistApplication.getWatchlistItem(1L, 100L)).thenReturn(bo);
        when(mockHttpConverter.watchlistItemBO2Response(bo)).thenCallRealMethod();

        // when & then
        mockMvc.perform(post("/api/watchlist/detail")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(1))
            .andExpect(jsonPath("$.data.animeId").value(100L));

        verify(mockWatchlistApplication, times(1)).getWatchlistItem(1L, 100L);
    }

    @Test
    void detail_whenNotFound_shouldReturnBusinessError() throws Exception {
        // given
        stubValidToken();
        doThrow(new com.anitrack.application.exception.AnitrackAppException(
                com.anitrack.application.exception.AppExceptionEnum.WATCHLIST_ITEM_NOT_FOUND))
            .when(mockWatchlistApplication).getWatchlistItem(1L, 999L);

        // when & then
        mockMvc.perform(post("/api/watchlist/detail")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 999L))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(0));
    }

    @Test
    void list_whenNoAuthorizationHeader_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/watchlist/list")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());

        verify(mockWatchlistApplication, never()).listMyWatchlist(any(), any());
    }

    @Test
    void list_whenRequestIsValid_shouldReturnWatchlistItemViewList() throws Exception {
        // given
        stubValidToken();
        WatchlistItemViewBO viewBO = WatchlistItemViewBO.builder()
            .id(10L).animeId(100L).animeTitleCn("中文名").status(WatchStatus.WATCHING).currentEpisode(5).build();
        when(mockWatchlistApplication.listMyWatchlist(1L, WatchStatus.WATCHING)).thenReturn(List.of(viewBO));
        when(mockHttpConverter.watchlistItemViewBOList2Response(List.of(viewBO))).thenCallRealMethod();
        when(mockHttpConverter.watchlistItemViewBO2Response(viewBO)).thenCallRealMethod();

        // when & then
        mockMvc.perform(post("/api/watchlist/list")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", "WATCHING"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(1))
            .andExpect(jsonPath("$.data[0].animeTitleCn").value("中文名"));

        verify(mockWatchlistApplication, times(1)).listMyWatchlist(1L, WatchStatus.WATCHING);
    }
}
```

- [ ] **Step 5: 运行测试，确认失败**

Run: `mvn -q -pl anitrack-starter -am test -Dtest=WatchlistControllerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL（`WatchlistController` 类不存在，编译错误）

- [ ] **Step 6: 编写 WatchlistController**

```java
package com.anitrack.starter.controller;

import com.anitrack.application.model.WatchlistItemBO;
import com.anitrack.application.model.WatchlistItemViewBO;
import com.anitrack.application.service.WatchlistApplication;
import com.anitrack.common.utils.UserContextHolder;
import com.anitrack.starter.converter.HttpConverter;
import com.anitrack.starter.request.WatchlistAddReq;
import com.anitrack.starter.request.WatchlistChangeStatusReq;
import com.anitrack.starter.request.WatchlistDetailReq;
import com.anitrack.starter.request.WatchlistListReq;
import com.anitrack.starter.request.WatchlistUpdateProgressReq;
import com.anitrack.starter.response.ResponseResult;
import com.anitrack.starter.response.WatchlistItemResponse;
import com.anitrack.starter.response.WatchlistItemViewResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/watchlist")
@RequiredArgsConstructor
public class WatchlistController {

    private final WatchlistApplication watchlistApplication;
    private final HttpConverter httpConverter;

    @PostMapping("/add")
    public ResponseResult<WatchlistItemResponse> add(@Valid @RequestBody WatchlistAddReq req) {
        WatchlistItemBO result = watchlistApplication.addToWatchlist(UserContextHolder.getUserId(), req.getAnimeId());
        return ResponseResult.success(httpConverter.watchlistItemBO2Response(result));
    }

    @PostMapping("/change_status")
    public ResponseResult<WatchlistItemResponse> changeStatus(@Valid @RequestBody WatchlistChangeStatusReq req) {
        WatchlistItemBO result = watchlistApplication.changeStatus(
            UserContextHolder.getUserId(), req.getAnimeId(), req.getStatus());
        return ResponseResult.success(httpConverter.watchlistItemBO2Response(result));
    }

    @PostMapping("/update_progress")
    public ResponseResult<WatchlistItemResponse> updateProgress(@Valid @RequestBody WatchlistUpdateProgressReq req) {
        WatchlistItemBO result = watchlistApplication.updateProgress(
            UserContextHolder.getUserId(), req.getAnimeId(), req.getEpisode());
        return ResponseResult.success(httpConverter.watchlistItemBO2Response(result));
    }

    @PostMapping("/detail")
    public ResponseResult<WatchlistItemResponse> detail(@Valid @RequestBody WatchlistDetailReq req) {
        WatchlistItemBO result = watchlistApplication.getWatchlistItem(UserContextHolder.getUserId(), req.getAnimeId());
        return ResponseResult.success(httpConverter.watchlistItemBO2Response(result));
    }

    @PostMapping("/list")
    public ResponseResult<List<WatchlistItemViewResponse>> list(@RequestBody WatchlistListReq req) {
        List<WatchlistItemViewBO> result = watchlistApplication.listMyWatchlist(
            UserContextHolder.getUserId(), req.getStatus());
        return ResponseResult.success(httpConverter.watchlistItemViewBOList2Response(result));
    }
}
```

- [ ] **Step 7: 运行测试，确认通过**

Run: `mvn -q -pl anitrack-starter -am test -Dtest=WatchlistControllerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS（18 个测试通过）

- [ ] **Step 8: Commit（需先征得用户同意后再执行）**

```bash
git add anitrack-starter/src/main/java/com/anitrack/starter/request/WatchlistAddReq.java anitrack-starter/src/main/java/com/anitrack/starter/request/WatchlistChangeStatusReq.java anitrack-starter/src/main/java/com/anitrack/starter/request/WatchlistUpdateProgressReq.java anitrack-starter/src/main/java/com/anitrack/starter/request/WatchlistDetailReq.java anitrack-starter/src/main/java/com/anitrack/starter/request/WatchlistListReq.java anitrack-starter/src/main/java/com/anitrack/starter/response/WatchlistItemResponse.java anitrack-starter/src/main/java/com/anitrack/starter/response/WatchlistItemViewResponse.java anitrack-starter/src/main/java/com/anitrack/starter/converter/HttpConverter.java anitrack-starter/src/main/java/com/anitrack/starter/controller/WatchlistController.java anitrack-starter/src/test/java/com/anitrack/starter/controller/WatchlistControllerTest.java
git commit -m "feat: 新增WatchlistController追番5个接口与Web层集成测试"
```

---

### Task 7: 全量编译测试 + 端到端联调

**Files:**
- 无新增/修改文件，本任务仅验证

**Interfaces:**
- Consumes：Task 1-6 全部产出
- Produces：可运行的、完整的追番核心功能

- [ ] **Step 1: 验证整个 Reactor 编译并测试通过**

Run: `mvn -q compile test`
Expected: 所有模块 `BUILD SUCCESS`，Task 1-6 编写的全部单测通过（Phase 1a 原有 44 个 + 本 spec 新增约 61 个）

- [ ] **Step 2: 端到端手动联调（需要本地 MySQL 与已注册测试用户）**

沿用 Phase 1a 的本地环境配置：

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
export PATH=/opt/homebrew/opt/maven/bin:$PATH
export SPRING_PROFILES_ACTIVE=local
```

启动应用（多模块 reactor 下 `spring-boot:run` 认不出主类，需先 `install` 上游模块）：

```bash
mvn -q install -DskipTests -pl anitrack-common,anitrack-domain,anitrack-infrastructure,anitrack-application -am
cd anitrack-starter
mvn -q spring-boot:run -Dspring-boot.run.mainClass=com.anitrack.starter.ApplicationLoader
```

Expected: 控制台无异常，日志显示 Flyway 执行 `V3__create_watchlist_item_table.sql` 成功，`t_watchlist_item` 表已创建

新开终端，注册并登录一个测试用户拿到 token（若 Phase 1a 遗留的测试用户仍在，可直接登录复用）：

```bash
curl -s -X POST http://localhost:8080/api/user/register \
  -H "Content-Type: application/json" \
  -d '{"username":"watchlist_bob","password":"password123","nickname":"Bob"}'

TOKEN=$(curl -s -X POST http://localhost:8080/api/user/login \
  -H "Content-Type: application/json" \
  -d '{"username":"watchlist_bob","password":"password123"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])")
```

先搜索一部番剧回填本地缓存（沿用 Phase 1a 的 `/api/anime/search`），记下返回的本地 `id`（假设为 `1`）：

```bash
curl -s -X POST http://localhost:8080/api/anime/search \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"keyword":"败犬女主太多了"}'
```

测试加入追番：

```bash
curl -s -X POST http://localhost:8080/api/watchlist/add \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"animeId":1}'
```

Expected: `{"status":1,"message":null,"data":{"id":1,"animeId":1,"status":"WANT_TO_WATCH","currentEpisode":0,"updateTime":null}}`（本地自增 `id` 与 `updateTime` 具体值以实际为准，重点验证 `status="WANT_TO_WATCH"`、`currentEpisode=0`）

测试重复加入（应报错）：

```bash
curl -s -X POST http://localhost:8080/api/watchlist/add \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"animeId":1}'
```

Expected: `{"status":0,"message":"该番剧已在追番列表中","data":null}`

测试对不存在的番剧加入追番（应报错）：

```bash
curl -s -X POST http://localhost:8080/api/watchlist/add \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"animeId":99999}'
```

Expected: `{"status":0,"message":"番剧不存在","data":null}`

测试变更状态（`WANT_TO_WATCH → WATCHING`）：

```bash
curl -s -X POST http://localhost:8080/api/watchlist/change_status \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"animeId":1,"status":"WATCHING"}'
```

Expected: `{"status":1,"message":null,"data":{"id":1,"animeId":1,"status":"WATCHING",...}}`

测试非法状态转移（`WATCHING → WANT_TO_WATCH`，应报错）：

```bash
curl -s -X POST http://localhost:8080/api/watchlist/change_status \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"animeId":1,"status":"WANT_TO_WATCH"}'
```

Expected: `{"status":0,"message":"非法的追番状态转移","data":null}`

测试更新进度：

```bash
curl -s -X POST http://localhost:8080/api/watchlist/update_progress \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"animeId":1,"episode":5}'
```

Expected: `{"status":1,"message":null,"data":{"id":1,"animeId":1,"status":"WATCHING","currentEpisode":5,...}}`

测试进度倒退（应报错）：

```bash
curl -s -X POST http://localhost:8080/api/watchlist/update_progress \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"animeId":1,"episode":3}'
```

Expected: `{"status":0,"message":"追番进度更新不合法","data":null}`

测试查询详情：

```bash
curl -s -X POST http://localhost:8080/api/watchlist/detail \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"animeId":1}'
```

Expected: 返回与上一次 `update_progress` 结果一致的详情（`currentEpisode=5`）

测试查询列表（按状态筛选）：

```bash
curl -s -X POST http://localhost:8080/api/watchlist/list \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"status":"WATCHING"}'
```

Expected: `{"status":1,"message":null,"data":[{"id":1,"animeId":1,"animeTitleCn":"败犬女主太多了！",...,"status":"WATCHING","currentEpisode":5,...}]}`，`animeTitleCn`/`animeTitleOriginal`/`animeCoverUrl` 均已正确拼接自 `t_anime` 表

测试查询列表（不传 status，返回全部）：

```bash
curl -s -X POST http://localhost:8080/api/watchlist/list \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{}'
```

Expected: 返回该用户全部追番记录（本例中与上一次结果一致，因为该用户只追了一部）

验证完成后按 `Ctrl+C` 停止应用。

**若当前环境暂无法访问本地 MySQL**：本 Step 2 需要在具备本地 MySQL 环境中执行；Step 1（自动化测试）不依赖真实数据库，可独立完成并验证。

- [ ] **Step 3: 无代码改动需要提交**

本任务仅做验证，未修改任何文件，无需执行 `git commit`。

---

## Self-Review

**1. Spec 覆盖度**：本 spec（`docs/superpowers/specs/2026-07-06-anitrack-phase1b-watchlist-design.md`）要求的领域模型（`WatchlistItem` 状态机与进度校验、`WatchStatusChangedEvent`，Task 1）、`WatchlistRepo`/`WatchlistDomainService` 跨上下文编排（`AnimeNotFoundException`/`WatchlistItemAlreadyExistsException`/`WatchlistItemNotFoundException`，Task 2）、持久化层（`t_watchlist_item`/`WatchlistItemPO`/`WatchlistItemMapper`/`AnimeMapper.selectByIds`，Task 3）、`WatchlistItemConverter`/`WatchlistRepoImpl`/`AnimeRepo.listByIds`（Task 4）、应用层（`WatchlistApplication` 5 个用例、异常转译、事件发布、`WatchlistAssembler`，Task 5）、Web 层（5 个接口，Task 6）均已覆盖。本 spec 明确的边界条件——`DROPPED→WATCHING` 保留进度（Task 1 测试覆盖）、`totalEpisodes=0` 视为无上限（Task 1 测试覆盖）、进度不允许倒退（Task 1 测试覆盖）、加入追番前校验番剧存在性与去重（Task 2 测试覆盖）、`listMyWatchlist` 批量查询而非 N+1（Task 4/5 的 `listByIds`/`WatchlistAssembler`）均已在对应任务体现。"明确不做的事"（监听器、分页、物理删除、评分字段）均未在任何任务中实现，符合 spec 的 YAGNI 范围。

**2. 占位符扫描**：全文所有 Step 均为可直接运行的完整代码/命令，无 TODO/"参考 Task N 类似实现"/"添加适当异常处理"等占位表述。

**3. 类型一致性检查**：`WatchlistItem.create`/`reconstitute`（Task 1 定义：`create(userId, animeId)`；`reconstitute(id, userId, animeId, status, currentEpisode, updateTime)`）在 Task 2 的 `WatchlistDomainServiceTest`、Task 4 的 `WatchlistItemConverter`/`WatchlistRepoImplTest`、Task 5 的 `WatchlistApplicationTest` 中参数顺序保持一致；`WatchlistRepo` 四个方法（`getByUserAndAnime`/`listByUser`/`add`/`update`，Task 2 定义）与 `WatchlistRepoImpl`（Task 4）、`WatchlistApplication`（Task 5）中的调用签名一致；`WatchlistDomainService.addToWatchlist(Long,Long)`/`updateProgress(Long,Long,Integer)`（Task 2 定义）与 `WatchlistApplication`（Task 5）中的调用签名一致；`AnimeRepo.listByIds(List<Long>)`（Task 4 新增）与 `WatchlistApplication.listMyWatchlist`（Task 5）、`AnimeRepoImplTest`（Task 4）中的调用签名一致；`WatchlistItemBO`/`WatchlistItemViewBO` 字段（Task 5 定义）与 Task 6 的 `HttpConverter`/`WatchlistItemResponse`/`WatchlistItemViewResponse`/`WatchlistControllerTest` 中的字段完全对应；`AppExceptionEnum` 新增的四个常量（Task 5）与 Task 5/6 测试中的断言消息完全一致。

---

## 与用户的既定流程约束（重申）

- 所有标注"需先征得用户同意后再执行"的 `git commit` 步骤，执行前必须先向用户说明将要提交的内容，由用户决定是否执行。
- 本机默认 `mvn` 版本过旧（见 Global Constraints），执行本计划前必须先切换 `JAVA_HOME`/`PATH`，否则所有 `mvn` 命令都会因 Maven/JDK 版本不满足要求而失败。
- Task 7 Step 2 的端到端联调依赖本地可用的 MySQL 环境，若当前环境不具备，可先完成 Step 1（自动化测试），Step 2 留到有本地 MySQL 的环境中执行。

