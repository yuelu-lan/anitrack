# anitrack Phase 2：评价（Review）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现评价（`Review`）上下文：用户对已看完（`WATCHED`）番剧的评分（1-10）与评论（可选），支持新增/修改/查看我的评价/按番剧分页列表/我的评价列表 5 个接口。

**Architecture:** `anitrack-domain.review` 定义 `Review` 聚合根（内置 score 校验）、`ReviewRepo` 仓储接口、`ReviewDomainService`（跨上下文编排：查询 `domain.watchlist.repo.WatchlistRepo` 校验 `WATCHED` 状态）与相关领域异常；`anitrack-infrastructure` 用 MyBatis 原生 XML 实现 `ReviewRepo`，并给已有 `UserRepo` 补充 `listByIds` 批量查询；`anitrack-application` 的 `ReviewApplication` 编排 5 个用例、把领域异常转译为 `AnitrackAppException`，`ReviewAssembler` 拼接 `Review + User`/`Review + Anime` 生成两种列表展示模型；`anitrack-starter` 承载 `ReviewController`、请求/响应对象，以及项目首次落地的通用 `PageResponse<T>`。

**Tech Stack:** Java 17、Spring Boot 3.5.3、MyBatis 原生 XML（沿用 `t_watchlist_item` 同款结构）、MapStruct 1.6.3（`ReviewPO <-> Review`）、Lombok、JUnit5 + Mockito + AssertJ、`@WebMvcTest` + MockMvc。

## Global Constraints

- 依赖方向：`starter → application → domain`；`infrastructure → domain`；仓储接口定义在 `domain`，`infrastructure` 实现；`application` 不直接依赖 `infrastructure`（`docs/rules/anitrack-project-rules.md`）
- 聚合根内部字段通过构造方法或工厂方法保证初始状态合法，不暴露无校验的 setter；业务规则写在聚合根方法内部（`docs/rules/anitrack-model-rules.md`）
- 跨聚合、跨上下文的业务规则放在 `XxxDomainService` 里，只依赖 `domain` 层仓储接口；单个聚合根内部能完成的逻辑不需要领域服务（`docs/rules/anitrack-model-rules.md`）
- 领域层不依赖任何 Spring 框架类型；`ReviewDomainService` 是不带任何 Spring 注解的纯类，其 Bean 注册通过 `anitrack-application` 层已有的 `DomainServiceConfig` 补充一个 `@Bean` 方法完成
- `Review` 字段：`id, userId, animeId, score, content, createTime`；`score` 必须在 `[1,10]`，`content` 允许为空；不含 `updateTime`（本 spec：`docs/superpowers/specs/2026-07-06-anitrack-phase2-review-design.md`，下称"本 spec"）
- 跨上下文规则："只有 `WATCHED` 状态才能评价"，在 `ReviewDomainService.addReview()` 里查询 `WatchlistRepo.getByUserAndAnime` 确认状态；`updateReview`/`getMyReview`/`listByAnime`/`listMyReviews` 不经过跨上下文校验，直接在 `ReviewApplication` 编排（本 spec）
- 一个用户对同一番剧只能有一条 `Review`（`userId + animeId` 唯一），由 `t_review` 表的 `uk_user_anime` 唯一索引与领域层的重复校验双重兜底（本 spec）
- 不设 `ReviewNotFoundException` 领域异常：`updateReview`/`getMyReview` 查不到记录属于纯应用层判断，直接抛 `AnitrackAppException(REVIEW_NOT_FOUND)`，不经过领域异常（本 spec）
- `listByAnime` 分页：`ReviewRepo.listByAnime(animeId, offset, limit)` + `countByAnime(animeId)` 分开定义，不在领域层引入 `Page<T>` 泛型；应用层 `ReviewPageBO<T>`/Web层 `PageResponse<T>` 字段为 `pageNum, pageSize, total, list`（`docs/rules/anitrack-web-rules.md` 第10.5节的既有项目约定，本次是首次落地实现）；`listMyReviews` 不分页（本 spec）
- 不使用 MyBatis-Plus，不允许联表查询，不允许使用外键（`docs/rules/anitrack-project-rules.md`）
- 断言统一使用 AssertJ `assertThat()`，禁止 `Assertions.assertEquals()`；Mock 交互验证必须明确指定次数（`times(1)` 等），不 Mock 值对象/DTO（`docs/rules/anitrack-unittest-rules.md`）
- 应用层统一异常使用 `AnitrackAppException(AppExceptionEnum)` 构造函数；`AppExceptionEnum` 新增常量延续 40xxx 编码段，Review 上下文使用 403xx（Watchlist 已用 402xx）
- 版本号统一由根 `pom.xml` 的 `<dependencyManagement>` 管理，子模块 `<dependency>` 不单独指定 `<version>`（`docs/rules/anitrack-init-dependency-rules.md`）
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
├── user/repo/UserRepo.java                                      # Modify: 新增 listByIds
└── review/
    ├── model/Review.java                                        # 聚合根：create()/reconstitute()/update()
    ├── repo/ReviewRepo.java                                      # 接口
    ├── service/ReviewDomainService.java                          # 跨上下文：addReview
    └── exception/
        ├── IllegalReviewScoreException.java
        ├── ReviewNotAllowedException.java
        └── ReviewAlreadyExistsException.java
anitrack-domain/src/test/java/com/anitrack/domain/review/
├── model/ReviewTest.java
└── service/ReviewDomainServiceTest.java

anitrack-starter/src/main/resources/db/migration/V4__create_review_table.sql
anitrack-infrastructure/src/main/java/com/anitrack/infra/
├── dal/po/ReviewPO.java
├── dal/mapper/ReviewMapper.java                                  # + resources/mapper/ReviewMapper.xml
├── dal/mapper/UserMapper.java                                    # Modify: 新增 selectByIds
├── converter/ReviewConverter.java                                # MapStruct: ReviewPO <-> Review
├── repo/ReviewRepoImpl.java                                      # implements ReviewRepo
└── repo/UserRepoImpl.java                                        # Modify: 新增 listByIds 实现
anitrack-infrastructure/src/main/resources/mapper/UserMapper.xml   # Modify: 新增 selectByIds
anitrack-infrastructure/src/test/java/com/anitrack/infra/repo/
├── ReviewRepoImplTest.java
└── UserRepoImplTest.java                                         # Create: 新文件，仅覆盖 listByIds

anitrack-application/src/main/java/com/anitrack/application/
├── exception/AppExceptionEnum.java                               # Modify: 新增 REVIEW_* 四个常量
├── model/ReviewBO.java
├── model/ReviewWithUserViewBO.java
├── model/ReviewWithAnimeViewBO.java
├── model/ReviewPageBO.java
├── assembler/ReviewAssembler.java
├── config/DomainServiceConfig.java                               # Modify: 新增 reviewDomainService bean
└── service/ReviewApplication.java
anitrack-application/src/test/java/com/anitrack/application/service/ReviewApplicationTest.java

anitrack-starter/src/main/java/com/anitrack/starter/
├── request/ReviewAddReq.java
├── request/ReviewUpdateReq.java
├── request/ReviewDetailReq.java
├── request/ReviewListByAnimeReq.java
├── response/PageResponse.java
├── response/ReviewResponse.java
├── response/ReviewWithUserResponse.java
├── response/ReviewWithAnimeResponse.java
├── converter/HttpConverter.java                                  # Modify: 新增 review 相关转换方法
└── controller/ReviewController.java
anitrack-starter/src/test/java/com/anitrack/starter/controller/ReviewControllerTest.java
```

---

### Task 1: Review 聚合根 + IllegalReviewScoreException

**Files:**
- Create: `anitrack-domain/src/main/java/com/anitrack/domain/review/exception/IllegalReviewScoreException.java`
- Create: `anitrack-domain/src/main/java/com/anitrack/domain/review/model/Review.java`
- Test: `anitrack-domain/src/test/java/com/anitrack/domain/review/model/ReviewTest.java`

**Interfaces:**
- Produces：`Review.create(Long userId, Long animeId, Integer score, String content): Review`；`Review.reconstitute(Long id, Long userId, Long animeId, Integer score, String content, LocalDateTime createTime): Review`；`Review.update(Integer score, String content): void`；`IllegalReviewScoreException(String message)`，供 Task 2 的 `ReviewDomainService`、Task 5 的 `ReviewApplication` 使用

- [ ] **Step 1: 编写 IllegalReviewScoreException**

```java
package com.anitrack.domain.review.exception;

import com.anitrack.domain.common.AnitrackDomainException;

public class IllegalReviewScoreException extends AnitrackDomainException {

    public IllegalReviewScoreException(String message) {
        super(message);
    }
}
```

- [ ] **Step 2: 编写失败的 ReviewTest**

```java
package com.anitrack.domain.review.model;

import com.anitrack.domain.review.exception.IllegalReviewScoreException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewTest {

    @Test
    void create_whenScoreIsValid_shouldInitializeFields() {
        // when
        Review review = Review.create(1L, 100L, 8, "很好看");

        // then
        assertThat(review.getId()).isNull();
        assertThat(review.getUserId()).isEqualTo(1L);
        assertThat(review.getAnimeId()).isEqualTo(100L);
        assertThat(review.getScore()).isEqualTo(8);
        assertThat(review.getContent()).isEqualTo("很好看");
    }

    @Test
    void create_whenContentIsNull_shouldAllow() {
        // when
        Review review = Review.create(1L, 100L, 10, null);

        // then
        assertThat(review.getContent()).isNull();
    }

    @Test
    void create_whenScoreIsNull_shouldThrowException() {
        // when & then
        assertThatThrownBy(() -> Review.create(1L, 100L, null, "content"))
            .isInstanceOf(IllegalReviewScoreException.class);
    }

    @Test
    void create_whenScoreIsZero_shouldThrowException() {
        // when & then
        assertThatThrownBy(() -> Review.create(1L, 100L, 0, "content"))
            .isInstanceOf(IllegalReviewScoreException.class);
    }

    @Test
    void create_whenScoreIsEleven_shouldThrowException() {
        // when & then
        assertThatThrownBy(() -> Review.create(1L, 100L, 11, "content"))
            .isInstanceOf(IllegalReviewScoreException.class);
    }

    @Test
    void create_whenScoreIsOne_shouldSucceed() {
        // when
        Review review = Review.create(1L, 100L, 1, null);

        // then
        assertThat(review.getScore()).isEqualTo(1);
    }

    @Test
    void create_whenScoreIsTen_shouldSucceed() {
        // when
        Review review = Review.create(1L, 100L, 10, null);

        // then
        assertThat(review.getScore()).isEqualTo(10);
    }

    @Test
    void reconstitute_whenCalled_shouldRestoreAllFields() {
        // given
        LocalDateTime createTime = LocalDateTime.of(2026, 1, 1, 0, 0);

        // when
        Review review = Review.reconstitute(10L, 1L, 100L, 8, "很好看", createTime);

        // then
        assertThat(review.getId()).isEqualTo(10L);
        assertThat(review.getScore()).isEqualTo(8);
        assertThat(review.getContent()).isEqualTo("很好看");
        assertThat(review.getCreateTime()).isEqualTo(createTime);
    }

    @Test
    void update_whenScoreIsValid_shouldReplaceScoreAndContent() {
        // given
        Review review = Review.create(1L, 100L, 8, "很好看");

        // when
        review.update(5, "重新打分");

        // then
        assertThat(review.getScore()).isEqualTo(5);
        assertThat(review.getContent()).isEqualTo("重新打分");
    }

    @Test
    void update_whenScoreIsInvalid_shouldThrowExceptionAndKeepOriginal() {
        // given
        Review review = Review.create(1L, 100L, 8, "很好看");

        // when & then
        assertThatThrownBy(() -> review.update(11, "重新打分"))
            .isInstanceOf(IllegalReviewScoreException.class);
        assertThat(review.getScore()).isEqualTo(8);
        assertThat(review.getContent()).isEqualTo("很好看");
    }
}
```

- [ ] **Step 3: 运行测试，确认失败**

Run: `mvn -q -pl anitrack-domain -am test -Dtest=ReviewTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL（`Review` 类不存在，编译错误）

- [ ] **Step 4: 编写 Review 聚合根**

```java
package com.anitrack.domain.review.model;

import com.anitrack.domain.review.exception.IllegalReviewScoreException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Review {

    private Long id;
    private Long userId;
    private Long animeId;
    private Integer score;
    private String content;
    private LocalDateTime createTime;

    public static Review create(Long userId, Long animeId, Integer score, String content) {
        validateScore(score);
        return Review.builder()
            .userId(userId)
            .animeId(animeId)
            .score(score)
            .content(content)
            .build();
    }

    public static Review reconstitute(Long id, Long userId, Long animeId, Integer score, String content,
                                       LocalDateTime createTime) {
        return Review.builder()
            .id(id)
            .userId(userId)
            .animeId(animeId)
            .score(score)
            .content(content)
            .createTime(createTime)
            .build();
    }

    public void update(Integer score, String content) {
        validateScore(score);
        this.score = score;
        this.content = content;
    }

    private static void validateScore(Integer score) {
        if (score == null || score < 1 || score > 10) {
            throw new IllegalReviewScoreException("评分必须在1-10之间");
        }
    }
}
```

- [ ] **Step 5: 运行测试，确认通过**

Run: `mvn -q -pl anitrack-domain -am test -Dtest=ReviewTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS（10 个测试通过）

- [ ] **Step 6: Commit**

```bash
git add anitrack-domain/src/main/java/com/anitrack/domain/review/exception/IllegalReviewScoreException.java anitrack-domain/src/main/java/com/anitrack/domain/review/model anitrack-domain/src/test/java/com/anitrack/domain/review/model
git commit -m "feat: 新增Review聚合根，实现评分1-10范围校验"
```

---

### Task 2: ReviewRepo 接口 + 跨上下文领域异常 + ReviewDomainService

**Files:**
- Create: `anitrack-domain/src/main/java/com/anitrack/domain/review/exception/ReviewNotAllowedException.java`
- Create: `anitrack-domain/src/main/java/com/anitrack/domain/review/exception/ReviewAlreadyExistsException.java`
- Create: `anitrack-domain/src/main/java/com/anitrack/domain/review/repo/ReviewRepo.java`
- Create: `anitrack-domain/src/main/java/com/anitrack/domain/review/service/ReviewDomainService.java`
- Test: `anitrack-domain/src/test/java/com/anitrack/domain/review/service/ReviewDomainServiceTest.java`

**Interfaces:**
- Consumes：`Review`（Task 1）、`WatchStatus`/`WatchlistItem`/`WatchlistRepo`（已存在，`domain.watchlist.enums.WatchStatus`/`domain.watchlist.model.WatchlistItem`/`domain.watchlist.repo.WatchlistRepo`）
- Produces：`ReviewRepo.getByUserAndAnime(Long, Long): Review`（查不到返回 `null`）／`listByAnime(Long, int, int): List<Review>`／`countByAnime(Long): long`／`listByUser(Long): List<Review>`／`add(Review): Review`／`update(Review): void`；`ReviewDomainService.addReview(Long userId, Long animeId, Integer score, String content): Review`，供 Task 4 的 `ReviewRepoImpl` 与 Task 5 的 `ReviewApplication` 使用；`ReviewNotAllowedException(Long userId, Long animeId)`／`ReviewAlreadyExistsException(Long userId, Long animeId)`

- [ ] **Step 1: 编写跨上下文领域异常**

```java
package com.anitrack.domain.review.exception;

import com.anitrack.domain.common.AnitrackDomainException;

public class ReviewNotAllowedException extends AnitrackDomainException {

    public ReviewNotAllowedException(Long userId, Long animeId) {
        super("用户" + userId + "尚未看完番剧" + animeId + "，不能评价");
    }
}
```

```java
package com.anitrack.domain.review.exception;

import com.anitrack.domain.common.AnitrackDomainException;

public class ReviewAlreadyExistsException extends AnitrackDomainException {

    public ReviewAlreadyExistsException(Long userId, Long animeId) {
        super("用户" + userId + "已经评价过番剧" + animeId);
    }
}
```

- [ ] **Step 2: 编写 ReviewRepo 接口**

```java
package com.anitrack.domain.review.repo;

import com.anitrack.domain.review.model.Review;

import java.util.List;

public interface ReviewRepo {

    Review getByUserAndAnime(Long userId, Long animeId);

    List<Review> listByAnime(Long animeId, int offset, int limit);

    long countByAnime(Long animeId);

    List<Review> listByUser(Long userId);

    Review add(Review review);

    void update(Review review);
}
```

- [ ] **Step 3: 编写失败的 ReviewDomainServiceTest**

```java
package com.anitrack.domain.review.service;

import com.anitrack.domain.review.exception.ReviewAlreadyExistsException;
import com.anitrack.domain.review.exception.ReviewNotAllowedException;
import com.anitrack.domain.review.model.Review;
import com.anitrack.domain.review.repo.ReviewRepo;
import com.anitrack.domain.watchlist.enums.WatchStatus;
import com.anitrack.domain.watchlist.model.WatchlistItem;
import com.anitrack.domain.watchlist.repo.WatchlistRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewDomainServiceTest {

    @Mock
    private ReviewRepo mockReviewRepo;

    @Mock
    private WatchlistRepo mockWatchlistRepo;

    @InjectMocks
    private ReviewDomainService sut;

    @Test
    void addReview_whenWatchedAndNotDuplicate_shouldCreateAndAdd() {
        // given
        WatchlistItem item = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WATCHED, 12, null);
        when(mockWatchlistRepo.getByUserAndAnime(1L, 100L)).thenReturn(item);
        when(mockReviewRepo.getByUserAndAnime(1L, 100L)).thenReturn(null);
        Review saved = Review.reconstitute(20L, 1L, 100L, 8, "很好看", null);
        when(mockReviewRepo.add(any(Review.class))).thenReturn(saved);

        // when
        Review result = sut.addReview(1L, 100L, 8, "很好看");

        // then
        assertThat(result).isEqualTo(saved);
        verify(mockReviewRepo, times(1)).add(any(Review.class));
    }

    @Test
    void addReview_whenNotWatched_shouldThrowException() {
        // given
        WatchlistItem item = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WATCHING, 5, null);
        when(mockWatchlistRepo.getByUserAndAnime(1L, 100L)).thenReturn(item);

        // when & then
        assertThatThrownBy(() -> sut.addReview(1L, 100L, 8, "很好看"))
            .isInstanceOf(ReviewNotAllowedException.class);

        verify(mockReviewRepo, never()).add(any());
    }

    @Test
    void addReview_whenNoWatchlistItem_shouldThrowException() {
        // given
        when(mockWatchlistRepo.getByUserAndAnime(1L, 100L)).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> sut.addReview(1L, 100L, 8, "很好看"))
            .isInstanceOf(ReviewNotAllowedException.class);

        verify(mockReviewRepo, never()).add(any());
    }

    @Test
    void addReview_whenAlreadyExists_shouldThrowException() {
        // given
        WatchlistItem item = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WATCHED, 12, null);
        when(mockWatchlistRepo.getByUserAndAnime(1L, 100L)).thenReturn(item);
        Review existing = Review.reconstitute(20L, 1L, 100L, 7, null, null);
        when(mockReviewRepo.getByUserAndAnime(1L, 100L)).thenReturn(existing);

        // when & then
        assertThatThrownBy(() -> sut.addReview(1L, 100L, 8, "很好看"))
            .isInstanceOf(ReviewAlreadyExistsException.class);

        verify(mockReviewRepo, never()).add(any());
    }
}
```

- [ ] **Step 4: 运行测试，确认失败**

Run: `mvn -q -pl anitrack-domain -am test -Dtest=ReviewDomainServiceTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL（`ReviewDomainService` 类不存在，编译错误）

- [ ] **Step 5: 编写 ReviewDomainService**

```java
package com.anitrack.domain.review.service;

import com.anitrack.domain.review.exception.ReviewAlreadyExistsException;
import com.anitrack.domain.review.exception.ReviewNotAllowedException;
import com.anitrack.domain.review.model.Review;
import com.anitrack.domain.review.repo.ReviewRepo;
import com.anitrack.domain.watchlist.enums.WatchStatus;
import com.anitrack.domain.watchlist.model.WatchlistItem;
import com.anitrack.domain.watchlist.repo.WatchlistRepo;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ReviewDomainService {

    private final ReviewRepo reviewRepo;
    private final WatchlistRepo watchlistRepo;

    public Review addReview(Long userId, Long animeId, Integer score, String content) {
        WatchlistItem item = watchlistRepo.getByUserAndAnime(userId, animeId);
        if (item == null || item.getStatus() != WatchStatus.WATCHED) {
            throw new ReviewNotAllowedException(userId, animeId);
        }
        if (reviewRepo.getByUserAndAnime(userId, animeId) != null) {
            throw new ReviewAlreadyExistsException(userId, animeId);
        }
        Review review = Review.create(userId, animeId, score, content);
        return reviewRepo.add(review);
    }
}
```

`ReviewDomainService` 不带任何 Spring 注解（领域层不依赖 Spring 框架类型），其 Bean 注册留到 Task 5（`anitrack-application` 层的 `DomainServiceConfig`）补充完成——本任务的单测直接通过 Mockito `@InjectMocks` 构造，不依赖 Spring 容器。

- [ ] **Step 6: 运行测试，确认通过**

Run: `mvn -q -pl anitrack-domain -am test -Dtest=ReviewDomainServiceTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS（4 个测试通过）

- [ ] **Step 7: Commit**

```bash
git add anitrack-domain/src/main/java/com/anitrack/domain/review/exception anitrack-domain/src/main/java/com/anitrack/domain/review/repo anitrack-domain/src/main/java/com/anitrack/domain/review/service anitrack-domain/src/test/java/com/anitrack/domain/review/service
git commit -m "feat: 新增ReviewRepo接口与ReviewDomainService跨上下文编排"
```

---

### Task 3: t_review 表 + ReviewPO + ReviewMapper + UserMapper.selectByIds

**Files:**
- Create: `anitrack-starter/src/main/resources/db/migration/V4__create_review_table.sql`
- Create: `anitrack-infrastructure/src/main/java/com/anitrack/infra/dal/po/ReviewPO.java`
- Create: `anitrack-infrastructure/src/main/java/com/anitrack/infra/dal/mapper/ReviewMapper.java`
- Create: `anitrack-infrastructure/src/main/resources/mapper/ReviewMapper.xml`
- Modify: `anitrack-infrastructure/src/main/java/com/anitrack/infra/dal/mapper/UserMapper.java`
- Modify: `anitrack-infrastructure/src/main/resources/mapper/UserMapper.xml`

**Interfaces:**
- Consumes：无（本任务不依赖 Task 1/2 的 Java 类型）
- Produces：`ReviewMapper.selectByUserAndAnime(Long, Long)`／`selectByAnime(Long, int, int)`／`countByAnime(Long)`／`selectByUserId(Long)`／`insert(ReviewPO)`／`updateByUserAndAnime(ReviewPO)`，供 Task 4 的 `ReviewRepoImpl` 调用；`UserMapper.selectByIds(List<Long>)`，供 Task 4 的 `UserRepoImpl.listByIds` 调用

- [ ] **Step 1: 编写 Flyway 建表脚本**

```sql
CREATE TABLE t_review (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    anime_id BIGINT NOT NULL COMMENT '番剧ID',
    score TINYINT NOT NULL COMMENT '评分，1-10',
    content TEXT COMMENT '评论内容，可为空',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_anime (user_id, anime_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评价表';
```

- [ ] **Step 2: 编写 ReviewPO**

```java
package com.anitrack.infra.dal.po;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReviewPO {

    private Long id;
    private Long userId;
    private Long animeId;
    private Integer score;
    private String content;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

- [ ] **Step 3: 编写 ReviewMapper 接口**

```java
package com.anitrack.infra.dal.mapper;

import com.anitrack.infra.dal.po.ReviewPO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ReviewMapper {

    ReviewPO selectByUserAndAnime(@Param("userId") Long userId, @Param("animeId") Long animeId);

    List<ReviewPO> selectByAnime(@Param("animeId") Long animeId, @Param("offset") int offset, @Param("limit") int limit);

    long countByAnime(@Param("animeId") Long animeId);

    List<ReviewPO> selectByUserId(@Param("userId") Long userId);

    int insert(ReviewPO po);

    int updateByUserAndAnime(ReviewPO po);
}
```

- [ ] **Step 4: 编写 ReviewMapper.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.anitrack.infra.dal.mapper.ReviewMapper">

    <resultMap id="BaseResultMap" type="com.anitrack.infra.dal.po.ReviewPO">
        <id column="id" property="id"/>
        <result column="user_id" property="userId"/>
        <result column="anime_id" property="animeId"/>
        <result column="score" property="score"/>
        <result column="content" property="content"/>
        <result column="create_time" property="createTime"/>
        <result column="update_time" property="updateTime"/>
    </resultMap>

    <sql id="Base_Column_List">
        id, user_id, anime_id, score, content, create_time, update_time
    </sql>

    <select id="selectByUserAndAnime" resultMap="BaseResultMap">
        SELECT
        <include refid="Base_Column_List"/>
        FROM t_review
        WHERE user_id = #{userId} AND anime_id = #{animeId}
    </select>

    <select id="selectByAnime" resultMap="BaseResultMap">
        SELECT
        <include refid="Base_Column_List"/>
        FROM t_review
        WHERE anime_id = #{animeId}
        ORDER BY create_time DESC
        LIMIT #{offset}, #{limit}
    </select>

    <select id="countByAnime" resultType="long">
        SELECT COUNT(1)
        FROM t_review
        WHERE anime_id = #{animeId}
    </select>

    <select id="selectByUserId" resultMap="BaseResultMap">
        SELECT
        <include refid="Base_Column_List"/>
        FROM t_review
        WHERE user_id = #{userId}
        ORDER BY create_time DESC
    </select>

    <insert id="insert" parameterType="com.anitrack.infra.dal.po.ReviewPO" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO t_review (user_id, anime_id, score, content)
        VALUES (#{userId}, #{animeId}, #{score}, #{content})
    </insert>

    <update id="updateByUserAndAnime" parameterType="com.anitrack.infra.dal.po.ReviewPO">
        UPDATE t_review
        SET score = #{score},
            content = #{content}
        WHERE user_id = #{userId} AND anime_id = #{animeId}
    </update>

</mapper>
```

- [ ] **Step 5: 给 UserMapper 新增 selectByIds**

修改 `anitrack-infrastructure/src/main/java/com/anitrack/infra/dal/mapper/UserMapper.java`：

```java
package com.anitrack.infra.dal.mapper;

import com.anitrack.infra.dal.po.UserPO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserMapper {

    int insert(UserPO po);

    UserPO selectByUsername(@Param("username") String username);

    boolean existsByUsername(@Param("username") String username);

    List<UserPO> selectByIds(@Param("ids") List<Long> ids);
}
```

- [ ] **Step 6: 给 UserMapper.xml 新增 selectByIds**

在现有 `UserMapper.xml` 的 `<select id="existsByUsername">` 之后、`</mapper>` 之前插入：

```xml
    <select id="selectByIds" resultMap="BaseResultMap">
        SELECT
        <include refid="Base_Column_List"/>
        FROM t_user
        WHERE id IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">
            #{id}
        </foreach>
    </select>
```

- [ ] **Step 7: 验证模块编译通过**

Run: `mvn -q -pl anitrack-infrastructure -am compile`
Expected: `BUILD SUCCESS`（此阶段无法验证 SQL/XML 与真实数据库的一致性，需等 Task 7 端到端联调时用真实 MySQL 验证）

- [ ] **Step 8: Commit**

```bash
git add anitrack-starter/src/main/resources/db/migration/V4__create_review_table.sql anitrack-infrastructure/src/main/java/com/anitrack/infra/dal/po/ReviewPO.java anitrack-infrastructure/src/main/java/com/anitrack/infra/dal/mapper/ReviewMapper.java anitrack-infrastructure/src/main/resources/mapper/ReviewMapper.xml anitrack-infrastructure/src/main/java/com/anitrack/infra/dal/mapper/UserMapper.java anitrack-infrastructure/src/main/resources/mapper/UserMapper.xml
git commit -m "feat: 新增t_review表结构与ReviewPO/Mapper，UserMapper补充selectByIds"
```

---

### Task 4: ReviewConverter + ReviewRepoImpl + UserRepo.listByIds 实现

**Files:**
- Modify: `anitrack-domain/src/main/java/com/anitrack/domain/user/repo/UserRepo.java`
- Modify: `anitrack-infrastructure/src/main/java/com/anitrack/infra/repo/UserRepoImpl.java`
- Test: `anitrack-infrastructure/src/test/java/com/anitrack/infra/repo/UserRepoImplTest.java`
- Create: `anitrack-infrastructure/src/main/java/com/anitrack/infra/converter/ReviewConverter.java`
- Create: `anitrack-infrastructure/src/main/java/com/anitrack/infra/repo/ReviewRepoImpl.java`
- Test: `anitrack-infrastructure/src/test/java/com/anitrack/infra/repo/ReviewRepoImplTest.java`

**Interfaces:**
- Consumes：`Review`/`ReviewRepo`（Task 2）、`ReviewPO`/`ReviewMapper`/`UserMapper.selectByIds`（Task 3）、`User`/`UserRepo`（已存在）
- Produces：`ReviewRepoImpl implements ReviewRepo`（`@Repository`）；`UserRepo.listByIds(List<Long>): List<User>`，供 Task 5 的 `ReviewApplication` 使用

- [ ] **Step 1: 给 UserRepo 接口新增 listByIds**

```java
package com.anitrack.domain.user.repo;

import com.anitrack.domain.user.model.User;

import java.util.List;

public interface UserRepo {

    User getByUsername(String username);

    User save(User user);

    boolean existsByUsername(String username);

    List<User> listByIds(List<Long> ids);
}
```

- [ ] **Step 2: 编写失败的 UserRepoImplTest（新文件，仅覆盖新增的 listByIds）**

```java
package com.anitrack.infra.repo;

import com.anitrack.domain.user.enums.UserRole;
import com.anitrack.domain.user.model.User;
import com.anitrack.infra.converter.UserConverter;
import com.anitrack.infra.dal.mapper.UserMapper;
import com.anitrack.infra.dal.po.UserPO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRepoImplTest {

    @Mock
    private UserMapper mockUserMapper;

    @Mock
    private UserConverter mockUserConverter;

    @InjectMocks
    private UserRepoImpl sut;

    @Test
    void listByIds_whenIdsProvided_shouldReturnConvertedList() {
        // given
        UserPO po = new UserPO();
        User user = User.reconstitute(1L, "bob", "hash", "Bob", null, UserRole.USER);
        when(mockUserMapper.selectByIds(List.of(1L))).thenReturn(List.of(po));
        when(mockUserConverter.toDomain(po)).thenReturn(user);

        // when
        List<User> result = sut.listByIds(List.of(1L));

        // then
        assertThat(result).containsExactly(user);
    }

    @Test
    void listByIds_whenIdsEmpty_shouldReturnEmptyListWithoutQuerying() {
        // when
        List<User> result = sut.listByIds(List.of());

        // then
        assertThat(result).isEmpty();
        verify(mockUserMapper, never()).selectByIds(any());
    }
}
```

- [ ] **Step 3: 运行测试，确认失败**

Run: `mvn -q -pl anitrack-infrastructure -am test -Dtest=UserRepoImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL（`UserRepoImpl` 未实现 `listByIds`，编译错误）

- [ ] **Step 4: 给 UserRepoImpl 新增 listByIds 实现**

```java
package com.anitrack.infra.repo;

import com.anitrack.domain.user.model.User;
import com.anitrack.domain.user.repo.UserRepo;
import com.anitrack.infra.converter.UserConverter;
import com.anitrack.infra.dal.mapper.UserMapper;
import com.anitrack.infra.dal.po.UserPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class UserRepoImpl implements UserRepo {

    private final UserMapper userMapper;
    private final UserConverter userConverter;

    @Override
    public User getByUsername(String username) {
        UserPO po = userMapper.selectByUsername(username);
        return po == null ? null : userConverter.toDomain(po);
    }

    @Override
    public User save(User user) {
        UserPO po = userConverter.toPO(user);
        userMapper.insert(po);
        return userConverter.toDomain(po);
    }

    @Override
    public boolean existsByUsername(String username) {
        return userMapper.existsByUsername(username);
    }

    @Override
    public List<User> listByIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return userMapper.selectByIds(ids).stream()
            .map(userConverter::toDomain)
            .toList();
    }
}
```

- [ ] **Step 5: 运行测试，确认通过**

Run: `mvn -q -pl anitrack-infrastructure -am test -Dtest=UserRepoImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS（2 个测试通过）

- [ ] **Step 6: 编写 ReviewConverter**

```java
package com.anitrack.infra.converter;

import com.anitrack.domain.review.model.Review;
import com.anitrack.infra.dal.po.ReviewPO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ReviewConverter {

    ReviewPO toPO(Review review);

    default Review toDomain(ReviewPO po) {
        if (po == null) {
            return null;
        }
        return Review.reconstitute(po.getId(), po.getUserId(), po.getAnimeId(),
            po.getScore(), po.getContent(), po.getCreateTime());
    }
}
```

`toPO` 由 MapStruct 自动生成：`id/userId/animeId/score/content` 字段名直接对应；`toDomain` 因 `Review` 构造器私有，手写 `default` 方法调用 `reconstitute(...)`（与 `WatchlistItemConverter` 的既有模式一致）。

- [ ] **Step 7: 编写失败的 ReviewRepoImplTest**

```java
package com.anitrack.infra.repo;

import com.anitrack.domain.review.model.Review;
import com.anitrack.infra.converter.ReviewConverter;
import com.anitrack.infra.dal.mapper.ReviewMapper;
import com.anitrack.infra.dal.po.ReviewPO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewRepoImplTest {

    @Mock
    private ReviewMapper mockReviewMapper;

    @Mock
    private ReviewConverter mockReviewConverter;

    @InjectMocks
    private ReviewRepoImpl sut;

    @Test
    void getByUserAndAnime_whenNotFound_shouldReturnNull() {
        // given
        when(mockReviewMapper.selectByUserAndAnime(1L, 100L)).thenReturn(null);

        // when
        Review result = sut.getByUserAndAnime(1L, 100L);

        // then
        assertThat(result).isNull();
    }

    @Test
    void add_whenCalled_shouldInsertAndReturnConvertedReview() {
        // given
        Review review = Review.create(1L, 100L, 8, "很好看");
        ReviewPO po = new ReviewPO();
        Review persisted = Review.reconstitute(20L, 1L, 100L, 8, "很好看", null);
        when(mockReviewConverter.toPO(review)).thenReturn(po);
        when(mockReviewConverter.toDomain(po)).thenReturn(persisted);

        // when
        Review result = sut.add(review);

        // then
        verify(mockReviewMapper, times(1)).insert(po);
        assertThat(result).isEqualTo(persisted);
    }

    @Test
    void update_whenCalled_shouldUpdateByUserAndAnime() {
        // given
        Review review = Review.reconstitute(20L, 1L, 100L, 5, "重新打分", null);
        ReviewPO po = new ReviewPO();
        when(mockReviewConverter.toPO(review)).thenReturn(po);

        // when
        sut.update(review);

        // then
        verify(mockReviewMapper, times(1)).updateByUserAndAnime(po);
    }

    @Test
    void listByAnime_whenCalled_shouldQueryWithOffsetAndLimit() {
        // given
        ReviewPO po = new ReviewPO();
        Review converted = Review.reconstitute(20L, 1L, 100L, 8, "很好看", null);
        when(mockReviewMapper.selectByAnime(100L, 0, 10)).thenReturn(List.of(po));
        when(mockReviewConverter.toDomain(po)).thenReturn(converted);

        // when
        List<Review> result = sut.listByAnime(100L, 0, 10);

        // then
        assertThat(result).containsExactly(converted);
    }

    @Test
    void countByAnime_whenCalled_shouldReturnCount() {
        // given
        when(mockReviewMapper.countByAnime(100L)).thenReturn(3L);

        // when
        long result = sut.countByAnime(100L);

        // then
        assertThat(result).isEqualTo(3L);
    }

    @Test
    void listByUser_whenCalled_shouldReturnConvertedList() {
        // given
        ReviewPO po = new ReviewPO();
        Review converted = Review.reconstitute(20L, 1L, 100L, 8, "很好看", null);
        when(mockReviewMapper.selectByUserId(1L)).thenReturn(List.of(po));
        when(mockReviewConverter.toDomain(po)).thenReturn(converted);

        // when
        List<Review> result = sut.listByUser(1L);

        // then
        assertThat(result).containsExactly(converted);
    }
}
```

- [ ] **Step 8: 运行测试，确认失败**

Run: `mvn -q -pl anitrack-infrastructure -am test -Dtest=ReviewRepoImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL（`ReviewRepoImpl` 类不存在，编译错误）

- [ ] **Step 9: 编写 ReviewRepoImpl**

```java
package com.anitrack.infra.repo;

import com.anitrack.domain.review.model.Review;
import com.anitrack.domain.review.repo.ReviewRepo;
import com.anitrack.infra.converter.ReviewConverter;
import com.anitrack.infra.dal.mapper.ReviewMapper;
import com.anitrack.infra.dal.po.ReviewPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ReviewRepoImpl implements ReviewRepo {

    private final ReviewMapper reviewMapper;
    private final ReviewConverter reviewConverter;

    @Override
    public Review getByUserAndAnime(Long userId, Long animeId) {
        ReviewPO po = reviewMapper.selectByUserAndAnime(userId, animeId);
        return po == null ? null : reviewConverter.toDomain(po);
    }

    @Override
    public List<Review> listByAnime(Long animeId, int offset, int limit) {
        return reviewMapper.selectByAnime(animeId, offset, limit).stream()
            .map(reviewConverter::toDomain)
            .toList();
    }

    @Override
    public long countByAnime(Long animeId) {
        return reviewMapper.countByAnime(animeId);
    }

    @Override
    public List<Review> listByUser(Long userId) {
        return reviewMapper.selectByUserId(userId).stream()
            .map(reviewConverter::toDomain)
            .toList();
    }

    @Override
    public Review add(Review review) {
        ReviewPO po = reviewConverter.toPO(review);
        reviewMapper.insert(po);
        return reviewConverter.toDomain(po);
    }

    @Override
    public void update(Review review) {
        reviewMapper.updateByUserAndAnime(reviewConverter.toPO(review));
    }
}
```

- [ ] **Step 10: 运行测试，确认通过**

Run: `mvn -q -pl anitrack-infrastructure -am test -Dtest=ReviewRepoImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS（6 个测试通过），且 `target/generated-sources/annotations` 下生成 `ReviewConverterImpl`

- [ ] **Step 11: Commit**

```bash
git add anitrack-domain/src/main/java/com/anitrack/domain/user/repo/UserRepo.java anitrack-infrastructure/src/main/java/com/anitrack/infra/repo/UserRepoImpl.java anitrack-infrastructure/src/test/java/com/anitrack/infra/repo/UserRepoImplTest.java anitrack-infrastructure/src/main/java/com/anitrack/infra/converter/ReviewConverter.java anitrack-infrastructure/src/main/java/com/anitrack/infra/repo/ReviewRepoImpl.java anitrack-infrastructure/src/test/java/com/anitrack/infra/repo/ReviewRepoImplTest.java
git commit -m "feat: 新增ReviewConverter/ReviewRepoImpl，UserRepo补充listByIds批量查询"
```

---

### Task 5: AppExceptionEnum 扩展 + BO 模型 + ReviewAssembler + ReviewApplication

**Files:**
- Modify: `anitrack-application/src/main/java/com/anitrack/application/exception/AppExceptionEnum.java`
- Create: `anitrack-application/src/main/java/com/anitrack/application/model/ReviewBO.java`
- Create: `anitrack-application/src/main/java/com/anitrack/application/model/ReviewWithUserViewBO.java`
- Create: `anitrack-application/src/main/java/com/anitrack/application/model/ReviewWithAnimeViewBO.java`
- Create: `anitrack-application/src/main/java/com/anitrack/application/model/ReviewPageBO.java`
- Create: `anitrack-application/src/main/java/com/anitrack/application/assembler/ReviewAssembler.java`
- Modify: `anitrack-application/src/main/java/com/anitrack/application/config/DomainServiceConfig.java`
- Create: `anitrack-application/src/main/java/com/anitrack/application/service/ReviewApplication.java`
- Test: `anitrack-application/src/test/java/com/anitrack/application/service/ReviewApplicationTest.java`

**Interfaces:**
- Consumes：`Review`/`ReviewRepo`/`ReviewDomainService`/相关领域异常（Task 1-2）、`ReviewConverter` 无需直接依赖、`User`/`UserRepo`（已存在，含 Task 4 新增的 `listByIds`）、`Anime`/`AnimeRepo`（已存在，含 `listByIds`）、`AnitrackAppException`（已存在）
- Produces：`ReviewApplication.addReview(Long, Long, Integer, String): ReviewBO`／`updateReview(Long, Long, Integer, String): ReviewBO`／`getMyReview(Long, Long): ReviewBO`／`listByAnime(Long, int, int): ReviewPageBO<ReviewWithUserViewBO>`／`listMyReviews(Long): List<ReviewWithAnimeViewBO>`，供 Task 6 的 `ReviewController` 调用

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
    ILLEGAL_WATCH_PROGRESS(40204, "追番进度更新不合法"),
    REVIEW_NOT_ALLOWED(40301, "只有看完的番剧才能评价"),
    REVIEW_ALREADY_EXISTS(40302, "该番剧已评价，请使用修改接口"),
    REVIEW_NOT_FOUND(40303, "评价记录不存在"),
    ILLEGAL_REVIEW_SCORE(40304, "评分必须在1-10之间");

    private final int code;
    private final String message;
}
```

- [ ] **Step 2: 编写 ReviewBO / ReviewWithUserViewBO / ReviewWithAnimeViewBO / ReviewPageBO**

```java
package com.anitrack.application.model;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ReviewBO {

    private Long id;
    private Long animeId;
    private Integer score;
    private String content;
    private LocalDateTime createTime;
}
```

```java
package com.anitrack.application.model;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ReviewWithUserViewBO {

    private Long id;
    private Long userId;
    private String userNickname;
    private String userAvatarUrl;
    private Integer score;
    private String content;
    private LocalDateTime createTime;
}
```

```java
package com.anitrack.application.model;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ReviewWithAnimeViewBO {

    private Long id;
    private Long animeId;
    private String animeTitleCn;
    private String animeTitleOriginal;
    private String animeCoverUrl;
    private Integer score;
    private String content;
    private LocalDateTime createTime;
}
```

```java
package com.anitrack.application.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ReviewPageBO<T> {

    private List<T> list;
    private long total;
    private int page;
    private int pageSize;
}
```

- [ ] **Step 3: 编写 ReviewAssembler**

```java
package com.anitrack.application.assembler;

import com.anitrack.application.model.ReviewWithAnimeViewBO;
import com.anitrack.application.model.ReviewWithUserViewBO;
import com.anitrack.domain.anime.model.Anime;
import com.anitrack.domain.review.model.Review;
import com.anitrack.domain.user.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ReviewAssembler {

    public List<ReviewWithUserViewBO> assembleWithUser(List<Review> reviews, List<User> users) {
        Map<Long, User> userById = users.stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));
        return reviews.stream()
            .map(review -> toUserViewBO(review, userById.get(review.getUserId())))
            .filter(Objects::nonNull)
            .toList();
    }

    public List<ReviewWithAnimeViewBO> assembleWithAnime(List<Review> reviews, List<Anime> animes) {
        Map<Long, Anime> animeById = animes.stream()
            .collect(Collectors.toMap(Anime::getId, Function.identity()));
        return reviews.stream()
            .map(review -> toAnimeViewBO(review, animeById.get(review.getAnimeId())))
            .filter(Objects::nonNull)
            .toList();
    }

    private ReviewWithUserViewBO toUserViewBO(Review review, User user) {
        if (user == null) {
            log.warn("评价关联的用户不存在, userId={}", review.getUserId());
            return null;
        }
        return ReviewWithUserViewBO.builder()
            .id(review.getId())
            .userId(review.getUserId())
            .userNickname(user.getNickname())
            .userAvatarUrl(user.getAvatarUrl())
            .score(review.getScore())
            .content(review.getContent())
            .createTime(review.getCreateTime())
            .build();
    }

    private ReviewWithAnimeViewBO toAnimeViewBO(Review review, Anime anime) {
        if (anime == null) {
            log.warn("评价关联的番剧不存在, animeId={}", review.getAnimeId());
            return null;
        }
        return ReviewWithAnimeViewBO.builder()
            .id(review.getId())
            .animeId(review.getAnimeId())
            .animeTitleCn(anime.getTitleCn())
            .animeTitleOriginal(anime.getTitleOriginal())
            .animeCoverUrl(anime.getCoverUrl())
            .score(review.getScore())
            .content(review.getContent())
            .createTime(review.getCreateTime())
            .build();
    }
}
```

- [ ] **Step 4: 给 DomainServiceConfig 新增 reviewDomainService bean**

```java
package com.anitrack.application.config;

import com.anitrack.domain.anime.repo.AnimeRepo;
import com.anitrack.domain.review.repo.ReviewRepo;
import com.anitrack.domain.review.service.ReviewDomainService;
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

    @Bean
    public ReviewDomainService reviewDomainService(ReviewRepo reviewRepo, WatchlistRepo watchlistRepo) {
        return new ReviewDomainService(reviewRepo, watchlistRepo);
    }
}
```

- [ ] **Step 5: 编写失败的 ReviewApplicationTest**

```java
package com.anitrack.application.service;

import com.anitrack.application.assembler.ReviewAssembler;
import com.anitrack.application.exception.AnitrackAppException;
import com.anitrack.application.model.ReviewBO;
import com.anitrack.application.model.ReviewPageBO;
import com.anitrack.application.model.ReviewWithAnimeViewBO;
import com.anitrack.application.model.ReviewWithUserViewBO;
import com.anitrack.domain.anime.model.Anime;
import com.anitrack.domain.anime.repo.AnimeRepo;
import com.anitrack.domain.review.exception.IllegalReviewScoreException;
import com.anitrack.domain.review.exception.ReviewAlreadyExistsException;
import com.anitrack.domain.review.exception.ReviewNotAllowedException;
import com.anitrack.domain.review.model.Review;
import com.anitrack.domain.review.repo.ReviewRepo;
import com.anitrack.domain.review.service.ReviewDomainService;
import com.anitrack.domain.user.enums.UserRole;
import com.anitrack.domain.user.model.User;
import com.anitrack.domain.user.repo.UserRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewApplicationTest {

    @Mock
    private ReviewDomainService mockReviewDomainService;

    @Mock
    private ReviewRepo mockReviewRepo;

    @Mock
    private UserRepo mockUserRepo;

    @Mock
    private AnimeRepo mockAnimeRepo;

    @Mock
    private ReviewAssembler mockReviewAssembler;

    @InjectMocks
    private ReviewApplication sut;

    @Test
    void addReview_whenSucceeds_shouldReturnBO() {
        // given
        Review review = Review.reconstitute(20L, 1L, 100L, 8, "很好看", null);
        when(mockReviewDomainService.addReview(1L, 100L, 8, "很好看")).thenReturn(review);

        // when
        ReviewBO result = sut.addReview(1L, 100L, 8, "很好看");

        // then
        assertThat(result.getId()).isEqualTo(20L);
        assertThat(result.getScore()).isEqualTo(8);
    }

    @Test
    void addReview_whenNotAllowed_shouldThrowAppException() {
        // given
        when(mockReviewDomainService.addReview(1L, 100L, 8, "很好看"))
            .thenThrow(new ReviewNotAllowedException(1L, 100L));

        // when & then
        assertThatThrownBy(() -> sut.addReview(1L, 100L, 8, "很好看"))
            .isInstanceOf(AnitrackAppException.class)
            .hasMessageContaining("只有看完的番剧才能评价");
    }

    @Test
    void addReview_whenAlreadyExists_shouldThrowAppException() {
        // given
        when(mockReviewDomainService.addReview(1L, 100L, 8, "很好看"))
            .thenThrow(new ReviewAlreadyExistsException(1L, 100L));

        // when & then
        assertThatThrownBy(() -> sut.addReview(1L, 100L, 8, "很好看"))
            .isInstanceOf(AnitrackAppException.class)
            .hasMessageContaining("该番剧已评价");
    }

    @Test
    void addReview_whenScoreIllegal_shouldThrowAppException() {
        // given
        when(mockReviewDomainService.addReview(1L, 100L, 11, "很好看"))
            .thenThrow(new IllegalReviewScoreException("评分必须在1-10之间"));

        // when & then
        assertThatThrownBy(() -> sut.addReview(1L, 100L, 11, "很好看"))
            .isInstanceOf(AnitrackAppException.class)
            .hasMessageContaining("评分必须在1-10之间");
    }

    @Test
    void updateReview_whenExists_shouldUpdateAndReturnBO() {
        // given
        Review review = Review.reconstitute(20L, 1L, 100L, 8, "很好看", null);
        when(mockReviewRepo.getByUserAndAnime(1L, 100L)).thenReturn(review);

        // when
        ReviewBO result = sut.updateReview(1L, 100L, 5, "重新打分");

        // then
        assertThat(result.getScore()).isEqualTo(5);
        assertThat(result.getContent()).isEqualTo("重新打分");
    }

    @Test
    void updateReview_whenNotFound_shouldThrowAppException() {
        // given
        when(mockReviewRepo.getByUserAndAnime(1L, 100L)).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> sut.updateReview(1L, 100L, 5, "重新打分"))
            .isInstanceOf(AnitrackAppException.class)
            .hasMessageContaining("评价记录不存在");
    }

    @Test
    void updateReview_whenScoreIllegal_shouldThrowAppException() {
        // given
        Review review = Review.reconstitute(20L, 1L, 100L, 8, "很好看", null);
        when(mockReviewRepo.getByUserAndAnime(1L, 100L)).thenReturn(review);

        // when & then
        assertThatThrownBy(() -> sut.updateReview(1L, 100L, 11, "重新打分"))
            .isInstanceOf(AnitrackAppException.class)
            .hasMessageContaining("评分必须在1-10之间");
    }

    @Test
    void getMyReview_whenExists_shouldReturnBO() {
        // given
        Review review = Review.reconstitute(20L, 1L, 100L, 8, "很好看", null);
        when(mockReviewRepo.getByUserAndAnime(1L, 100L)).thenReturn(review);

        // when
        ReviewBO result = sut.getMyReview(1L, 100L);

        // then
        assertThat(result.getId()).isEqualTo(20L);
    }

    @Test
    void getMyReview_whenNotFound_shouldThrowAppException() {
        // given
        when(mockReviewRepo.getByUserAndAnime(1L, 100L)).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> sut.getMyReview(1L, 100L))
            .isInstanceOf(AnitrackAppException.class)
            .hasMessageContaining("评价记录不存在");
    }

    @Test
    void listByAnime_whenCalled_shouldFetchUsersAndAssembleWithPaging() {
        // given
        Review review = Review.reconstitute(20L, 1L, 100L, 8, "很好看", null);
        User user = User.reconstitute(1L, "bob", "hash", "Bob", null, UserRole.USER);
        ReviewWithUserViewBO viewBO = ReviewWithUserViewBO.builder().id(20L).userId(1L).build();
        when(mockReviewRepo.listByAnime(100L, 0, 10)).thenReturn(List.of(review));
        when(mockReviewRepo.countByAnime(100L)).thenReturn(1L);
        when(mockUserRepo.listByIds(List.of(1L))).thenReturn(List.of(user));
        when(mockReviewAssembler.assembleWithUser(List.of(review), List.of(user))).thenReturn(List.of(viewBO));

        // when
        ReviewPageBO<ReviewWithUserViewBO> result = sut.listByAnime(100L, 1, 10);

        // then
        assertThat(result.getList()).containsExactly(viewBO);
        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getPageSize()).isEqualTo(10);
    }

    @Test
    void listByAnime_whenPageIsTwo_shouldComputeOffset() {
        // given
        when(mockReviewRepo.listByAnime(100L, 10, 10)).thenReturn(List.of());
        when(mockReviewRepo.countByAnime(100L)).thenReturn(0L);
        when(mockUserRepo.listByIds(List.of())).thenReturn(List.of());
        when(mockReviewAssembler.assembleWithUser(List.of(), List.of())).thenReturn(List.of());

        // when
        sut.listByAnime(100L, 2, 10);

        // then（验证 offset = (page-1)*pageSize = 10）
        org.mockito.Mockito.verify(mockReviewRepo, org.mockito.Mockito.times(1)).listByAnime(100L, 10, 10);
    }

    @Test
    void listMyReviews_whenCalled_shouldFetchAnimesAndAssemble() {
        // given
        Review review = Review.reconstitute(20L, 1L, 100L, 8, "很好看", null);
        Anime anime = Anime.reconstitute(100L, 999L, "中文名", "Original Title", "http://cover.jpg", 12,
            LocalDate.of(2024, 1, 1), "简介");
        ReviewWithAnimeViewBO viewBO = ReviewWithAnimeViewBO.builder().id(20L).animeId(100L).build();
        when(mockReviewRepo.listByUser(1L)).thenReturn(List.of(review));
        when(mockAnimeRepo.listByIds(List.of(100L))).thenReturn(List.of(anime));
        when(mockReviewAssembler.assembleWithAnime(List.of(review), List.of(anime))).thenReturn(List.of(viewBO));

        // when
        List<ReviewWithAnimeViewBO> result = sut.listMyReviews(1L);

        // then
        assertThat(result).containsExactly(viewBO);
    }
}
```

- [ ] **Step 6: 运行测试，确认失败**

Run: `mvn -q -pl anitrack-application -am test -Dtest=ReviewApplicationTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL（`ReviewApplication` 类不存在，编译错误）

- [ ] **Step 7: 编写 ReviewApplication**

```java
package com.anitrack.application.service;

import com.anitrack.application.assembler.ReviewAssembler;
import com.anitrack.application.exception.AnitrackAppException;
import com.anitrack.application.exception.AppExceptionEnum;
import com.anitrack.application.model.ReviewBO;
import com.anitrack.application.model.ReviewPageBO;
import com.anitrack.application.model.ReviewWithAnimeViewBO;
import com.anitrack.application.model.ReviewWithUserViewBO;
import com.anitrack.domain.anime.model.Anime;
import com.anitrack.domain.anime.repo.AnimeRepo;
import com.anitrack.domain.review.exception.IllegalReviewScoreException;
import com.anitrack.domain.review.exception.ReviewAlreadyExistsException;
import com.anitrack.domain.review.exception.ReviewNotAllowedException;
import com.anitrack.domain.review.model.Review;
import com.anitrack.domain.review.repo.ReviewRepo;
import com.anitrack.domain.review.service.ReviewDomainService;
import com.anitrack.domain.user.model.User;
import com.anitrack.domain.user.repo.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewApplication {

    private final ReviewDomainService reviewDomainService;
    private final ReviewRepo reviewRepo;
    private final UserRepo userRepo;
    private final AnimeRepo animeRepo;
    private final ReviewAssembler reviewAssembler;

    @Transactional
    public ReviewBO addReview(Long userId, Long animeId, Integer score, String content) {
        Review review;
        try {
            review = reviewDomainService.addReview(userId, animeId, score, content);
        } catch (ReviewNotAllowedException e) {
            throw new AnitrackAppException(AppExceptionEnum.REVIEW_NOT_ALLOWED);
        } catch (ReviewAlreadyExistsException e) {
            throw new AnitrackAppException(AppExceptionEnum.REVIEW_ALREADY_EXISTS);
        } catch (IllegalReviewScoreException e) {
            throw new AnitrackAppException(AppExceptionEnum.ILLEGAL_REVIEW_SCORE);
        }
        return toBO(review);
    }

    @Transactional
    public ReviewBO updateReview(Long userId, Long animeId, Integer score, String content) {
        Review review = reviewRepo.getByUserAndAnime(userId, animeId);
        if (review == null) {
            throw new AnitrackAppException(AppExceptionEnum.REVIEW_NOT_FOUND);
        }
        try {
            review.update(score, content);
        } catch (IllegalReviewScoreException e) {
            throw new AnitrackAppException(AppExceptionEnum.ILLEGAL_REVIEW_SCORE);
        }
        reviewRepo.update(review);
        return toBO(review);
    }

    public ReviewBO getMyReview(Long userId, Long animeId) {
        Review review = reviewRepo.getByUserAndAnime(userId, animeId);
        if (review == null) {
            throw new AnitrackAppException(AppExceptionEnum.REVIEW_NOT_FOUND);
        }
        return toBO(review);
    }

    public ReviewPageBO<ReviewWithUserViewBO> listByAnime(Long animeId, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<Review> reviews = reviewRepo.listByAnime(animeId, offset, pageSize);
        long total = reviewRepo.countByAnime(animeId);
        List<Long> userIds = reviews.stream().map(Review::getUserId).toList();
        List<User> users = userRepo.listByIds(userIds);
        List<ReviewWithUserViewBO> list = reviewAssembler.assembleWithUser(reviews, users);
        return ReviewPageBO.<ReviewWithUserViewBO>builder()
            .list(list)
            .total(total)
            .page(page)
            .pageSize(pageSize)
            .build();
    }

    public List<ReviewWithAnimeViewBO> listMyReviews(Long userId) {
        List<Review> reviews = reviewRepo.listByUser(userId);
        List<Long> animeIds = reviews.stream().map(Review::getAnimeId).toList();
        List<Anime> animes = animeRepo.listByIds(animeIds);
        return reviewAssembler.assembleWithAnime(reviews, animes);
    }

    private ReviewBO toBO(Review review) {
        return ReviewBO.builder()
            .id(review.getId())
            .animeId(review.getAnimeId())
            .score(review.getScore())
            .content(review.getContent())
            .createTime(review.getCreateTime())
            .build();
    }
}
```

- [ ] **Step 8: 运行测试，确认通过**

Run: `mvn -q -pl anitrack-application -am test -Dtest=ReviewApplicationTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS（12 个测试通过）

- [ ] **Step 9: Commit**

```bash
git add anitrack-application/src/main/java/com/anitrack/application/exception/AppExceptionEnum.java anitrack-application/src/main/java/com/anitrack/application/model/ReviewBO.java anitrack-application/src/main/java/com/anitrack/application/model/ReviewWithUserViewBO.java anitrack-application/src/main/java/com/anitrack/application/model/ReviewWithAnimeViewBO.java anitrack-application/src/main/java/com/anitrack/application/model/ReviewPageBO.java anitrack-application/src/main/java/com/anitrack/application/assembler/ReviewAssembler.java anitrack-application/src/main/java/com/anitrack/application/config/DomainServiceConfig.java anitrack-application/src/main/java/com/anitrack/application/service/ReviewApplication.java anitrack-application/src/test/java/com/anitrack/application/service/ReviewApplicationTest.java
git commit -m "feat: 新增ReviewApplication编排评价5个用例"
```

---

### Task 6: PageResponse + Req/Response 对象 + ReviewController（5个接口）+ Web 层集成测试

**Files:**
- Create: `anitrack-starter/src/main/java/com/anitrack/starter/response/PageResponse.java`
- Create: `anitrack-starter/src/main/java/com/anitrack/starter/request/ReviewAddReq.java`
- Create: `anitrack-starter/src/main/java/com/anitrack/starter/request/ReviewUpdateReq.java`
- Create: `anitrack-starter/src/main/java/com/anitrack/starter/request/ReviewDetailReq.java`
- Create: `anitrack-starter/src/main/java/com/anitrack/starter/request/ReviewListByAnimeReq.java`
- Create: `anitrack-starter/src/main/java/com/anitrack/starter/response/ReviewResponse.java`
- Create: `anitrack-starter/src/main/java/com/anitrack/starter/response/ReviewWithUserResponse.java`
- Create: `anitrack-starter/src/main/java/com/anitrack/starter/response/ReviewWithAnimeResponse.java`
- Modify: `anitrack-starter/src/main/java/com/anitrack/starter/converter/HttpConverter.java`
- Create: `anitrack-starter/src/main/java/com/anitrack/starter/controller/ReviewController.java`
- Test: `anitrack-starter/src/test/java/com/anitrack/starter/controller/ReviewControllerTest.java`

**Interfaces:**
- Consumes：`ReviewApplication`（Task 5）、`ResponseResult`/`UserContextHolder`（已存在）
- Produces：`POST /api/review/add`、`POST /api/review/update`、`POST /api/review/detail`、`POST /api/review/list_by_anime`、`POST /api/review/my_list`

- [ ] **Step 1: 编写 PageResponse 通用分页响应壳**

```java
package com.anitrack.starter.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PageResponse<T> {

    private Integer pageNum;
    private Integer pageSize;
    private Long total;
    private List<T> list;
}
```

- [ ] **Step 2: 编写请求对象**

```java
package com.anitrack.starter.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class ReviewAddReq {

    @NotNull(message = "番剧ID不能为空")
    private Long animeId;

    @NotNull(message = "评分不能为空")
    private Integer score;

    private String content;
}
```

```java
package com.anitrack.starter.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class ReviewUpdateReq {

    @NotNull(message = "番剧ID不能为空")
    private Long animeId;

    @NotNull(message = "评分不能为空")
    private Integer score;

    private String content;
}
```

```java
package com.anitrack.starter.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class ReviewDetailReq {

    @NotNull(message = "番剧ID不能为空")
    private Long animeId;
}
```

```java
package com.anitrack.starter.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class ReviewListByAnimeReq {

    @NotNull(message = "番剧ID不能为空")
    private Long animeId;

    private Integer page;

    private Integer pageSize;
}
```

- [ ] **Step 3: 编写响应对象**

```java
package com.anitrack.starter.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ReviewResponse {

    private Long id;
    private Long animeId;
    private Integer score;
    private String content;
    private LocalDateTime createTime;
}
```

```java
package com.anitrack.starter.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ReviewWithUserResponse {

    private Long id;
    private Long userId;
    private String userNickname;
    private String userAvatarUrl;
    private Integer score;
    private String content;
    private LocalDateTime createTime;
}
```

```java
package com.anitrack.starter.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ReviewWithAnimeResponse {

    private Long id;
    private Long animeId;
    private String animeTitleCn;
    private String animeTitleOriginal;
    private String animeCoverUrl;
    private Integer score;
    private String content;
    private LocalDateTime createTime;
}
```

- [ ] **Step 4: 给 HttpConverter 新增转换方法**

在现有 `HttpConverter` 类中新增以下方法（保留已有方法不变），并新增对应 import：`com.anitrack.application.model.ReviewBO`、`com.anitrack.application.model.ReviewPageBO`、`com.anitrack.application.model.ReviewWithAnimeViewBO`、`com.anitrack.application.model.ReviewWithUserViewBO`、`com.anitrack.starter.response.PageResponse`、`com.anitrack.starter.response.ReviewResponse`、`com.anitrack.starter.response.ReviewWithAnimeResponse`、`com.anitrack.starter.response.ReviewWithUserResponse`：

```java
public ReviewResponse reviewBO2Response(ReviewBO bo) {
    return ReviewResponse.builder()
        .id(bo.getId())
        .animeId(bo.getAnimeId())
        .score(bo.getScore())
        .content(bo.getContent())
        .createTime(bo.getCreateTime())
        .build();
}

public ReviewWithUserResponse reviewWithUserViewBO2Response(ReviewWithUserViewBO bo) {
    return ReviewWithUserResponse.builder()
        .id(bo.getId())
        .userId(bo.getUserId())
        .userNickname(bo.getUserNickname())
        .userAvatarUrl(bo.getUserAvatarUrl())
        .score(bo.getScore())
        .content(bo.getContent())
        .createTime(bo.getCreateTime())
        .build();
}

public ReviewWithAnimeResponse reviewWithAnimeViewBO2Response(ReviewWithAnimeViewBO bo) {
    return ReviewWithAnimeResponse.builder()
        .id(bo.getId())
        .animeId(bo.getAnimeId())
        .animeTitleCn(bo.getAnimeTitleCn())
        .animeTitleOriginal(bo.getAnimeTitleOriginal())
        .animeCoverUrl(bo.getAnimeCoverUrl())
        .score(bo.getScore())
        .content(bo.getContent())
        .createTime(bo.getCreateTime())
        .build();
}

public List<ReviewWithAnimeResponse> reviewWithAnimeViewBOList2Response(List<ReviewWithAnimeViewBO> boList) {
    return boList.stream().map(this::reviewWithAnimeViewBO2Response).toList();
}

public PageResponse<ReviewWithUserResponse> reviewPageBO2Response(ReviewPageBO<ReviewWithUserViewBO> pageBO) {
    List<ReviewWithUserResponse> list = pageBO.getList().stream()
        .map(this::reviewWithUserViewBO2Response)
        .toList();
    return PageResponse.<ReviewWithUserResponse>builder()
        .pageNum(pageBO.getPage())
        .pageSize(pageBO.getPageSize())
        .total(pageBO.getTotal())
        .list(list)
        .build();
}
```

- [ ] **Step 5: 编写失败的 ReviewControllerTest**

```java
package com.anitrack.starter.controller;

import com.anitrack.application.model.ReviewBO;
import com.anitrack.application.model.ReviewPageBO;
import com.anitrack.application.model.ReviewWithAnimeViewBO;
import com.anitrack.application.model.ReviewWithUserViewBO;
import com.anitrack.application.service.ReviewApplication;
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

@WebMvcTest(ReviewController.class)
class ReviewControllerTest {

    private static final String AUTH_HEADER_VALUE = "Bearer test-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReviewApplication mockReviewApplication;

    @MockBean
    private HttpConverter mockHttpConverter;

    @MockBean
    private JwtTokenProvider mockJwtTokenProvider;

    private void stubValidToken() {
        when(mockJwtTokenProvider.validateToken(anyString())).thenReturn(true);
        when(mockJwtTokenProvider.getUserId(anyString())).thenReturn(1L);
    }

    private ReviewBO createTestReviewBO() {
        return ReviewBO.builder().id(20L).animeId(100L).score(8).content("很好看").build();
    }

    @Test
    void add_whenNoAuthorizationHeader_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/review/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L, "score", 8))))
            .andExpect(status().isUnauthorized());

        verify(mockReviewApplication, never()).addReview(any(), any(), any(), any());
    }

    @Test
    void add_whenScoreMissing_shouldReturnBadRequest() throws Exception {
        stubValidToken();

        mockMvc.perform(post("/api/review/add")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void add_whenRequestIsValid_shouldReturnReview() throws Exception {
        // given
        stubValidToken();
        ReviewBO bo = createTestReviewBO();
        when(mockReviewApplication.addReview(1L, 100L, 8, "很好看")).thenReturn(bo);
        when(mockHttpConverter.reviewBO2Response(bo)).thenCallRealMethod();

        // when & then
        mockMvc.perform(post("/api/review/add")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L, "score", 8, "content", "很好看"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(1))
            .andExpect(jsonPath("$.data.score").value(8));

        verify(mockReviewApplication, times(1)).addReview(1L, 100L, 8, "很好看");
    }

    @Test
    void add_whenNotAllowed_shouldReturnBusinessError() throws Exception {
        // given
        stubValidToken();
        doThrow(new com.anitrack.application.exception.AnitrackAppException(
                com.anitrack.application.exception.AppExceptionEnum.REVIEW_NOT_ALLOWED))
            .when(mockReviewApplication).addReview(1L, 100L, 8, null);

        // when & then
        mockMvc.perform(post("/api/review/add")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L, "score", 8))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(0));
    }

    @Test
    void update_whenNoAuthorizationHeader_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/review/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L, "score", 5))))
            .andExpect(status().isUnauthorized());

        verify(mockReviewApplication, never()).updateReview(any(), any(), any(), any());
    }

    @Test
    void update_whenRequestIsValid_shouldReturnReview() throws Exception {
        // given
        stubValidToken();
        ReviewBO bo = ReviewBO.builder().id(20L).animeId(100L).score(5).content("重新打分").build();
        when(mockReviewApplication.updateReview(1L, 100L, 5, "重新打分")).thenReturn(bo);
        when(mockHttpConverter.reviewBO2Response(bo)).thenCallRealMethod();

        // when & then
        mockMvc.perform(post("/api/review/update")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L, "score", 5, "content", "重新打分"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(1))
            .andExpect(jsonPath("$.data.score").value(5));

        verify(mockReviewApplication, times(1)).updateReview(1L, 100L, 5, "重新打分");
    }

    @Test
    void update_whenNotFound_shouldReturnBusinessError() throws Exception {
        // given
        stubValidToken();
        doThrow(new com.anitrack.application.exception.AnitrackAppException(
                com.anitrack.application.exception.AppExceptionEnum.REVIEW_NOT_FOUND))
            .when(mockReviewApplication).updateReview(1L, 999L, 5, null);

        // when & then
        mockMvc.perform(post("/api/review/update")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 999L, "score", 5))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(0));
    }

    @Test
    void detail_whenNoAuthorizationHeader_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/review/detail")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L))))
            .andExpect(status().isUnauthorized());

        verify(mockReviewApplication, never()).getMyReview(any(), any());
    }

    @Test
    void detail_whenAnimeIdMissing_shouldReturnBadRequest() throws Exception {
        stubValidToken();

        mockMvc.perform(post("/api/review/detail")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void detail_whenRequestIsValid_shouldReturnReview() throws Exception {
        // given
        stubValidToken();
        ReviewBO bo = createTestReviewBO();
        when(mockReviewApplication.getMyReview(1L, 100L)).thenReturn(bo);
        when(mockHttpConverter.reviewBO2Response(bo)).thenCallRealMethod();

        // when & then
        mockMvc.perform(post("/api/review/detail")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(1))
            .andExpect(jsonPath("$.data.animeId").value(100L));

        verify(mockReviewApplication, times(1)).getMyReview(1L, 100L);
    }

    @Test
    void detail_whenNotFound_shouldReturnBusinessError() throws Exception {
        // given
        stubValidToken();
        doThrow(new com.anitrack.application.exception.AnitrackAppException(
                com.anitrack.application.exception.AppExceptionEnum.REVIEW_NOT_FOUND))
            .when(mockReviewApplication).getMyReview(1L, 999L);

        // when & then
        mockMvc.perform(post("/api/review/detail")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 999L))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(0));
    }

    @Test
    void listByAnime_whenNoAuthorizationHeader_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/review/list_by_anime")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L))))
            .andExpect(status().isUnauthorized());

        verify(mockReviewApplication, never()).listByAnime(any(), anyInt(), anyInt());
    }

    @Test
    void listByAnime_whenAnimeIdMissing_shouldReturnBadRequest() throws Exception {
        stubValidToken();

        mockMvc.perform(post("/api/review/list_by_anime")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void listByAnime_whenPageAndPageSizeOmitted_shouldUseDefaults() throws Exception {
        // given
        stubValidToken();
        ReviewWithUserViewBO viewBO = ReviewWithUserViewBO.builder().id(20L).userId(1L).userNickname("Bob").build();
        ReviewPageBO<ReviewWithUserViewBO> pageBO = ReviewPageBO.<ReviewWithUserViewBO>builder()
            .list(List.of(viewBO)).total(1L).page(1).pageSize(10).build();
        when(mockReviewApplication.listByAnime(100L, 1, 10)).thenReturn(pageBO);
        when(mockHttpConverter.reviewPageBO2Response(pageBO)).thenCallRealMethod();
        when(mockHttpConverter.reviewWithUserViewBO2Response(viewBO)).thenCallRealMethod();

        // when & then
        mockMvc.perform(post("/api/review/list_by_anime")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(1))
            .andExpect(jsonPath("$.data.pageNum").value(1))
            .andExpect(jsonPath("$.data.pageSize").value(10))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.list[0].userNickname").value("Bob"));

        verify(mockReviewApplication, times(1)).listByAnime(100L, 1, 10);
    }

    @Test
    void listByAnime_whenPageAndPageSizeProvided_shouldUseProvidedValues() throws Exception {
        // given
        stubValidToken();
        ReviewPageBO<ReviewWithUserViewBO> pageBO = ReviewPageBO.<ReviewWithUserViewBO>builder()
            .list(List.of()).total(0L).page(2).pageSize(5).build();
        when(mockReviewApplication.listByAnime(100L, 2, 5)).thenReturn(pageBO);
        when(mockHttpConverter.reviewPageBO2Response(pageBO)).thenCallRealMethod();

        // when & then
        mockMvc.perform(post("/api/review/list_by_anime")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 100L, "page", 2, "pageSize", 5))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(1));

        verify(mockReviewApplication, times(1)).listByAnime(100L, 2, 5);
    }

    @Test
    void myList_whenNoAuthorizationHeader_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/review/my_list")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());

        verify(mockReviewApplication, never()).listMyReviews(any());
    }

    @Test
    void myList_whenRequestIsValid_shouldReturnReviewWithAnimeList() throws Exception {
        // given
        stubValidToken();
        ReviewWithAnimeViewBO viewBO = ReviewWithAnimeViewBO.builder()
            .id(20L).animeId(100L).animeTitleCn("中文名").score(8).build();
        when(mockReviewApplication.listMyReviews(1L)).thenReturn(List.of(viewBO));
        when(mockHttpConverter.reviewWithAnimeViewBOList2Response(List.of(viewBO))).thenCallRealMethod();
        when(mockHttpConverter.reviewWithAnimeViewBO2Response(viewBO)).thenCallRealMethod();

        // when & then
        mockMvc.perform(post("/api/review/my_list")
                .header("Authorization", AUTH_HEADER_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(1))
            .andExpect(jsonPath("$.data[0].animeTitleCn").value("中文名"));

        verify(mockReviewApplication, times(1)).listMyReviews(1L);
    }
}
```

- [ ] **Step 6: 运行测试，确认失败**

Run: `mvn -q -pl anitrack-starter -am test -Dtest=ReviewControllerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL（`ReviewController` 类不存在，编译错误）

- [ ] **Step 7: 编写 ReviewController**

```java
package com.anitrack.starter.controller;

import com.anitrack.application.model.ReviewBO;
import com.anitrack.application.model.ReviewPageBO;
import com.anitrack.application.model.ReviewWithAnimeViewBO;
import com.anitrack.application.model.ReviewWithUserViewBO;
import com.anitrack.application.service.ReviewApplication;
import com.anitrack.common.utils.UserContextHolder;
import com.anitrack.starter.converter.HttpConverter;
import com.anitrack.starter.request.ReviewAddReq;
import com.anitrack.starter.request.ReviewDetailReq;
import com.anitrack.starter.request.ReviewListByAnimeReq;
import com.anitrack.starter.request.ReviewUpdateReq;
import com.anitrack.starter.response.PageResponse;
import com.anitrack.starter.response.ResponseResult;
import com.anitrack.starter.response.ReviewResponse;
import com.anitrack.starter.response.ReviewWithAnimeResponse;
import com.anitrack.starter.response.ReviewWithUserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/review")
@RequiredArgsConstructor
public class ReviewController {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;

    private final ReviewApplication reviewApplication;
    private final HttpConverter httpConverter;

    @PostMapping("/add")
    public ResponseResult<ReviewResponse> add(@Valid @RequestBody ReviewAddReq req) {
        ReviewBO result = reviewApplication.addReview(
            UserContextHolder.getUserId(), req.getAnimeId(), req.getScore(), req.getContent());
        return ResponseResult.success(httpConverter.reviewBO2Response(result));
    }

    @PostMapping("/update")
    public ResponseResult<ReviewResponse> update(@Valid @RequestBody ReviewUpdateReq req) {
        ReviewBO result = reviewApplication.updateReview(
            UserContextHolder.getUserId(), req.getAnimeId(), req.getScore(), req.getContent());
        return ResponseResult.success(httpConverter.reviewBO2Response(result));
    }

    @PostMapping("/detail")
    public ResponseResult<ReviewResponse> detail(@Valid @RequestBody ReviewDetailReq req) {
        ReviewBO result = reviewApplication.getMyReview(UserContextHolder.getUserId(), req.getAnimeId());
        return ResponseResult.success(httpConverter.reviewBO2Response(result));
    }

    @PostMapping("/list_by_anime")
    public ResponseResult<PageResponse<ReviewWithUserResponse>> listByAnime(@Valid @RequestBody ReviewListByAnimeReq req) {
        int page = req.getPage() == null ? DEFAULT_PAGE : req.getPage();
        int pageSize = req.getPageSize() == null ? DEFAULT_PAGE_SIZE : req.getPageSize();
        ReviewPageBO<ReviewWithUserViewBO> result = reviewApplication.listByAnime(req.getAnimeId(), page, pageSize);
        return ResponseResult.success(httpConverter.reviewPageBO2Response(result));
    }

    @PostMapping("/my_list")
    public ResponseResult<List<ReviewWithAnimeResponse>> myList() {
        List<ReviewWithAnimeViewBO> result = reviewApplication.listMyReviews(UserContextHolder.getUserId());
        return ResponseResult.success(httpConverter.reviewWithAnimeViewBOList2Response(result));
    }
}
```

- [ ] **Step 8: 运行测试，确认通过**

Run: `mvn -q -pl anitrack-starter -am test -Dtest=ReviewControllerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS（16 个测试通过）

- [ ] **Step 9: Commit**

```bash
git add anitrack-starter/src/main/java/com/anitrack/starter/response/PageResponse.java anitrack-starter/src/main/java/com/anitrack/starter/request/ReviewAddReq.java anitrack-starter/src/main/java/com/anitrack/starter/request/ReviewUpdateReq.java anitrack-starter/src/main/java/com/anitrack/starter/request/ReviewDetailReq.java anitrack-starter/src/main/java/com/anitrack/starter/request/ReviewListByAnimeReq.java anitrack-starter/src/main/java/com/anitrack/starter/response/ReviewResponse.java anitrack-starter/src/main/java/com/anitrack/starter/response/ReviewWithUserResponse.java anitrack-starter/src/main/java/com/anitrack/starter/response/ReviewWithAnimeResponse.java anitrack-starter/src/main/java/com/anitrack/starter/converter/HttpConverter.java anitrack-starter/src/main/java/com/anitrack/starter/controller/ReviewController.java anitrack-starter/src/test/java/com/anitrack/starter/controller/ReviewControllerTest.java
git commit -m "feat: 新增ReviewController评价5个接口、PageResponse通用分页壳与Web层集成测试"
```

---

### Task 7: 全量编译测试 + 端到端联调

**Files:**
- 无新增/修改文件，本任务仅验证

**Interfaces:**
- Consumes：Task 1-6 全部产出
- Produces：可运行的、完整的评价功能

- [ ] **Step 1: 验证整个 Reactor 编译并测试通过**

Run: `mvn -q compile test`
Expected: 所有模块 `BUILD SUCCESS`，Task 1-6 编写的全部单测通过（Phase 1a/1b 原有测试 + 本 spec 新增约 44 个）

- [ ] **Step 2: 端到端手动联调（需要本地 MySQL 与已注册测试用户）**

沿用 Phase 1b 的本地环境配置：

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

Expected: 控制台无异常，日志显示 Flyway 执行 `V4__create_review_table.sql` 成功，`t_review` 表已创建

新开终端，注册并登录一个测试用户拿到 token：

```bash
curl -s -X POST http://localhost:8080/api/user/register \
  -H "Content-Type: application/json" \
  -d '{"username":"review_bob","password":"password123","nickname":"Bob"}'

TOKEN=$(curl -s -X POST http://localhost:8080/api/user/login \
  -H "Content-Type: application/json" \
  -d '{"username":"review_bob","password":"password123"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])")
```

搜索一部番剧回填本地缓存，记下返回的本地 `id`（假设为 `1`），并加入追番：

```bash
curl -s -X POST http://localhost:8080/api/anime/search \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"keyword":"败犬女主太多了"}'

curl -s -X POST http://localhost:8080/api/watchlist/add \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"animeId":1}'
```

测试未看完时评价（应报错，此时状态仍是 `WANT_TO_WATCH`）：

```bash
curl -s -X POST http://localhost:8080/api/review/add \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"animeId":1,"score":8,"content":"很好看"}'
```

Expected: `{"status":0,"message":"只有看完的番剧才能评价","data":null}`

推进追番状态到 `WATCHED`：

```bash
curl -s -X POST http://localhost:8080/api/watchlist/change_status \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"animeId":1,"status":"WATCHING"}'

curl -s -X POST http://localhost:8080/api/watchlist/change_status \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"animeId":1,"status":"WATCHED"}'
```

测试新增评价：

```bash
curl -s -X POST http://localhost:8080/api/review/add \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"animeId":1,"score":8,"content":"很好看"}'
```

Expected: `{"status":1,"message":null,"data":{"id":1,"animeId":1,"score":8,"content":"很好看","createTime":"..."}}`

测试重复评价（应报错）：

```bash
curl -s -X POST http://localhost:8080/api/review/add \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"animeId":1,"score":9,"content":"再看一遍"}'
```

Expected: `{"status":0,"message":"该番剧已评价，请使用修改接口","data":null}`

测试非法评分（应报错）：

```bash
curl -s -X POST http://localhost:8080/api/review/update \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"animeId":1,"score":11,"content":"打个高分"}'
```

Expected: `{"status":0,"message":"评分必须在1-10之间","data":null}`

测试修改评价：

```bash
curl -s -X POST http://localhost:8080/api/review/update \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"animeId":1,"score":10,"content":"二刷之后改主意了，神作"}'
```

Expected: `{"status":1,"message":null,"data":{"id":1,"animeId":1,"score":10,"content":"二刷之后改主意了，神作",...}}`

测试查看我的评价详情：

```bash
curl -s -X POST http://localhost:8080/api/review/detail \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"animeId":1}'
```

Expected: 返回与上一次 `update` 结果一致的详情（`score=10`）

测试按番剧分页查询评价列表：

```bash
curl -s -X POST http://localhost:8080/api/review/list_by_anime \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"animeId":1}'
```

Expected: `{"status":1,"message":null,"data":{"pageNum":1,"pageSize":10,"total":1,"list":[{"id":1,"userId":1,"userNickname":"Bob","userAvatarUrl":null,"score":10,...}]}}`，未传 `page`/`pageSize` 时使用默认值 `1`/`10`，`userNickname` 已正确拼接自 `t_user` 表

测试我的评价列表：

```bash
curl -s -X POST http://localhost:8080/api/review/my_list \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN"
```

Expected: `{"status":1,"message":null,"data":[{"id":1,"animeId":1,"animeTitleCn":"败犬女主太多了！",...,"score":10,...}]}`，`animeTitleCn`/`animeTitleOriginal`/`animeCoverUrl` 均已正确拼接自 `t_anime` 表

验证完成后按 `Ctrl+C` 停止应用。

**若当前环境暂无法访问本地 MySQL**：本 Step 2 需要在具备本地 MySQL 环境中执行；Step 1（自动化测试）不依赖真实数据库，可独立完成并验证。

- [ ] **Step 3: 无代码改动需要提交**

本任务仅做验证，未修改任何文件，无需执行 `git commit`。

---

## Self-Review

**1. Spec 覆盖度**：本 spec（`docs/superpowers/specs/2026-07-06-anitrack-phase2-review-design.md`）要求的领域模型（`Review` 聚合根 score 校验，Task 1）、`ReviewRepo`/`ReviewDomainService` 跨上下文编排（`ReviewNotAllowedException`/`ReviewAlreadyExistsException`，Task 2）、持久化层（`t_review`/`ReviewPO`/`ReviewMapper`/`UserMapper.selectByIds`，Task 3）、`ReviewConverter`/`ReviewRepoImpl`/`UserRepo.listByIds`（Task 4）、应用层（`ReviewApplication` 5 个用例、异常转译、`ReviewAssembler` 两种拼接、`ReviewPageBO` 分页，Task 5）、Web 层（5 个接口、`PageResponse<T>` 通用分页壳，Task 6）均已覆盖。本 spec 明确的边界条件——`score` 必须在 `[1,10]`（Task 1 测试覆盖）、`content` 允许为空（Task 1 测试覆盖）、只有 `WATCHED` 才能评价（Task 2 测试覆盖非 WATCHED 与无追番记录两种场景）、重复评价拒绝（Task 2 测试覆盖）、`updateReview`/`getMyReview` 不经过领域异常直接抛 `AnitrackAppException`（Task 5 `ReviewApplication` 实现与测试均未引入多余的 `ReviewNotFoundException`）、`listByAnime` 分页 offset 计算（Task 5 测试 `listByAnime_whenPageIsTwo_shouldComputeOffset` 覆盖）、`listMyReviews` 不分页（Task 5/6 均为 `List` 返回类型，非 `ReviewPageBO`）均已在对应任务体现。"明确不做的事"（Anime 聚合分数展示、物理删除、`listMyReviews` 分页、`content` 长度校验、领域事件）均未在任何任务中实现，符合 spec 的 YAGNI 范围。

**2. 占位符扫描**：全文所有 Step 均为可直接运行的完整代码/命令，无 TODO/"参考 Task N 类似实现"/"添加适当异常处理"等占位表述。

**3. 类型一致性检查**：`Review.create`/`reconstitute`/`update`（Task 1 定义：`create(userId, animeId, score, content)`；`reconstitute(id, userId, animeId, score, content, createTime)`；`update(score, content)`）在 Task 2 的 `ReviewDomainServiceTest`、Task 4 的 `ReviewConverter`/`ReviewRepoImplTest`、Task 5 的 `ReviewApplicationTest` 中参数顺序保持一致；`ReviewRepo` 六个方法（`getByUserAndAnime`/`listByAnime`/`countByAnime`/`listByUser`/`add`/`update`，Task 2 定义）与 `ReviewRepoImpl`（Task 4）、`ReviewApplication`（Task 5）中的调用签名一致；`ReviewDomainService.addReview(Long,Long,Integer,String)`（Task 2 定义）与 `ReviewApplication`（Task 5）中的调用签名一致；`UserRepo.listByIds(List<Long>)`（Task 4 新增）与 `ReviewApplication.listByAnime`（Task 5）、`UserRepoImplTest`（Task 4）中的调用签名一致；`ReviewBO`/`ReviewWithUserViewBO`/`ReviewWithAnimeViewBO`/`ReviewPageBO<T>` 字段（Task 5 定义）与 Task 6 的 `HttpConverter`/`ReviewResponse`/`ReviewWithUserResponse`/`ReviewWithAnimeResponse`/`PageResponse<T>`/`ReviewControllerTest` 中的字段完全对应（`ReviewPageBO.page`/`pageSize` 对应 `PageResponse.pageNum`/`pageSize`，映射在 `HttpConverter.reviewPageBO2Response` 中显式完成）；`AppExceptionEnum` 新增的四个常量（Task 5）与 Task 5/6 测试中的断言消息完全一致。

---

## 与用户的既定流程约束（重申）

- 本机默认 `mvn` 版本过旧（见 Global Constraints），执行本计划前必须先切换 `JAVA_HOME`/`PATH`，否则所有 `mvn` 命令都会因 Maven/JDK 版本不满足要求而失败。
- Task 7 Step 2 的端到端联调依赖本地可用的 MySQL 环境，若当前环境不具备，可先完成 Step 1（自动化测试），Step 2 留到有本地 MySQL 的环境中执行。
