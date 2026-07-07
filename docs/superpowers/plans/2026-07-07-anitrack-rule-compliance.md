# anitrack 规则合规修复 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 4 项中等级别规则偏差——应用层缺 MapStruct Converter、Web 响应枚举非 `{code,name}`、时间字段未 ISO-8601 序列化、UserController 直接注入 JwtTokenProvider。

**Architecture:** 按上下文拆分 4 个应用层 MapStruct Converter 收拢 toBO；新增 EnumVO + WatchStatus.code 让响应枚举输出 `{code,name}`；新增 JacksonConfig 全局禁用 WRITE_DATES_AS_TIMESTAMPS；domain 层新增 TokenProvider 接口让 UserApplication 依赖接口而非 infra 实现类，JwtTokenProvider 实现该接口。

**Tech Stack:** Spring Boot 3 / Java 17 / MapStruct / Lombok / MyBatis / JUnit5 + Mockito + AssertJ / MockMvc

## Global Constraints

- 依赖方向：`application` 不直接依赖 `infrastructure`；`application → domain`，`starter → application/infrastructure`，`infrastructure → domain`
- 领域层不依赖 Spring 框架类型（`TokenProvider` 是纯接口，放 domain 层）
- MapStruct `@Mapper(componentModel = "spring")`
- 方法命名 `xxx2Yyy`（如 `watchlistItem2BO`、`userRegisterReq2BO`）
- 聚合根 `@Builder(access = PRIVATE)`，MapStruct toBO 方向自动映射（源有 getter、目标 BO `@Builder` 公开）
- Maven 多模块：`JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home PATH=/opt/homebrew/opt/maven/bin:$PATH mvn`
- 跨模块测试需先 `install -DskipTests` 上游模块
- 提交分 3 个 commit：Commit A=Task1-2（Converter）、Commit B=Task3-5（Web枚举+时间）、Commit C=Task6-9（JWT下沉）

---

## Task 1: 应用层 MapStruct Converter（Anime + User）

**Files:**
- Create: `anitrack-application/src/main/java/com/anitrack/application/converter/AnimeConverter.java`
- Create: `anitrack-application/src/main/java/com/anitrack/application/converter/UserConverter.java`
- Modify: `anitrack-application/src/main/java/com/anitrack/application/service/AnimeApplication.java`（删 `toBO`，注入 `AnimeConverter`）
- Modify: `anitrack-application/src/main/java/com/anitrack/application/service/UserApplication.java`（删 `toBO`，注入 `UserConverter`）
- Modify: `anitrack-application/src/test/java/com/anitrack/application/service/AnimeApplicationTest.java`
- Modify: `anitrack-application/src/test/java/com/anitrack/application/service/UserApplicationTest.java`

**Interfaces:**
- Produces: `AnimeConverter.anime2BO(Anime) -> AnimeBO`；`UserConverter.user2BO(User) -> UserBO`

**说明：** application 模块需有 MapStruct 依赖。先检查 `anitrack-application/pom.xml` 是否已有 `mapstruct` + annotation processor；若无，需添加（参照 `anitrack-infrastructure/pom.xml` 的配置）。

- [ ] **Step 1: 确认/添加 application 模块 MapStruct 依赖**

读 `anitrack-application/pom.xml` 与 `anitrack-infrastructure/pom.xml`。若 application 缺 mapstruct 依赖或 annotation processor，从 infrastructure pom 复制对应片段到 application pom（`mapstruct` dep + `maven-compiler-plugin` 的 annotationProcessorPaths 含 `mapstruct-processor` + `lombok`）。版本已在根 pom `properties` 统一管理，子模块不指定版本。

- [ ] **Step 2: 写 AnimeConverter 测试**

Create `anitrack-application/src/test/java/com/anitrack/application/converter/AnimeConverterTest.java`：

```java
package com.anitrack.application.converter;

import com.anitrack.application.model.AnimeBO;
import com.anitrack.domain.anime.model.Anime;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AnimeConverterTest {

    private final AnimeConverter converter = new AnimeConverterImpl();

    @Test
    void anime2BO_shouldMapAllFields() {
        Anime anime = Anime.reconstitute(1L, 100L, "中文名", "Original", "http://cover.jpg", 12,
            LocalDate.of(2024, 1, 1), "简介");
        AnimeBO bo = converter.anime2BO(anime);
        assertThat(bo.getId()).isEqualTo(1L);
        assertThat(bo.getBangumiId()).isEqualTo(100L);
        assertThat(bo.getTitleCn()).isEqualTo("中文名");
        assertThat(bo.getTotalEpisodes()).isEqualTo(12);
        assertThat(bo.getSummary()).isEqualTo("简介");
    }
}
```

> MapStruct 编译期生成 `AnimeConverterImpl`（无参构造），测试直接 `new AnimeConverterImpl()`。需 Step 3 的接口先存在或同 commit 编译（实际 Step 2/3 一起编译）。

- [ ] **Step 3: 创建 AnimeConverter 接口**

Create `anitrack-application/src/main/java/com/anitrack/application/converter/AnimeConverter.java`：

```java
package com.anitrack.application.converter;

import com.anitrack.application.model.AnimeBO;
import com.anitrack.domain.anime.model.Anime;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AnimeConverter {

    AnimeBO anime2BO(Anime anime);
}
```

- [ ] **Step 4: 编译 + 跑测试**

Run:
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home PATH=/opt/homebrew/opt/maven/bin:$PATH mvn -q -pl anitrack-application -am test -Dtest=AnimeConverterTest
```
Expected: PASS（MapStruct 生成 `AnimeConverterImpl`）

- [ ] **Step 5: 改造 AnimeApplication 注入 Converter**

Modify `AnimeApplication.java`：
- 新增 import `com.anitrack.application.converter.AnimeConverter`
- 类字段加 `private final AnimeConverter animeConverter;`
- `searchAnime` 中 `.map(this::toBO)` 改为 `.map(animeConverter::anime2BO)`
- `getAnimeDetail` 中 `return toBO(anime);` 改为 `return animeConverter.anime2BO(anime);`
- 删除 `private AnimeBO toBO(Anime anime)` 方法（第 48-59 行）

- [ ] **Step 6: 更新 AnimeApplicationTest**

`AnimeApplicationTest` 用 `@InjectMocks`，新增 `@Mock private AnimeConverter mockAnimeConverter;`。把原来依赖 `toBO` 的断言改为 mock `mockAnimeConverter.anime2BO(any())` 返回预期 BO：

```java
import com.anitrack.application.converter.AnimeConverter;
import com.anitrack.application.model.AnimeBO;
// ...

@Mock
private AnimeConverter mockAnimeConverter;

@Test
void searchAnime_whenGatewaySucceeds_shouldUpsertAndReturnBOList() {
    Anime searchResult = Anime.fromBangumi(100L, "中文名", "Original Title", "http://cover.jpg", 12,
        LocalDate.of(2024, 1, 1), "简介");
    Anime persisted = Anime.reconstitute(1L, 100L, "中文名", "Original Title", "http://cover.jpg", 12,
        LocalDate.of(2024, 1, 1), "简介");
    AnimeBO bo = AnimeBO.builder().id(1L).titleCn("中文名").build();
    when(mockBangumiGateway.search("关键字")).thenReturn(List.of(searchResult));
    when(mockAnimeRepo.upsert(searchResult)).thenReturn(persisted);
    when(mockAnimeConverter.anime2BO(persisted)).thenReturn(bo);

    List<AnimeBO> result = sut.searchAnime("关键字");

    assertThat(result).containsExactly(bo);
    verify(mockAnimeRepo, times(1)).upsert(searchResult);
}
```

`getAnimeDetail_whenAnimeExists_shouldReturnBO` 同理 mock `mockAnimeConverter.anime2BO(anime)` 返回带 id/titleOriginal 的 BO，断言改为检查 BO 字段。

- [ ] **Step 7: 跑 AnimeApplicationTest**

Run:
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home PATH=/opt/homebrew/opt/maven/bin:$PATH mvn -q -pl anitrack-application -am test -Dtest=AnimeApplicationTest
```
Expected: PASS

- [ ] **Step 8: 创建 UserConverter 接口**

Create `anitrack-application/src/main/java/com/anitrack/application/converter/UserConverter.java`：

```java
package com.anitrack.application.converter;

import com.anitrack.application.model.UserBO;
import com.anitrack.domain.user.model.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserConverter {

    UserBO user2BO(User user);
}
```

- [ ] **Step 9: 改造 UserApplication 注入 Converter**

Modify `UserApplication.java`：
- 新增 import `com.anitrack.application.converter.UserConverter`
- 字段加 `private final UserConverter userConverter;`
- `register` 中 `return toBO(savedUser);` 改为 `return userConverter.user2BO(savedUser);`
- `login` 中 `return toBO(user);` 改为 `return userConverter.user2BO(user);`
- 删除 `private UserBO toBO(User user)` 方法（第 41-49 行）

> 注意：Task 1 阶段 `UserApplication` 构造参数为 `(UserRepo, PasswordEncoder, UserConverter)`。Task 6 会再加 `TokenProvider`。

- [ ] **Step 10: 更新 UserApplicationTest**

`UserApplicationTest` 用手工 `new UserApplication(...)` 构造。需：
- 新增 `@Mock private UserConverter mockUserConverter;`
- 每个 `new UserApplication(mockUserRepo, mockPasswordEncoder)` 改为 `new UserApplication(mockUserRepo, mockPasswordEncoder, mockUserConverter)`
- `register` 测试 mock `mockUserConverter.user2BO(any())` 返回预期 `UserBO`，断言改检查 BO
- `login_whenCredentialsAreValid` 同理 mock converter 返回带 id/username 的 BO

示例改造 `register_whenUsernameNotExists`：
```java
@Mock
private UserConverter mockUserConverter;

@Test
void register_whenUsernameNotExists_shouldSaveAndReturnUserBO() {
    sut = new UserApplication(mockUserRepo, mockPasswordEncoder, mockUserConverter);
    UserRegisterBO registerBO = UserRegisterBO.builder()
        .username("alice").password("raw-password").nickname("Alice").build();
    when(mockUserRepo.existsByUsername("alice")).thenReturn(false);
    when(mockPasswordEncoder.encode("raw-password")).thenReturn("hashed-password");
    when(mockUserRepo.save(any())).thenAnswer(invocation -> {
        User toSave = invocation.getArgument(0);
        return User.reconstitute(1L, toSave.getUsername(), toSave.getPasswordHash(),
            toSave.getNickname(), toSave.getAvatarUrl(), toSave.getRole());
    });
    UserBO expectedBO = UserBO.builder().id(1L).username("alice").nickname("Alice").role(UserRole.USER).build();
    when(mockUserConverter.user2BO(any())).thenReturn(expectedBO);

    UserBO result = sut.register(registerBO);

    assertThat(result).isEqualTo(expectedBO);
    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(mockUserRepo, times(1)).save(captor.capture());
    assertThat(captor.getValue().getPasswordHash()).isEqualTo("hashed-password");
}
```

`login_whenCredentialsAreValid`：
```java
@Test
void login_whenCredentialsAreValid_shouldReturnUserBO() {
    sut = new UserApplication(mockUserRepo, mockPasswordEncoder, mockUserConverter);
    UserLoginBO loginBO = UserLoginBO.builder().username("alice").password("raw-password").build();
    User existingUser = User.reconstitute(1L, "alice", "hashed-password", "Alice", null, UserRole.USER);
    when(mockUserRepo.getByUsername("alice")).thenReturn(existingUser);
    when(mockPasswordEncoder.matches("raw-password", "hashed-password")).thenReturn(true);
    UserBO expectedBO = UserBO.builder().id(1L).username("alice").build();
    when(mockUserConverter.user2BO(existingUser)).thenReturn(expectedBO);

    UserBO result = sut.login(loginBO);

    assertThat(result).isEqualTo(expectedBO);
}
```

其余 `login_whenUserNotFound`/`login_whenPasswordIncorrect`/`register_whenUsernameExists` 只需更新构造参数，无需 mock converter（异常路径不触达）。

- [ ] **Step 11: 跑 UserApplicationTest**

Run:
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home PATH=/opt/homebrew/opt/maven/bin:$PATH mvn -q -pl anitrack-application -am test -Dtest=UserApplicationTest
```
Expected: PASS

---

## Task 2: 应用层 MapStruct Converter（Watchlist + Review）

**Files:**
- Create: `anitrack-application/src/main/java/com/anitrack/application/converter/WatchlistConverter.java`
- Create: `anitrack-application/src/main/java/com/anitrack/application/converter/ReviewConverter.java`
- Modify: `anitrack-application/src/main/java/com/anitrack/application/service/WatchlistApplication.java`
- Modify: `anitrack-application/src/main/java/com/anitrack/application/service/ReviewApplication.java`
- Modify: `anitrack-application/src/test/java/com/anitrack/application/service/WatchlistApplicationTest.java`
- Modify: `anitrack-application/src/test/java/com/anitrack/application/service/ReviewApplicationTest.java`

**Interfaces:**
- Produces: `WatchlistConverter.watchlistItem2BO(WatchlistItem) -> WatchlistItemBO`；`ReviewConverter.review2BO(Review) -> ReviewBO`

- [ ] **Step 1: 创建 WatchlistConverter**

Create `anitrack-application/src/main/java/com/anitrack/application/converter/WatchlistConverter.java`：

```java
package com.anitrack.application.converter;

import com.anitrack.application.model.WatchlistItemBO;
import com.anitrack.domain.watchlist.model.WatchlistItem;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface WatchlistConverter {

    WatchlistItemBO watchlistItem2BO(WatchlistItem item);
}
```

- [ ] **Step 2: 创建 ReviewConverter**

Create `anitrack-application/src/main/java/com/anitrack/application/converter/ReviewConverter.java`：

```java
package com.anitrack.application.converter;

import com.anitrack.application.model.ReviewBO;
import com.anitrack.domain.review.model.Review;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ReviewConverter {

    ReviewBO review2BO(Review review);
}
```

- [ ] **Step 3: 改造 WatchlistApplication**

Modify `WatchlistApplication.java`：
- 新增 import `com.anitrack.application.converter.WatchlistConverter`
- 字段加 `private final WatchlistConverter watchlistConverter;`
- `addToWatchlist` 中 `return toBO(item);` 改为 `return watchlistConverter.watchlistItem2BO(item);`
- `changeStatus` 中 `return toBO(item);` 改为 `return watchlistConverter.watchlistItem2BO(item);`
- `updateProgress` 中 `return toBO(item);` 改为 `return watchlistConverter.watchlistItem2BO(item);`
- `getWatchlistItem` 中 `return toBO(item);` 改为 `return watchlistConverter.watchlistItem2BO(item);`
- 删除 `private WatchlistItemBO toBO(WatchlistItem item)` 方法（第 103-111 行）

- [ ] **Step 4: 改造 ReviewApplication**

Modify `ReviewApplication.java`：
- 新增 import `com.anitrack.application.converter.ReviewConverter`
- 字段加 `private final ReviewConverter reviewConverter;`
- `addReview` 中 `return toBO(review);` 改为 `return reviewConverter.review2BO(review);`
- `updateReview` 中 `return toBO(review);` 改为 `return reviewConverter.review2BO(review);`
- `getMyReview` 中 `return toBO(review);` 改为 `return reviewConverter.review2BO(review);`
- 删除 `private ReviewBO toBO(Review review)` 方法（第 96-104 行）

- [ ] **Step 5: 更新 WatchlistApplicationTest**

新增 `@Mock private WatchlistConverter mockWatchlistConverter;`（`@InjectMocks` 会自动注入）。

`addToWatchlist_whenSucceeds_shouldReturnBO`：原断言 `result.getId()`/`result.getStatus()`，改为 mock converter 返回预期 BO：
```java
@Test
void addToWatchlist_whenSucceeds_shouldReturnBO() {
    WatchlistItem item = WatchlistItem.reconstitute(10L, 1L, 100L, WatchStatus.WANT_TO_WATCH, 0, null);
    when(mockWatchlistDomainService.addToWatchlist(1L, 100L)).thenReturn(item);
    WatchlistItemBO expectedBO = WatchlistItemBO.builder().id(10L).status(WatchStatus.WANT_TO_WATCH).build();
    when(mockWatchlistConverter.watchlistItem2BO(item)).thenReturn(expectedBO);

    WatchlistItemBO result = sut.addToWatchlist(1L, 100L);

    assertThat(result.getId()).isEqualTo(10L);
    assertThat(result.getStatus()).isEqualTo(WatchStatus.WANT_TO_WATCH);
}
```

`changeStatus_whenSucceeds_shouldReturnBOAndPublishEvent`：mock converter 返回带 WATCHED 的 BO：
```java
WatchlistItemBO expectedBO = WatchlistItemBO.builder().status(WatchStatus.WATCHED).build();
when(mockWatchlistConverter.watchlistItem2BO(item)).thenReturn(expectedBO);
// ...
assertThat(result.getStatus()).isEqualTo(WatchStatus.WATCHED);
verify(mockEventPublisher, times(1)).publishEvent(event);
```

`updateProgress_whenSucceeds_shouldReturnBO`、`getWatchlistItem_whenExists_shouldReturnBO` 同理 mock converter。

异常路径测试（`whenAnimeNotFound`/`whenAlreadyExists`/`whenItemNotFound`/`whenTransitionIllegal`/`whenTotalEpisodesInvalid`/`whenProgressIllegal`/`whenNotFound`）无需 mock converter（异常路径不触达）。

`listMyWatchlist` 不调 toBO，无需改。

- [ ] **Step 6: 更新 ReviewApplicationTest**

新增 `@Mock private ReviewConverter mockReviewConverter;`。

`addReview_whenSucceeds_shouldReturnBO`：mock converter 返回带 id/score 的 BO：
```java
ReviewBO expectedBO = ReviewBO.builder().id(20L).score(8).build();
when(mockReviewConverter.review2BO(review)).thenReturn(expectedBO);
ReviewBO result = sut.addReview(1L, 100L, 8, "很好看");
assertThat(result.getId()).isEqualTo(20L);
assertThat(result.getScore()).isEqualTo(8);
```

`updateReview_whenExists_shouldUpdateAndReturnBO`、`getMyReview_whenExists_shouldReturnBO` 同理 mock converter。

异常路径（`whenNotAllowed`/`whenAlreadyExists`/`whenScoreIllegal`/`whenNotFound`）无需 mock converter。

`listByAnime`/`listMyReviews` 不调 toBO，无需改。

- [ ] **Step 7: 跑全部 application 模块测试**

Run:
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home PATH=/opt/homebrew/opt/maven/bin:$PATH mvn -q -pl anitrack-application -am test
```
Expected: 全过（AnimeConverterTest/UserApplicationTest/WatchlistApplicationTest/ReviewApplicationTest/AnimeApplicationTest）

- [ ] **Step 8: Commit A**

```bash
git add anitrack-application/
git commit -m "$(cat <<'EOF'
refactor(application): 收拢 toBO 至 MapStruct Converter

按上下文拆分 AnimeConverter/UserConverter/WatchlistConverter/ReviewConverter，
4 个 Application 删除手写 toBO 改注入 Converter，方法命名遵循 xxx2Yyy。

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: WatchStatus 加 code 字段 + EnumVO

**Files:**
- Modify: `anitrack-domain/src/main/java/com/anitrack/domain/watchlist/enums/WatchStatus.java`
- Create: `anitrack-starter/src/main/java/com/anitrack/starter/response/vo/EnumVO.java`

**Interfaces:**
- Produces: `WatchStatus.getCode() -> Integer`；`EnumVO{Integer code, String name}`

**说明：** `WatchlistItemPO.status` 是 `String`，mapper 用 `name()` 存取，加 code 不影响持久化。但需检查 `WatchlistItemConverter`（infra）和 `WatchlistItemMapper.xml` 的 status 映射——PO 是 String，domain WatchStatus 枚举，MapStruct 默认用 `name()` 转换，加 code 字段不破坏。

- [ ] **Step 1: 写 WatchStatus code 测试**

Modify `anitrack-domain/src/test/java/com/anitrack/domain/watchlist/enums/WatchStatusTest.java`（若不存在则创建）：

```java
package com.anitrack.domain.watchlist.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WatchStatusTest {

    @Test
    void code_shouldBeStable() {
        assertThat(WatchStatus.WANT_TO_WATCH.getCode()).isEqualTo(1);
        assertThat(WatchStatus.WATCHING.getCode()).isEqualTo(2);
        assertThat(WatchStatus.WATCHED.getCode()).isEqualTo(3);
        assertThat(WatchStatus.DROPPED.getCode()).isEqualTo(4);
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run:
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home PATH=/opt/homebrew/opt/maven/bin:$PATH mvn -q -pl anitrack-domain test -Dtest=WatchStatusTest
```
Expected: FAIL（无 getCode 方法）

- [ ] **Step 3: 给 WatchStatus 加 code**

Modify `WatchStatus.java`：
```java
package com.anitrack.domain.watchlist.enums;

import lombok.Getter;

@Getter
public enum WatchStatus {
    WANT_TO_WATCH(1),
    WATCHING(2),
    WATCHED(3),
    DROPPED(4);

    private final Integer code;

    WatchStatus(Integer code) {
        this.code = code;
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

Run:
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home PATH=/opt/homebrew/opt/maven/bin:$PATH mvn -q -pl anitrack-domain test -Dtest=WatchStatusTest
```
Expected: PASS

- [ ] **Step 5: 跑 domain 全量测试确认无回归**

Run:
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home PATH=/opt/homebrew/opt/maven/bin:$PATH mvn -q -pl anitrack-domain test
```
Expected: 全过（确认 WatchlistItemTest/WatchlistDomainServiceTest 等不受影响）

- [ ] **Step 6: 创建 EnumVO**

Create `anitrack-starter/src/main/java/com/anitrack/starter/response/vo/EnumVO.java`：
```java
package com.anitrack.starter.response.vo;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EnumVO {
    private final Integer code;
    private final String name;
}
```

- [ ] **Step 7: 在 HttpConverter 加 WatchStatus 转 EnumVO 方法**

Modify `anitrack-starter/src/main/java/com/anitrack/starter/converter/HttpConverter.java`，新增 import `com.anitrack.starter.response.vo.EnumVO` 和 `com.anitrack.domain.watchlist.enums.WatchStatus`，新增方法：
```java
public EnumVO watchStatus2VO(WatchStatus status) {
    if (status == null) {
        return null;
    }
    return EnumVO.builder()
        .code(status.getCode())
        .name(status.name())
        .build();
}
```

---

## Task 4: WatchlistItemResponse / ViewResponse 改用 EnumVO

**Files:**
- Modify: `anitrack-starter/src/main/java/com/anitrack/starter/response/WatchlistItemResponse.java`
- Modify: `anitrack-starter/src/main/java/com/anitrack/starter/response/WatchlistItemViewResponse.java`
- Modify: `anitrack-starter/src/main/java/com/anitrack/starter/converter/HttpConverter.java`
- Modify: `anitrack-starter/src/test/java/com/anitrack/starter/controller/WatchlistControllerTest.java`

**Interfaces:**
- Consumes: Task 3 的 `EnumVO`、`WatchStatus.getCode()`、`HttpConverter.watchStatus2VO`

- [ ] **Step 1: 改 WatchlistItemResponse**

Modify `WatchlistItemResponse.java`：`status` 字段类型 `WatchStatus` 改为 `EnumVO`：
```java
package com.anitrack.starter.response;

import com.anitrack.starter.response.vo.EnumVO;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class WatchlistItemResponse {
    private Long id;
    private Long animeId;
    private EnumVO status;
    private Integer currentEpisode;
    private LocalDateTime updateTime;
}
```

- [ ] **Step 2: 改 WatchlistItemViewResponse**

读 `WatchlistItemViewResponse.java`，同样把 `private WatchStatus status;` 改为 `private EnumVO status;`，import 调整。

- [ ] **Step 3: 改 HttpConverter 转换方法**

Modify `HttpConverter.java`：
- `watchlistItemBO2Response` 中 `.status(bo.getStatus())` 改为 `.status(watchStatus2VO(bo.getStatus()))`
- `watchlistItemViewBO2Response` 中 `.status(bo.getStatus())` 改为 `.status(watchStatus2VO(bo.getStatus()))`

- [ ] **Step 4: 更新 WatchlistControllerTest 断言**

Modify `WatchlistControllerTest.java`：
- `add_whenRequestIsValid_shouldReturnWatchlistItem`：`$.data.status` 原断言 `"WANT_TO_WATCH"` 改为 `$.data.status.name` 断言 `"WANT_TO_WATCH"`：
```java
.andExpect(jsonPath("$.data.status.name").value("WANT_TO_WATCH"))
.andExpect(jsonPath("$.data.status.code").value(1));
```
- `changeStatus_whenRequestIsValid_shouldReturnWatchlistItem`：`$.data.status` 改为 `$.data.status.name` 值 `"WATCHING"`：
```java
.andExpect(jsonPath("$.data.status.name").value("WATCHING"))
.andExpect(jsonPath("$.data.status.code").value(2));
```

- [ ] **Step 5: 跑 WatchlistControllerTest**

Run:
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home PATH=/opt/homebrew/opt/maven/bin:$PATH mvn -q -pl anitrack-starter -am test -Dtest=WatchlistControllerTest
```
Expected: PASS

---

## Task 5: JacksonConfig 全局 ISO-8601 时间序列化

**Files:**
- Create: `anitrack-starter/src/main/java/com/anitrack/starter/config/JacksonConfig.java`
- Modify: `anitrack-starter/src/test/java/com/anitrack/starter/controller/WatchlistControllerTest.java`（若涉及时间断言）
- Create: `anitrack-starter/src/test/java/com/anitrack/starter/config/JacksonConfigTest.java`

**说明：** `@WebMvcTest` 默认不加载非 web 配置，但 `JacksonConfig` 是 `@Configuration`，需确认 MockMvc 用的 ObjectMapper 是否应用了该配置。Spring Boot 的 `@WebMvcTest` 会自动配置 `ObjectMapper`，`Jackson2ObjectMapperBuilderCustomizer` Bean 在切片测试中也会被应用（自动配置机制）。若测试中发现未生效，需在测试类加 `@Import(JacksonConfig.class)`。

- [ ] **Step 1: 写 JacksonConfigTest**

Create `anitrack-starter/src/test/java/com/anitrack/starter/config/JacksonConfigTest.java`：
```java
package com.anitrack.starter.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonConfigTest {

    @Test
    void localDateTime_shouldSerializeAsIso8601String() throws Exception {
        Jackson2ObjectMapperBuilderCustomizer customizer = new JacksonConfig().jacksonCustomizer();
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        customizer.customize(builder);
        ObjectMapper mapper = builder.build();

        String json = mapper.writeValueAsString(LocalDateTime.of(2026, 7, 7, 12, 34, 56));
        assertThat(json).isEqualTo("\"2026-07-07T12:34:56\"");
    }
}
```

> customizer 已通过 `modulesToInstall(new JavaTimeModule())` 注册时间模块，测试无需重复安装。

- [ ] **Step 2: 跑测试确认失败**

Run:
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home PATH=/opt/homebrew/opt/maven/bin:$PATH mvn -q -pl anitrack-starter -am test -Dtest=JacksonConfigTest
```
Expected: FAIL（JacksonConfig 不存在）

- [ ] **Step 3: 创建 JacksonConfig**

Create `anitrack-starter/src/main/java/com/anitrack/starter/config/JacksonConfig.java`：
```java
package com.anitrack.starter.config;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

- [ ] **Step 4: 跑 JacksonConfigTest**

Run:
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home PATH=/opt/homebrew/opt/maven/bin:$PATH mvn -q -pl anitrack-starter -am test -Dtest=JacksonConfigTest
```
Expected: PASS

- [ ] **Step 5: 跑 WatchlistControllerTest 确认时间序列化无回归**

Run:
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home PATH=/opt/homebrew/opt/maven/bin:$PATH mvn -q -pl anitrack-starter -am test -Dtest=WatchlistControllerTest
```
Expected: PASS（现有测试未断言时间字段格式，应无影响。若有时间断言失败，改为 ISO-8601 字符串断言）

- [ ] **Step 6: 跑 starter 全量测试**

Run:
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home PATH=/opt/homebrew/opt/maven/bin:$PATH mvn -q -pl anitrack-starter -am test
```
Expected: 全过

- [ ] **Step 7: Commit B**

```bash
git add anitrack-starter/ anitrack-domain/src/main/java/com/anitrack/domain/watchlist/enums/WatchStatus.java anitrack-domain/src/test/java/com/anitrack/domain/watchlist/enums/WatchStatusTest.java
git commit -m "$(cat <<'EOF'
feat(web): 枚举响应 {code,name} 与时间 ISO-8601 序列化

WatchStatus 新增 code 字段，Response 改用 EnumVO 输出 {code,name}；
新增 JacksonConfig 全局禁用 WRITE_DATES_AS_TIMESTAMPS，LocalDateTime 输出 ISO-8601 字符串。

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: domain 层 TokenProvider 接口

**Files:**
- Create: `anitrack-domain/src/main/java/com/anitrack/domain/user/service/TokenProvider.java`

**Interfaces:**
- Produces: `TokenProvider.generateToken(Long userId) -> String`（接口）

- [ ] **Step 1: 创建 TokenProvider 接口**

Create `anitrack-domain/src/main/java/com/anitrack/domain/user/service/TokenProvider.java`：
```java
package com.anitrack.domain.user.service;

public interface TokenProvider {
    String generateToken(Long userId);
}
```

> 放 `domain.user.service` 包。`service` 子包当前可能不存在（user 上下文无 DomainService），创建即可。接口无 Spring 依赖，符合领域层规范。

- [ ] **Step 2: 跑 domain 测试确认无回归**

Run:
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home PATH=/opt/homebrew/opt/maven/bin:$PATH mvn -q -pl anitrack-domain test
```
Expected: 全过

---

## Task 7: JwtTokenProvider 实现 TokenProvider

**Files:**
- Modify: `anitrack-infrastructure/src/main/java/com/anitrack/infra/auth/JwtTokenProvider.java`

**Interfaces:**
- Consumes: Task 6 的 `TokenProvider`
- Produces: `JwtTokenProvider implements TokenProvider`

- [ ] **Step 1: 让 JwtTokenProvider 实现 TokenProvider**

Modify `JwtTokenProvider.java`：
- 新增 import `com.anitrack.domain.user.service.TokenProvider`
- 类声明改为 `public class JwtTokenProvider implements TokenProvider`
- `generateToken` 方法已有，加 `@Override`

```java
@Component
public class JwtTokenProvider implements TokenProvider {

    // ... 字段/构造不变 ...

    @Override
    public String generateToken(Long userId) {
        // ... 原实现不变 ...
    }
    // validateToken / getUserId 不变
}
```

- [ ] **Step 2: 跑 infrastructure 测试确认无回归**

Run:
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home PATH=/opt/homebrew/opt/maven/bin:$PATH mvn -q -pl anitrack-infrastructure -am test
```
Expected: 全过

---

## Task 8: UserApplication 下沉 token 签发 + LoginBO

**Files:**
- Create: `anitrack-application/src/main/java/com/anitrack/application/model/LoginBO.java`
- Modify: `anitrack-application/src/main/java/com/anitrack/application/service/UserApplication.java`
- Modify: `anitrack-application/src/test/java/com/anitrack/application/service/UserApplicationTest.java`

**Interfaces:**
- Consumes: Task 6 的 `TokenProvider`
- Produces: `LoginBO{UserBO user, String token}`；`UserApplication.login(UserLoginBO) -> LoginBO`

- [ ] **Step 1: 创建 LoginBO**

Create `anitrack-application/src/main/java/com/anitrack/application/model/LoginBO.java`：
```java
package com.anitrack.application.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginBO {
    private final UserBO user;
    private final String token;
}
```

- [ ] **Step 2: 改造 UserApplication**

Modify `UserApplication.java`：
- 新增 import `com.anitrack.domain.user.service.TokenProvider`、`com.anitrack.application.model.LoginBO`
- 字段加 `private final TokenProvider tokenProvider;`
- `login` 方法返回类型由 `UserBO` 改为 `LoginBO`：
```java
public LoginBO login(UserLoginBO loginBO) {
    User user = userRepo.getByUsername(loginBO.getUsername());
    if (user == null || !passwordEncoder.matches(loginBO.getPassword(), user.getPasswordHash())) {
        throw new AnitrackAppException(AppExceptionEnum.LOGIN_FAILED);
    }
    UserBO userBO = userConverter.user2BO(user);
    String token = tokenProvider.generateToken(user.getId());
    return LoginBO.builder().user(userBO).token(token).build();
}
```

> 此时 `UserApplication` 构造参数为 `(UserRepo, PasswordEncoder, UserConverter, TokenProvider)`。

- [ ] **Step 3: 更新 UserApplicationTest**

`UserApplicationTest`：
- 新增 `@Mock private TokenProvider mockTokenProvider;`
- 所有 `new UserApplication(mockUserRepo, mockPasswordEncoder, mockUserConverter)` 改为 `new UserApplication(mockUserRepo, mockPasswordEncoder, mockUserConverter, mockTokenProvider)`
- `login_whenCredentialsAreValid` 改为断言 `LoginBO`：
```java
@Test
void login_whenCredentialsAreValid_shouldReturnLoginBO() {
    sut = new UserApplication(mockUserRepo, mockPasswordEncoder, mockUserConverter, mockTokenProvider);
    UserLoginBO loginBO = UserLoginBO.builder().username("alice").password("raw-password").build();
    User existingUser = User.reconstitute(1L, "alice", "hashed-password", "Alice", null, UserRole.USER);
    when(mockUserRepo.getByUsername("alice")).thenReturn(existingUser);
    when(mockPasswordEncoder.matches("raw-password", "hashed-password")).thenReturn(true);
    UserBO expectedUserBO = UserBO.builder().id(1L).username("alice").build();
    when(mockUserConverter.user2BO(existingUser)).thenReturn(expectedUserBO);
    when(mockTokenProvider.generateToken(1L)).thenReturn("mock-token");

    LoginBO result = sut.login(loginBO);

    assertThat(result.getUser()).isEqualTo(expectedUserBO);
    assertThat(result.getToken()).isEqualTo("mock-token");
}
```
新增 import `com.anitrack.application.model.LoginBO`、`com.anitrack.domain.user.service.TokenProvider`。

`login_whenUserNotFound`/`login_whenPasswordIncorrect`：更新构造参数即可，异常路径不触达 tokenProvider。

- [ ] **Step 4: 跑 UserApplicationTest**

Run:
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home PATH=/opt/homebrew/opt/maven/bin:$PATH mvn -q -pl anitrack-application -am test -Dtest=UserApplicationTest
```
Expected: PASS

---

## Task 9: UserController 移除 JwtTokenProvider + HttpConverter 命名修正

**Files:**
- Modify: `anitrack-starter/src/main/java/com/anitrack/starter/controller/UserController.java`
- Modify: `anitrack-starter/src/main/java/com/anitrack/starter/converter/HttpConverter.java`（`toLoginResponse` 改签名 + `req2BO` 重命名）
- Modify: `anitrack-starter/src/test/java/com/anitrack/starter/controller/UserControllerTest.java`

**Interfaces:**
- Consumes: Task 8 的 `LoginBO`、`UserApplication.login -> LoginBO`

- [ ] **Step 1: 改 HttpConverter 的 toLoginResponse 签名 + req2BO 重命名**

Modify `HttpConverter.java`：
- `toLoginResponse(String token, UserBO bo)` 改为 `toLoginResponse(LoginBO bo)`：
```java
public LoginResponse toLoginResponse(LoginBO bo) {
    return LoginResponse.builder()
        .token(bo.getToken())
        .userInfo(bo2Response(bo.getUser()))
        .build();
}
```
新增 import `com.anitrack.application.model.LoginBO`。

- `req2BO(UserRegisterReq)` 重命名为 `userRegisterReq2BO`，`req2BO(UserLoginReq)` 重命名为 `userLoginReq2BO`（消除泛化重载，符合 `xxx2Yyy`）。

- [ ] **Step 2: 改 UserController**

Modify `UserController.java`：
- 移除 import `com.anitrack.infra.auth.JwtTokenProvider` 和字段 `private final JwtTokenProvider jwtTokenProvider;`
- `register` 中 `httpConverter.req2BO(req)` 改为 `httpConverter.userRegisterReq2BO(req)`
- `login` 改为：
```java
@PostMapping("/login")
public ResponseResult<LoginResponse> login(@Valid @RequestBody UserLoginReq req) {
    LoginBO loginBO = userApplication.login(httpConverter.userLoginReq2BO(req));
    return ResponseResult.success(httpConverter.toLoginResponse(loginBO));
}
```
新增 import `com.anitrack.application.model.LoginBO`。

- [ ] **Step 3: 更新 UserControllerTest**

Modify `UserControllerTest.java`：
- 移除 `@MockBean private JwtTokenProvider mockJwtTokenProvider;` 和 import
- 新增 `@MockBean private com.anitrack.application.model.LoginBO` 相关 mock（实际不需要 MockBean LoginBO，它是 POJO）
- `postRegister_*` 测试：`mockHttpConverter.req2BO(any(UserRegisterReq.class))` 改为 `mockHttpConverter.userRegisterReq2BO(any(UserRegisterReq.class))`
- `postLogin_whenRequestIsValid_shouldReturnTokenAndUserInfo`：
```java
UserBO userBO = UserBO.builder().id(1L).username("alice").nickname("Alice").role(UserRole.USER).build();
LoginBO loginBO = LoginBO.builder().user(userBO).token("mock-token").build();
when(mockHttpConverter.userLoginReq2BO(any(UserLoginReq.class))).thenCallRealMethod();
when(mockUserApplication.login(any())).thenReturn(loginBO);
when(mockHttpConverter.bo2Response(userBO)).thenCallRealMethod();
when(mockHttpConverter.toLoginResponse(loginBO)).thenCallRealMethod();

mockMvc.perform(post("/api/user/login")...)
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.status").value(1))
    .andExpect(jsonPath("$.data.token").value("mock-token"))
    .andExpect(jsonPath("$.data.userInfo.username").value("alice"));

verify(mockUserApplication, times(1)).login(any());
```
新增 import `com.anitrack.application.model.LoginBO`。

- `postLogin_whenCredentialsInvalid_shouldReturnBusinessError`：移除 `verify(mockJwtTokenProvider, never()).generateToken(any());`（JwtTokenProvider 已不在 Controller），改为 `verify(mockUserApplication, times(1)).login(any());` 已在异常路径触发。`req2BO` 引用改为 `userLoginReq2BO`。

- [ ] **Step 4: 跑 UserControllerTest**

Run:
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home PATH=/opt/homebrew/opt/maven/bin:$PATH mvn -q -pl anitrack-starter -am test -Dtest=UserControllerTest
```
Expected: PASS

> 若 `@WebMvcTest(UserController.class)` 因移除 JwtTokenProvider 导致 `JwtAuthInterceptor` 缺 Bean 报错：检查 `WebMvcConfig` 是否注册了拦截器。`JwtAuthInterceptor` 仍依赖 `JwtTokenProvider`（infra），`@WebMvcTest` 切片需 `@MockBean JwtTokenProvider` 供拦截器构造。**保留 `@MockBean JwtTokenProvider`**（仅供拦截器切片用，不在 Controller 业务逻辑中）。重新评估：`UserControllerTest` 的 `@MockBean JwtTokenProvider` 不能移除——`WebMvcConfig` 注册的 `JwtAuthInterceptor` 依赖它。保留该 MockBean，但 Controller 类本身不再注入。

**修正 Step 3：** 不要移除 `@MockBean private JwtTokenProvider mockJwtTokenProvider;`（拦截器需要）。只移除 Controller 字段。测试中 `postLogin_whenRequestIsValid` 不再 mock `mockJwtTokenProvider.generateToken`（因为 Controller 不调了），改 mock `mockUserApplication.login` 返回 `LoginBO`。`postLogin_whenCredentialsInvalid` 的 `verify(mockJwtTokenProvider, never()).generateToken(any())` 删除（断言已无意义，token 在应用层签发，Controller 不触达），可改为 `verify(mockUserApplication, times(1)).login(any())`。

- [ ] **Step 5: 跑 starter 全量测试**

Run:
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home PATH=/opt/homebrew/opt/maven/bin:$PATH mvn -q -pl anitrack-starter -am test
```
Expected: 全过

- [ ] **Step 6: 全量构建回归**

Run:
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home PATH=/opt/homebrew/opt/maven/bin:$PATH mvn -q clean test
```
Expected: 5 模块全过，0 失败

- [ ] **Step 7: Commit C**

```bash
git add anitrack-domain/src/main/java/com/anitrack/domain/user/service/TokenProvider.java anitrack-infrastructure/src/main/java/com/anitrack/infra/auth/JwtTokenProvider.java anitrack-application/ anitrack-starter/
git commit -m "$(cat <<'EOF'
refactor(auth): JWT 签发下沉应用层，Controller 不再注入 JwtTokenProvider

domain 新增 TokenProvider 接口，JwtTokenProvider 实现之；
UserApplication.login 返回 LoginBO(含 token)，依赖 TokenProvider 接口而非 infra 实现类；
UserController 移除 JwtTokenProvider 依赖；HttpConverter.req2BO 重命名为 userRegisterReq2BO/userLoginReq2BO。

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

## 验证清单

- [ ] `mvn clean test` 5 模块全过
- [ ] 4 个 Application 无手写 `toBO`，均注入 MapStruct Converter
- [ ] `WatchlistItemResponse.status` / `WatchlistItemViewResponse.status` 为 `EnumVO`，JSON 输出 `{code,name}`
- [ ] `LocalDateTime` 响应字段输出 ISO-8601 字符串
- [ ] `UserController` 无 `JwtTokenProvider` 字段，`UserApplication.login` 返回 `LoginBO`
- [ ] `application` 模块无 `import com.anitrack.infra.*`（依赖方向合规）
