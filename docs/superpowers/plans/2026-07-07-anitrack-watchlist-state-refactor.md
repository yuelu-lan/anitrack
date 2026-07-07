# 追番状态机与进度逻辑重构 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 重构 WatchlistItem 状态机边、进度校验规则与 changeStatus 进度联动，使想看阶段可直接转看完/弃番、在看阶段进度可倒退、转看完时进度自动置满，并修复 updateTime 不刷新的遗留问题。

**Architecture:** 聚合根 `WatchlistItem` 扩展 `TRANSITIONS` 表与 `changeStatus(newStatus, totalEpisodes)` 签名，DomainService 统一校验集数有效性，应用层 `changeStatus` 改走 DomainService 编排。TDD：每任务先写失败测试再实现。

**Tech Stack:** Java 17 + Spring Boot 3.x + JUnit5 + Mockito + AssertJ

## Global Constraints

- 依赖方向：`starter → application → domain`；`infrastructure → domain`，不破坏
- 领域层不依赖 Spring 框架类型
- ORM 不变（MyBatis 原生 XML），不调整持久化层
- 命名遵循 `anitrack-project-rules.md`：聚合根方法 `changeStatus`/`updateProgress`，异常 `XxxException`
- 不新增 API 端点、不修改请求响应结构
- 不引入"进度自动转看完"

设计依据：`docs/superpowers/specs/2026-07-07-anitrack-watchlist-state-refactor-design.md`

---

### Task 1: 新增 AnimeTotalEpisodesInvalidException

**Files:**
- Create: `anitrack-domain/src/main/java/com/anitrack/domain/anime/exception/AnimeTotalEpisodesInvalidException.java`
- Test: `anitrack-domain/src/test/java/com/anitrack/domain/anime/exception/AnimeTotalEpisodesInvalidExceptionTest.java`

**Interfaces:**
- Produces: `AnimeTotalEpisodesInvalidException(Long animeId)`，继承 `AnitrackDomainException`，消息 `"番剧总集数无效: " + animeId`

- [ ] **Step 1: 写失败测试**

Create `anitrack-domain/src/test/java/com/anitrack/domain/anime/exception/AnimeTotalEpisodesInvalidExceptionTest.java`:

```java
package com.anitrack.domain.anime.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnimeTotalEpisodesInvalidExceptionTest {

    @Test
    void constructor_shouldCarryAnimeIdInMessage() {
        AnimeTotalEpisodesInvalidException ex = new AnimeTotalEpisodesInvalidException(100L);
        assertThat(ex.getMessage()).isEqualTo("番剧总集数无效: 100");
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd /Users/ywy/Desktop/my-project/anitrack && ./mvnw -pl anitrack-domain test -Dtest=AnimeTotalEpisodesInvalidExceptionTest -q`
Expected: FAIL，编译错误（类不存在）

- [ ] **Step 3: 写实现**

Create `anitrack-domain/src/main/java/com/anitrack/domain/anime/exception/AnimeTotalEpisodesInvalidException.java`:

```java
package com.anitrack.domain.anime.exception;

import com.anitrack.domain.common.AnitrackDomainException;

public class AnimeTotalEpisodesInvalidException extends AnitrackDomainException {

    public AnimeTotalEpisodesInvalidException(Long animeId) {
        super("番剧总集数无效: " + animeId);
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd /Users/ywy/Desktop/my-project/anitrack && ./mvnw -pl anitrack-domain test -Dtest=AnimeTotalEpisodesInvalidExceptionTest -q`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add anitrack-domain/src/main/java/com/anitrack/domain/anime/exception/AnimeTotalEpisodesInvalidException.java anitrack-domain/src/test/java/com/anitrack/domain/anime/exception/AnimeTotalEpisodesInvalidExceptionTest.java
git commit -m "feat(domain): 新增 AnimeTotalEpisodesInvalidException"
```

---

### Task 2: 扩展 WatchlistItem 状态机 TRANSITIONS 表

**Files:**
- Modify: `anitrack-domain/src/main/java/com/anitrack/domain/watchlist/model/WatchlistItem.java:20-25`
- Test: `anitrack-domain/src/test/java/com/anitrack/domain/watchlist/model/WatchlistItemTest.java`

**Interfaces:**
- Consumes: 无
- Produces: `TRANSITIONS` 表新增 `WANT_TO_WATCH → WATCHED/DROPPED`、`DROPPED → WANT_TO_WATCH`

**注意：** 本任务仅扩展状态机表，`changeStatus` 签名仍保持无参（旧签名）。签名扩展在 Task 4 做。本任务的测试用旧签名 `changeStatus(WatchStatus)` 验证新转移边。

- [ ] **Step 1: 写失败测试**

在 `WatchlistItemTest.java` 末尾新增（保留现有测试，仅替换下文指明的两条）：

```java
    @Test
    void changeStatus_whenWantToWatchToWatched_shouldSucceed() {
        // given
        WatchlistItem item = WatchlistItem.create(1L, 100L);

        // when
        item.changeStatus(WatchStatus.WATCHED);

        // then
        assertThat(item.getStatus()).isEqualTo(WatchStatus.WATCHED);
    }

    @Test
    void changeStatus_whenWantToWatchToDropped_shouldSucceed() {
        // given
        WatchlistItem item = WatchlistItem.create(1L, 100L);

        // when
        item.changeStatus(WatchStatus.DROPPED);

        // then
        assertThat(item.getStatus()).isEqualTo(WatchStatus.DROPPED);
    }

    @Test
    void changeStatus_whenDroppedToWantToWatch_shouldSucceedAndKeepProgress() {
        // given
        WatchlistItem item = WatchlistItem.create(1L, 100L);
        item.changeStatus(WatchStatus.WATCHING);
        item.updateProgress(5, 12);
        item.changeStatus(WatchStatus.DROPPED);

        // when
        item.changeStatus(WatchStatus.WANT_TO_WATCH);

        // then
        assertThat(item.getStatus()).isEqualTo(WatchStatus.WANT_TO_WATCH);
        assertThat(item.getCurrentEpisode()).isEqualTo(5);
    }
```

同时**删除**现有测试 `changeStatus_whenWantToWatchToWatched_shouldThrowException`（WatchlistItemTest.java:96-103），因为语义反转。

- [ ] **Step 2: 运行测试确认失败**

Run: `cd /Users/ywy/Desktop/my-project/anitrack && ./mvnw -pl anitrack-domain test -Dtest=WatchlistItemTest -q`
Expected: FAIL，新增的三个用例因 `IllegalWatchStatusTransitionException` 失败

- [ ] **Step 3: 改 TRANSITIONS 表**

修改 `WatchlistItem.java:20-25`，将 `TRANSITIONS` 改为：

```java
    private static final Map<WatchStatus, Set<WatchStatus>> TRANSITIONS = Map.of(
        WatchStatus.WANT_TO_WATCH, Set.of(WatchStatus.WATCHING, WatchStatus.WATCHED, WatchStatus.DROPPED),
        WatchStatus.WATCHING, Set.of(WatchStatus.WATCHED, WatchStatus.DROPPED),
        WatchStatus.WATCHED, Set.of(),
        WatchStatus.DROPPED, Set.of(WatchStatus.WATCHING, WatchStatus.WANT_TO_WATCH)
    );
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd /Users/ywy/Desktop/my-project/anitrack && ./mvnw -pl anitrack-domain test -Dtest=WatchlistItemTest -q`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add anitrack-domain/src/main/java/com/anitrack/domain/watchlist/model/WatchlistItem.java anitrack-domain/src/test/java/com/anitrack/domain/watchlist/model/WatchlistItemTest.java
git commit -m "feat(domain): 扩展 WatchlistItem 状态机转移边"
```

---

### Task 3: 调整 updateProgress 校验规则与 updateTime 刷新

**Files:**
- Modify: `anitrack-domain/src/main/java/com/anitrack/domain/watchlist/model/WatchlistItem.java:64-78`
- Test: `anitrack-domain/src/test/java/com/anitrack/domain/watchlist/model/WatchlistItemTest.java`

**Interfaces:**
- Produces: `updateProgress(Integer episode, Integer totalEpisodes)` 校验改为 `episode >= 0`、删倒退校验、刷 `updateTime`

**注意：** `updateTime` 字段当前在聚合根内无 setter，`reconstitute` 可传入。本任务在 `updateProgress` 内用 `LocalDateTime.now()` 刷新。需新增 `import java.time.LocalDateTime;`（已存在）。

- [ ] **Step 1: 写失败测试**

在 `WatchlistItemTest.java` 中：

**删除** `updateProgress_whenEpisodeIsZero_shouldThrowException`（:139-147），替换为：

```java
    @Test
    void updateProgress_whenEpisodeIsZero_shouldSucceed() {
        // given
        WatchlistItem item = WatchlistItem.create(1L, 100L);
        item.changeStatus(WatchStatus.WATCHING);

        // when
        item.updateProgress(0, 12);

        // then
        assertThat(item.getCurrentEpisode()).isZero();
    }
```

**删除** `updateProgress_whenEpisodeRegresses_shouldThrowException`（:150-159），替换为：

```java
    @Test
    void updateProgress_whenEpisodeRegresses_shouldSucceed() {
        // given
        WatchlistItem item = WatchlistItem.create(1L, 100L);
        item.changeStatus(WatchStatus.WATCHING);
        item.updateProgress(5, 12);

        // when
        item.updateProgress(3, 12);

        // then
        assertThat(item.getCurrentEpisode()).isEqualTo(3);
    }
```

**新增** updateTime 刷新测试：

```java
    @Test
    void updateProgress_whenValid_shouldRefreshUpdateTime() {
        // given
        WatchlistItem item = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WATCHING, 0, null);

        // when
        item.updateProgress(5, 12);

        // then
        assertThat(item.getUpdateTime()).isNotNull();
    }
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd /Users/ywy/Desktop/my-project/anitrack && ./mvnw -pl anitrack-domain test -Dtest=WatchlistItemTest -q`
Expected: FAIL，episode=0 和倒退用例因 `IllegalWatchProgressException` 失败；updateTime 用例因 `updateTime` 为 null 失败

- [ ] **Step 3: 改 updateProgress 实现**

修改 `WatchlistItem.java:64-78`，将 `updateProgress` 改为：

```java
    public void updateProgress(Integer episode, Integer totalEpisodes) {
        if (this.status != WatchStatus.WATCHING) {
            throw new IllegalWatchProgressException("只有观看中的番剧才能更新进度");
        }
        if (episode == null || episode < 0) {
            throw new IllegalWatchProgressException("观看进度必须大于等于0");
        }
        if (totalEpisodes != null && totalEpisodes > 0 && episode > totalEpisodes) {
            throw new IllegalWatchProgressException("观看进度不能超过总集数");
        }
        this.currentEpisode = episode;
        this.updateTime = LocalDateTime.now();
    }
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd /Users/ywy/Desktop/my-project/anitrack && ./mvnw -pl anitrack-domain test -Dtest=WatchlistItemTest -q`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add anitrack-domain/src/main/java/com/anitrack/domain/watchlist/model/WatchlistItem.java anitrack-domain/src/test/java/com/anitrack/domain/watchlist/model/WatchlistItemTest.java
git commit -m "feat(domain): 放宽 updateProgress 校验并刷新 updateTime"
```

---

### Task 4: 扩展 changeStatus 签名与进度联动

**Files:**
- Modify: `anitrack-domain/src/main/java/com/anitrack/domain/watchlist/model/WatchlistItem.java:55-62`
- Test: `anitrack-domain/src/test/java/com/anitrack/domain/watchlist/model/WatchlistItemTest.java`

**Interfaces:**
- Consumes: `AnimeTotalEpisodesInvalidException`（Task 1）
- Produces: `WatchStatusChangedEvent changeStatus(WatchStatus newStatus, Integer totalEpisodes)`，转 WATCHED 时进度置满 + 集数无效自我保护

**注意：** 此任务改变 `changeStatus` 签名（无参 → 带 totalEpisodes），会破坏所有现有调用方（Task 2 的测试、WatchlistDomainServiceTest、WatchlistApplicationTest）。本任务先改聚合根与聚合根测试；后续任务逐层修复调用方。

- [ ] **Step 1: 写失败测试**

在 `WatchlistItemTest.java` 中：

**替换** `changeStatus_whenWantToWatchToWatching_shouldSucceedAndReturnEvent`（:38-51）等所有现有 `changeStatus(WatchStatus)` 调用为 `changeStatus(WatchStatus, Integer)`。具体地，全局将 `item.changeStatus(WatchStatus.XXX)` 替换为 `item.changeStatus(WatchStatus.XXX, 12)`（测试默认 totalEpisodes=12）。

**新增** 转看完进度置满测试：

```java
    @Test
    void changeStatus_whenTransitionToWatched_shouldSetProgressToTotalEpisodes() {
        // given
        WatchlistItem item = WatchlistItem.create(1L, 100L);
        item.changeStatus(WatchStatus.WATCHING, 12);
        item.updateProgress(5, 12);

        // when
        item.changeStatus(WatchStatus.WATCHED, 12);

        // then
        assertThat(item.getStatus()).isEqualTo(WatchStatus.WATCHED);
        assertThat(item.getCurrentEpisode()).isEqualTo(12);
    }

    @Test
    void changeStatus_whenWantToWatchToWatched_shouldSetProgressToTotalEpisodes() {
        // given
        WatchlistItem item = WatchlistItem.create(1L, 100L);

        // when
        item.changeStatus(WatchStatus.WATCHED, 12);

        // then
        assertThat(item.getStatus()).isEqualTo(WatchStatus.WATCHED);
        assertThat(item.getCurrentEpisode()).isEqualTo(12);
    }

    @Test
    void changeStatus_whenTransitionToWatchedAndTotalEpisodesInvalid_shouldThrow() {
        // given
        WatchlistItem item = WatchlistItem.create(1L, 100L);
        item.changeStatus(WatchStatus.WATCHING, 12);

        // when & then
        assertThatThrownBy(() -> item.changeStatus(WatchStatus.WATCHED, null))
            .isInstanceOf(AnimeTotalEpisodesInvalidException.class);
        assertThatThrownBy(() -> item.changeStatus(WatchStatus.WATCHED, 0))
            .isInstanceOf(AnimeTotalEpisodesInvalidException.class);
    }
```

新增 import：`import com.anitrack.domain.anime.exception.AnimeTotalEpisodesInvalidException;`

- [ ] **Step 2: 运行测试确认失败**

Run: `cd /Users/ywy/Desktop/my-project/anitrack && ./mvnw -pl anitrack-domain test -Dtest=WatchlistItemTest -q`
Expected: FAIL，编译错误（changeStatus 签名不匹配）

- [ ] **Step 3: 改 changeStatus 实现**

修改 `WatchlistItem.java:55-62`，将 `changeStatus` 改为：

```java
    public WatchStatusChangedEvent changeStatus(WatchStatus newStatus, Integer totalEpisodes) {
        if (!TRANSITIONS.getOrDefault(this.status, Set.of()).contains(newStatus)) {
            throw new IllegalWatchStatusTransitionException(this.status, newStatus);
        }
        if (newStatus == WatchStatus.WATCHED) {
            if (totalEpisodes == null || totalEpisodes <= 0) {
                throw new AnimeTotalEpisodesInvalidException(this.animeId);
            }
            this.currentEpisode = totalEpisodes;
        }
        WatchStatus oldStatus = this.status;
        this.status = newStatus;
        return new WatchStatusChangedEvent(this.userId, this.animeId, oldStatus, newStatus);
    }
```

新增 import：`import com.anitrack.domain.anime.exception.AnimeTotalEpisodesInvalidException;`

- [ ] **Step 4: 运行测试确认通过**

Run: `cd /Users/ywy/Desktop/my-project/anitrack && ./mvnw -pl anitrack-domain test -Dtest=WatchlistItemTest -q`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add anitrack-domain/src/main/java/com/anitrack/domain/watchlist/model/WatchlistItem.java anitrack-domain/src/test/java/com/anitrack/domain/watchlist/model/WatchlistItemTest.java
git commit -m "feat(domain): changeStatus 签名扩展，转看完时进度置满"
```

---

### Task 5: DomainService.addToWatchlist 移除集数校验 + 新增 changeStatus 编排

**Files:**
- Modify: `anitrack-domain/src/main/java/com/anitrack/domain/watchlist/service/WatchlistDomainService.java`
- Test: `anitrack-domain/src/test/java/com/anitrack/domain/watchlist/service/WatchlistDomainServiceTest.java`

**Interfaces:**
- Consumes: `WatchlistItem.changeStatus(WatchStatus, Integer)`（Task 4）、`AnimeTotalEpisodesInvalidException`（Task 1）
- Produces: `WatchStatusChangedEvent changeStatus(Long userId, Long animeId, WatchStatus newStatus)`；`addToWatchlist` 不再校验集数

**设计决策：** DomainService.changeStatus 返回 `WatchStatusChangedEvent`（非 `WatchlistItem`），供应用层发布。应用层用 event 中的 userId+animeId 重新查 item 转 BO。原因：聚合根 `changeStatus` 已返回 event，透传最自然；应用层多一次 repo 查询换取职责清晰。

- [ ] **Step 1: 写失败测试**

修改 `WatchlistDomainServiceTest.java`：

**调整** `createTestAnime` 保持 totalEpisodes=12 不变。

**新增** addToWatchlist 集数异常允许追番用例：

```java
    @Test
    void addToWatchlist_whenTotalEpisodesInvalid_shouldStillAllowAdd() {
        // given
        Anime animeWithInvalidEpisodes = Anime.reconstitute(100L, 999L, "中文名", "Original Title", "http://cover.jpg",
            null, LocalDate.of(2024, 1, 1), "简介");
        when(mockAnimeRepo.getById(100L)).thenReturn(animeWithInvalidEpisodes);
        when(mockWatchlistRepo.getByUserAndAnime(1L, 100L)).thenReturn(null);
        WatchlistItem saved = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WANT_TO_WATCH, 0, null);
        when(mockWatchlistRepo.add(any(WatchlistItem.class))).thenReturn(saved);

        // when
        WatchlistItem result = sut.addToWatchlist(1L, 100L);

        // then
        assertThat(result).isEqualTo(saved);
        verify(mockWatchlistRepo, times(1)).add(any(WatchlistItem.class));
    }
```

**新增** changeStatus 编排用例：

```java
    @Test
    void changeStatus_whenTransitionToWatching_shouldDelegateAndUpdateAndReturnEvent() {
        // given
        WatchlistItem item = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WANT_TO_WATCH, 0, null);
        when(mockWatchlistRepo.getByUserAndAnime(1L, 100L)).thenReturn(item);
        when(mockAnimeRepo.getById(100L)).thenReturn(createTestAnime());

        // when
        WatchStatusChangedEvent event = sut.changeStatus(1L, 100L, WatchStatus.WATCHING);

        // then
        assertThat(event.oldStatus()).isEqualTo(WatchStatus.WANT_TO_WATCH);
        assertThat(event.newStatus()).isEqualTo(WatchStatus.WATCHING);
        verify(mockWatchlistRepo, times(1)).update(item);
    }

    @Test
    void changeStatus_whenTransitionToWatched_shouldSetProgressToTotalEpisodes() {
        // given
        WatchlistItem item = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WATCHING, 5, null);
        when(mockWatchlistRepo.getByUserAndAnime(1L, 100L)).thenReturn(item);
        when(mockAnimeRepo.getById(100L)).thenReturn(createTestAnime());

        // when
        sut.changeStatus(1L, 100L, WatchStatus.WATCHED);

        // then
        assertThat(item.getStatus()).isEqualTo(WatchStatus.WATCHED);
        assertThat(item.getCurrentEpisode()).isEqualTo(12);
        verify(mockWatchlistRepo, times(1)).update(item);
    }

    @Test
    void changeStatus_whenTransitionToWatchingAndTotalEpisodesInvalid_shouldThrow() {
        // given
        Anime animeWithInvalidEpisodes = Anime.reconstitute(100L, 999L, "中文名", "Original Title", "http://cover.jpg",
            null, LocalDate.of(2024, 1, 1), "简介");
        WatchlistItem item = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WANT_TO_WATCH, 0, null);
        when(mockWatchlistRepo.getByUserAndAnime(1L, 100L)).thenReturn(item);
        when(mockAnimeRepo.getById(100L)).thenReturn(animeWithInvalidEpisodes);

        // when & then
        assertThatThrownBy(() -> sut.changeStatus(1L, 100L, WatchStatus.WATCHING))
            .isInstanceOf(AnimeTotalEpisodesInvalidException.class);

        verify(mockWatchlistRepo, never()).update(any());
    }

    @Test
    void changeStatus_whenTransitionToDropped_shouldNotCheckTotalEpisodes() {
        // given
        Anime animeWithInvalidEpisodes = Anime.reconstitute(100L, 999L, "中文名", "Original Title", "http://cover.jpg",
            null, LocalDate.of(2024, 1, 1), "简介");
        WatchlistItem item = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WATCHING, 5, null);
        when(mockWatchlistRepo.getByUserAndAnime(1L, 100L)).thenReturn(item);
        when(mockAnimeRepo.getById(100L)).thenReturn(animeWithInvalidEpisodes);

        // when
        sut.changeStatus(1L, 100L, WatchStatus.DROPPED);

        // then
        assertThat(item.getStatus()).isEqualTo(WatchStatus.DROPPED);
        verify(mockWatchlistRepo, times(1)).update(item);
    }

    @Test
    void changeStatus_whenItemNotFound_shouldThrowException() {
        // given
        when(mockWatchlistRepo.getByUserAndAnime(1L, 100L)).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> sut.changeStatus(1L, 100L, WatchStatus.WATCHING))
            .isInstanceOf(WatchlistItemNotFoundException.class);

        verify(mockWatchlistRepo, never()).update(any());
    }

    @Test
    void changeStatus_whenAnimeNotFound_shouldThrowException() {
        // given
        WatchlistItem item = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WANT_TO_WATCH, 0, null);
        when(mockWatchlistRepo.getByUserAndAnime(1L, 100L)).thenReturn(item);
        when(mockAnimeRepo.getById(100L)).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> sut.changeStatus(1L, 100L, WatchStatus.WATCHING))
            .isInstanceOf(AnimeNotFoundException.class);

        verify(mockWatchlistRepo, never()).update(any());
    }
```

新增 import：`import com.anitrack.domain.anime.exception.AnimeTotalEpisodesInvalidException;`、`import com.anitrack.domain.watchlist.exception.WatchlistItemNotFoundException;`（若未存在）、`import com.anitrack.domain.watchlist.model.WatchStatusChangedEvent;`。

DomainService 实现需新增 import：`import com.anitrack.domain.watchlist.model.WatchStatusChangedEvent;`、`import com.anitrack.domain.anime.exception.AnimeTotalEpisodesInvalidException;`。

- [ ] **Step 2: 运行测试确认失败**

Run: `cd /Users/ywy/Desktop/my-project/anitrack && ./mvnw -pl anitrack-domain test -Dtest=WatchlistDomainServiceTest -q`
Expected: FAIL，`changeStatus` 方法不存在、`addToWatchlist_whenTotalEpisodesInvalid` 不符合现有行为

- [ ] **Step 3: 改 DomainService 实现**

修改 `WatchlistDomainService.java`，整体替换为：

```java
package com.anitrack.domain.watchlist.service;

import com.anitrack.domain.anime.exception.AnimeNotFoundException;
import com.anitrack.domain.anime.exception.AnimeTotalEpisodesInvalidException;
import com.anitrack.domain.anime.model.Anime;
import com.anitrack.domain.anime.repo.AnimeRepo;
import com.anitrack.domain.watchlist.enums.WatchStatus;
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

    public WatchStatusChangedEvent changeStatus(Long userId, Long animeId, WatchStatus newStatus) {
        WatchlistItem item = watchlistRepo.getByUserAndAnime(userId, animeId);
        if (item == null) {
            throw new WatchlistItemNotFoundException(userId, animeId);
        }
        Anime anime = animeRepo.getById(animeId);
        if (anime == null) {
            throw new AnimeNotFoundException(animeId);
        }
        Integer totalEpisodes = anime.getTotalEpisodes();
        if ((newStatus == WatchStatus.WATCHING || newStatus == WatchStatus.WATCHED)
            && (totalEpisodes == null || totalEpisodes <= 0)) {
            throw new AnimeTotalEpisodesInvalidException(animeId);
        }
        WatchStatusChangedEvent event = item.changeStatus(newStatus, totalEpisodes);
        watchlistRepo.update(item);
        return event;
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd /Users/ywy/Desktop/my-project/anitrack && ./mvnw -pl anitrack-domain test -Dtest=WatchlistDomainServiceTest -q`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add anitrack-domain/src/main/java/com/anitrack/domain/watchlist/service/WatchlistDomainService.java anitrack-domain/src/test/java/com/anitrack/domain/watchlist/service/WatchlistDomainServiceTest.java
git commit -m "feat(domain): DomainService 新增 changeStatus 编排与集数校验"
```

---

### Task 6: AppExceptionEnum 新增 ANIME_TOTAL_EPISODES_INVALID

**Files:**
- Modify: `anitrack-application/src/main/java/com/anitrack/application/exception/AppExceptionEnum.java`

**Interfaces:**
- Produces: `AppExceptionEnum.ANIME_TOTAL_EPISODES_INVALID`（code 40103，消息"番剧总集数无效"）

- [ ] **Step 1: 写实现**（枚举无独立测试，由应用层测试覆盖）

修改 `AppExceptionEnum.java`，在 `ANIME_NOT_FOUND(40102, "番剧不存在")` 后新增一行：

```java
    ANIME_TOTAL_EPISODES_INVALID(40103, "番剧总集数无效"),
```

- [ ] **Step 2: 运行编译确认**

Run: `cd /Users/ywy/Desktop/my-project/anitrack && ./mvnw -pl anitrack-application compile -q`
Expected: 编译成功

- [ ] **Step 3: 提交**

```bash
git add anitrack-application/src/main/java/com/anitrack/application/exception/AppExceptionEnum.java
git commit -m "feat(application): 新增 ANIME_TOTAL_EPISODES_INVALID 枚举"
```

---

### Task 7: WatchlistApplication.changeStatus 改走 DomainService

**Files:**
- Modify: `anitrack-application/src/main/java/com/anitrack/application/service/WatchlistApplication.java:50-65`
- Test: `anitrack-application/src/test/java/com/anitrack/application/service/WatchlistApplicationTest.java`

**Interfaces:**
- Consumes: `WatchStatusChangedEvent changeStatus(Long, Long, WatchStatus)`（Task 5）、`AnimeTotalEpisodesInvalidException`（Task 1）
- Produces: `WatchlistApplication.changeStatus` 委托 DomainService，发布 event，重新查 item 转 BO

- [ ] **Step 1: 写失败测试**

修改 `WatchlistApplicationTest.java`：

**删除** 现有 `changeStatus_whenItemExists_shouldUpdateAndPublishEvent`（:92-105）、`changeStatus_whenItemNotFound_shouldThrowAppException`（:107-118）、`changeStatus_whenTransitionIllegal_shouldThrowAppException`（:120-133），替换为：

```java
    @Test
    void changeStatus_whenSucceeds_shouldReturnBOAndPublishEvent() {
        // given
        WatchStatusChangedEvent event = new WatchStatusChangedEvent(1L, 100L, WatchStatus.WATCHING, WatchStatus.WATCHED);
        WatchlistItem item = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WATCHED, 12, null);
        when(mockWatchlistDomainService.changeStatus(1L, 100L, WatchStatus.WATCHED)).thenReturn(event);
        when(mockWatchlistRepo.getByUserAndAnime(1L, 100L)).thenReturn(item);

        // when
        WatchlistItemBO result = sut.changeStatus(1L, 100L, WatchStatus.WATCHED);

        // then
        assertThat(result.getStatus()).isEqualTo(WatchStatus.WATCHED);
        verify(mockEventPublisher, times(1)).publishEvent(event);
    }

    @Test
    void changeStatus_whenItemNotFound_shouldThrowAppException() {
        // given
        when(mockWatchlistDomainService.changeStatus(1L, 100L, WatchStatus.WATCHING))
            .thenThrow(new WatchlistItemNotFoundException(1L, 100L));

        // when & then
        assertThatThrownBy(() -> sut.changeStatus(1L, 100L, WatchStatus.WATCHING))
            .isInstanceOf(AnitrackAppException.class)
            .hasMessageContaining("追番记录不存在");

        verify(mockEventPublisher, never()).publishEvent(any());
    }

    @Test
    void changeStatus_whenTransitionIllegal_shouldThrowAppException() {
        // given
        when(mockWatchlistDomainService.changeStatus(1L, 100L, WatchStatus.WATCHING))
            .thenThrow(new IllegalWatchStatusTransitionException(WatchStatus.WATCHED, WatchStatus.WATCHING));

        // when & then
        assertThatThrownBy(() -> sut.changeStatus(1L, 100L, WatchStatus.WATCHING))
            .isInstanceOf(AnitrackAppException.class)
            .hasMessageContaining("非法的追番状态转移");

        verify(mockEventPublisher, never()).publishEvent(any());
    }

    @Test
    void changeStatus_whenTotalEpisodesInvalid_shouldThrowAppException() {
        // given
        when(mockWatchlistDomainService.changeStatus(1L, 100L, WatchStatus.WATCHING))
            .thenThrow(new AnimeTotalEpisodesInvalidException(100L));

        // when & then
        assertThatThrownBy(() -> sut.changeStatus(1L, 100L, WatchStatus.WATCHING))
            .isInstanceOf(AnitrackAppException.class)
            .hasMessageContaining("番剧总集数无效");

        verify(mockEventPublisher, never()).publishEvent(any());
    }
```

新增 import：`import com.anitrack.domain.anime.exception.AnimeTotalEpisodesInvalidException;`、`import com.anitrack.domain.watchlist.exception.IllegalWatchStatusTransitionException;`、`import com.anitrack.domain.watchlist.exception.WatchlistItemNotFoundException;`、`import static org.mockito.Mockito.*;`（确认）。

- [ ] **Step 2: 运行测试确认失败**

Run: `cd /Users/ywy/Desktop/my-project/anitrack && ./mvnw -pl anitrack-application test -Dtest=WatchlistApplicationTest -q`
Expected: FAIL，`changeStatus` 仍直接操作 repo，未调用 `mockWatchlistDomainService.changeStatus`

- [ ] **Step 3: 改 WatchlistApplication.changeStatus 实现**

修改 `WatchlistApplication.java:50-65`，将 `changeStatus` 替换为：

```java
    @Transactional
    public WatchlistItemBO changeStatus(Long userId, Long animeId, WatchStatus newStatus) {
        WatchStatusChangedEvent event;
        try {
            event = watchlistDomainService.changeStatus(userId, animeId, newStatus);
        } catch (WatchlistItemNotFoundException e) {
            throw new AnitrackAppException(AppExceptionEnum.WATCHLIST_ITEM_NOT_FOUND);
        } catch (AnimeNotFoundException e) {
            throw new AnitrackAppException(AppExceptionEnum.ANIME_NOT_FOUND);
        } catch (AnimeTotalEpisodesInvalidException e) {
            throw new AnitrackAppException(AppExceptionEnum.ANIME_TOTAL_EPISODES_INVALID);
        } catch (IllegalWatchStatusTransitionException e) {
            throw new AnitrackAppException(AppExceptionEnum.ILLEGAL_WATCH_STATUS_TRANSITION);
        }
        eventPublisher.publishEvent(event);
        WatchlistItem item = watchlistRepo.getByUserAndAnime(userId, animeId);
        return toBO(item);
    }
```

新增 import：`import com.anitrack.domain.anime.exception.AnimeTotalEpisodesInvalidException;`、`import com.anitrack.domain.watchlist.exception.IllegalWatchStatusTransitionException;`、`import com.anitrack.domain.watchlist.exception.WatchlistItemNotFoundException;`

- [ ] **Step 4: 运行测试确认通过**

Run: `cd /Users/ywy/Desktop/my-project/anitrack && ./mvnw -pl anitrack-application test -Dtest=WatchlistApplicationTest -q`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add anitrack-application/src/main/java/com/anitrack/application/service/WatchlistApplication.java anitrack-application/src/test/java/com/anitrack/application/service/WatchlistApplicationTest.java
git commit -m "refactor(application): changeStatus 改走 DomainService 编排"
```

---

### Task 8: WatchlistControllerTest 调整非法转移样例

**Files:**
- Test: `anitrack-starter/src/test/java/com/anitrack/starter/controller/WatchlistControllerTest.java:160-175`

**Interfaces:** 无（仅测试调整）

- [ ] **Step 1: 改测试**

修改 `WatchlistControllerTest.java` 的 `changeStatus_whenIllegalTransition_shouldReturnBusinessError`（:160-175）。原用 `WATCHED` 作为 `WANT_TO_WATCH` 的非法目标，现合法。改为用 `WATCHED → WATCHING` 非法转移：

```java
    @Test
    void changeStatus_whenIllegalTransition_shouldReturnBusinessError() throws Exception {
        // given
        stubValidToken();
        doThrow(new com.anitrack.application.exception.AnitrackAppException(
                com.anitrack.application.exception.AppExceptionEnum.ILLEGAL_WATCH_STATUS_TRANSITION))
            .when(mockWatchlistApplication).changeStatus(1L, 100L, WatchStatus.WATCHING);

        // when & then
        mockMvc.perform(post("/api/watchlist/change_status")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L, "status", "WATCHING"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(0));
    }
```

- [ ] **Step 2: 运行测试确认通过**

Run: `cd /Users/ywy/Desktop/my-project/anitrack && ./mvnw -pl anitrack-starter test -Dtest=WatchlistControllerTest -q`
Expected: PASS

- [ ] **Step 3: 提交**

```bash
git add anitrack-starter/src/test/java/com/anitrack/starter/controller/WatchlistControllerTest.java
git commit -m "test(web): 调整 changeStatus 非法转移测试样例"
```

---

### Task 9: 全量构建与回归

**Files:** 无

- [ ] **Step 1: 全量测试**

Run: `cd /Users/ywy/Desktop/my-project/anitrack && ./mvnw test -q`
Expected: 全部 PASS

- [ ] **Step 2: 检查未提交改动**

Run: `cd /Users/ywy/Desktop/my-project/anitrack && git status`
Expected: clean

- [ ] **Step 3: 完成**

无提交（仅验证）。

---

## 自检与已知风险

1. **Task 4 破坏现有调用方**：`changeStatus` 签名改变会一次性破坏 Task 2 测试、WatchlistDomainServiceTest、WatchlistApplicationTest。Task 4 只修复聚合根测试，Task 5/7 逐层修复。中间状态编译不通过属正常，按任务顺序执行即可。
2. **事件透传设计**：DomainService.changeStatus 返回 `WatchStatusChangedEvent`，应用层发布后重新 `watchlistRepo.getByUserAndAnime` 取 item 转 BO。多一次查询换取职责清晰（event 由聚合根产生，透传到应用层发布）。
3. **未覆盖前端**：前端调用 `changeStatus` 时传入的 status 值范围可能需调整（新增 WANT_TO_WATCH → WATCHED 等入口），本计划不涉及。
