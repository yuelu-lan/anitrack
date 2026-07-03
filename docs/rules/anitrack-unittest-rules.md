# anitrack 单元测试规范

## 概述

本规范用于指导生成符合最佳实践的 Java JUnit5 单元测试，重点强调生命周期管理、Mock/Stub 区分、断言规范、异常测试与数据驱动测试。

- 使用 JUnit5 + Mockito + AssertJ
- 当被测方法位于 Controller 层时，需分析完整调用链路，为链路上所有方法生成单元测试
- 生成的单测必须能用 Maven 命令运行成功；不确定的类/方法签名不得凭空编造，需先查阅源码确认
- 非必要场景下不修改 Maven 依赖

## 1. 结构与生命周期规则

### 1.1 文件与类结构

- 测试类名：被测类名 + `Test`（如 `WatchlistDomainServiceTest`）
- 测试类放在与被测类相同的包结构下，位于 `src/test/java` 目录

### 1.2 生命周期方法

| 注解 | 用途 |
| --- | --- |
| `@BeforeAll`（静态方法） | 整个测试类执行前运行一次，初始化昂贵、不可变的共享资源 |
| `@AfterAll`（静态方法） | 整个测试类执行后运行一次，释放共享资源 |
| `@BeforeEach` | 每个测试方法执行前运行，创建/重置被测对象（SUT）和 Mock 对象 |
| `@AfterEach` | 每个测试方法执行后运行，清理临时资源 |

### 1.3 测试方法结构

使用注释分段标记测试的三个阶段（准备、执行、断言）：

```java
@Test
void changeStatus_whenTransitionIsValid_shouldUpdateStatusAndReturnEvent() {
    // given
    WatchlistItem item = WatchlistItem.builder().status(WatchStatus.WANT_TO_WATCH).build();

    // when
    WatchStatusChangedEvent event = item.changeStatus(WatchStatus.WATCHING);

    // then
    assertThat(item.getStatus()).isEqualTo(WatchStatus.WATCHING);
    assertThat(event.newStatus()).isEqualTo(WatchStatus.WATCHING);
}
```

## 2. 命名与代码风格规范

### 2.1 类与方法命名

- 测试类使用 UpperCamelCase（如 `WatchlistDomainServiceTest`）
- 测试方法必须使用描述性命名，格式：`方法名_场景描述_预期行为`，如 `changeStatus_whenTransitionIsInvalid_shouldThrowException`

### 2.2 变量命名

- 被测对象（System Under Test）推荐命名为 `sut`
- Mock 对象使用 `mock` 前缀（如 `mockWatchlistRepo`）
- 其他变量使用清晰的 lowerCamelCase

## 3. Mocking 与 Stubbing 规则

### 3.1 使用 Mockito

优先使用 Mockito 的 `@Mock` 注解 + `@ExtendWith(MockitoExtension.class)`：

```java
@ExtendWith(MockitoExtension.class)
class WatchlistDomainServiceTest {
    @Mock
    private WatchlistRepo mockWatchlistRepo;

    @Mock
    private AnimeRepo mockAnimeRepo;

    @InjectMocks
    private WatchlistDomainService sut;
}
```

### 3.2 区分 Stub 行为与 Mock 交互验证

- **提供固定返回值**（不关心是否被调用）：`when(mockWatchlistRepo.getByUserAndAnime(any(), any())).thenReturn(item)`
- **验证交互行为**（是否被调用、调用次数、参数）：`verify(mockWatchlistRepo, times(1)).save(item)`

### 3.3 交互验证

必须明确指定交互次数：`times(1)`、`atLeastOnce()`、`never()`，避免省略次数。使用 `any()` 匹配任意参数，能用具体值时优先使用具体值或 `ArgumentCaptor`。

### 3.4 避免过度 Mocking

- 不 Mock 值对象/DTO，直接创建实例
- 不 Mock 易于实例化的具体类，只在需要隔离外部系统（DB、API）或验证交互时才 Mock
- 不 Mock private 方法（测试实现细节，应通过公共 API 测试行为）
- 不 Mock final 方法/类（需要创建真实实例）

## 4. 断言规则

### 4.1 使用 AssertJ 链式断言

严禁使用 JUnit 内置的 `Assertions.assertEquals()`，统一使用 AssertJ 的 `assertThat()`：

```java
// Bad
assertEquals(WatchStatus.WATCHING, item.getStatus());

// Good
assertThat(item.getStatus()).isEqualTo(WatchStatus.WATCHING);
```

### 4.2 集合断言

```java
assertThat(result).hasSize(2);
assertThat(result).containsExactly("a", "b");
assertThat(result).extracting(WatchlistItem::getStatus).containsOnly(WatchStatus.WATCHING);
```

## 5. 异常测试规则

使用 `assertThatThrownBy` 验证异常类型与消息：

```java
@Test
void changeStatus_whenTransitionIsInvalid_shouldThrowException() {
    WatchlistItem item = WatchlistItem.builder().status(WatchStatus.WATCHED).build();

    assertThatThrownBy(() -> item.changeStatus(WatchStatus.WANT_TO_WATCH))
        .isInstanceOf(IllegalWatchStatusTransitionException.class)
        .hasMessageContaining("WATCHED");
}
```

## 6. 数据驱动测试规则

使用 `@ParameterizedTest` + `@CsvSource`（简单场景）或 `@MethodSource`（复杂对象场景）实现数据驱动测试：

```java
@ParameterizedTest(name = "{0} -> {1} should be allowed")
@CsvSource({
    "WANT_TO_WATCH, WATCHING",
    "WANT_TO_WATCH, DROPPED",
    "WATCHING, WATCHED",
    "WATCHING, DROPPED"
})
void changeStatus_whenTransitionIsLegal_shouldSucceed(WatchStatus from, WatchStatus to) {
    WatchlistItem item = WatchlistItem.builder().status(from).build();

    item.changeStatus(to);

    assertThat(item.getStatus()).isEqualTo(to);
}
```

## 7. 测试覆盖与最佳实践

- **行为驱动覆盖**：测试重点是业务行为，覆盖所有成功路径、可预见的失败路径（如非法状态转移）、边界条件
- **测试单一关注点**：每个测试方法只测试一个独立场景
- **测试独立性**：测试之间不得共享可变状态，使用 `@BeforeEach` 确保每个测试在干净环境中运行
- **可读性优先**：使用注释分段与空行组织复杂的测试准备逻辑

## 8. 单测位置

测试类放在 `src/test/java` 目录下，与被测类包结构对应。

## 9. 其他

- 不需要单独生成测试报告
- 生成完单测之后需要能运行成功
- 生成的单测都需要使用断言
- 如果已经存在单元测试，则不需要再次生成

## 示例代码

```java
class WatchlistItemTest {

    @Test
    void changeStatus_whenTransitionIsValid_shouldUpdateStatus() {
        // given
        WatchlistItem item = WatchlistItem.builder()
            .status(WatchStatus.WANT_TO_WATCH)
            .build();

        // when
        WatchStatusChangedEvent event = item.changeStatus(WatchStatus.WATCHING);

        // then
        assertThat(item.getStatus()).isEqualTo(WatchStatus.WATCHING);
        assertThat(event.oldStatus()).isEqualTo(WatchStatus.WANT_TO_WATCH);
        assertThat(event.newStatus()).isEqualTo(WatchStatus.WATCHING);
    }

    @Test
    void changeStatus_whenTransitionIsInvalid_shouldThrowException() {
        // given
        WatchlistItem item = WatchlistItem.builder()
            .status(WatchStatus.WATCHED)
            .build();

        // when & then
        assertThatThrownBy(() -> item.changeStatus(WatchStatus.WANT_TO_WATCH))
            .isInstanceOf(IllegalWatchStatusTransitionException.class);
    }

    @ParameterizedTest
    @EnumSource(value = WatchStatus.class, names = {"WANT_TO_WATCH", "WATCHED", "DROPPED"})
    void updateProgress_whenStatusIsNotWatching_shouldThrowException(WatchStatus status) {
        // given
        WatchlistItem item = WatchlistItem.builder().status(status).build();

        // when & then
        assertThatThrownBy(() -> item.updateProgress(5, 12))
            .isInstanceOf(IllegalStateException.class);
    }
}
```
