# anitrack Web层集成测试规范

## 概述

本规范用于指导 Spring Boot Web 层（Controller）的集成测试编写，重点关注 HTTP 请求处理流程、参数验证、异常处理。使用 `@WebMvcTest` + MockMvc + `@MockBean`。

## 1. 测试框架选择

### 1.1 推荐使用 @WebMvcTest

- 只加载 Web 层相关的 Bean，启动速度快
- 自动配置 MockMvc
- 支持参数验证测试、HTTP状态码与响应头验证

### 1.2 与单元测试的区别

| 测试类型 | 适用场景 | 验证注解 | Spring上下文 | 测试重点 |
| --- | --- | --- | --- | --- |
| 单元测试 | 业务逻辑 | 不生效 | 不启动 | Controller/领域/应用层内部逻辑 |
| 集成测试 | HTTP请求处理 | 生效 | 启动Web层 | 完整请求链路 |

## 2. 测试类结构规范

### 2.1 基本结构

```java
package com.anitrack.starter.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WatchlistController.class)
class WatchlistControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WatchlistApplication mockWatchlistApplication;

    @MockBean
    private HttpConverter mockHttpConverter;
}
```

### 2.2 命名规范

- 测试类名：`{Controller名}Test`
- 测试方法名：`{HTTP方法}{路径}_{场景描述}_{预期结果}`，如 `postAdd_whenAnimeIdIsNull_shouldReturn400`

## 3. 参数验证测试

### 3.1 @Valid 和 @NotNull 验证测试

```java
@Test
void postAdd_whenRequestBodyIsNull_shouldReturnBadRequest() throws Exception {
    mockMvc.perform(post("/api/watchlist/add")
            .contentType(MediaType.APPLICATION_JSON)
            .content("null"))
        .andExpect(status().isBadRequest());
}

@Test
void postAdd_whenRequestBodyIsEmptyObject_shouldReturnBadRequest() throws Exception {
    mockMvc.perform(post("/api/watchlist/add")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest());
}
```

### 3.2 字段级验证测试（数据驱动）

```java
@ParameterizedTest(name = "animeId={0} should return 400")
@NullSource
@ValueSource(longs = {-1L})
void postAdd_whenAnimeIdIsInvalid_shouldReturnBadRequest(Long animeId) throws Exception {
    Map<String, Object> request = new HashMap<>();
    request.put("animeId", animeId);

    mockMvc.perform(post("/api/watchlist/add")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
}
```

## 4. 成功场景测试

```java
@Test
void postAdd_whenRequestIsValid_shouldReturnOk() throws Exception {
    // given
    Map<String, Object> request = Map.of("animeId", 1L);

    // when & then
    mockMvc.perform(post("/api/watchlist/add")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(1));

    verify(mockWatchlistApplication, times(1)).addToWatchlist(any());
}
```

## 5. 异常处理测试

```java
@Test
void postChangeStatus_whenTransitionInvalid_shouldReturnBusinessError() throws Exception {
    // given
    Map<String, Object> request = Map.of("animeId", 1L, "newStatus", "WANT_TO_WATCH");
    doThrow(new IllegalWatchStatusTransitionException(WatchStatus.WATCHED, WatchStatus.WANT_TO_WATCH))
        .when(mockWatchlistApplication).changeStatus(any(), any(), any());

    // when & then
    mockMvc.perform(post("/api/watchlist/change_status")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(0));
}

@Test
void postAdd_whenSystemExceptionOccurs_shouldReturnInternalServerErrorStatus() throws Exception {
    doThrow(new RuntimeException("数据库连接失败")).when(mockWatchlistApplication).addToWatchlist(any());

    mockMvc.perform(post("/api/watchlist/add")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("animeId", 1L))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(0));
}
```

## 6. Mock策略

- 使用 `@MockBean` Mock 所有依赖的 `XxxApplication`、`HttpConverter`
- 鉴权拦截器（`JwtAuthInterceptor`）在 `@WebMvcTest` 中默认不加载其真实实现，如需测试鉴权行为，使用 `@MockBean` Mock `JwtTokenProvider` 并通过 `@WithMockUser` 或手动设置请求头模拟已登录态

## 7. 测试数据工厂方法

```java
private WatchlistBO createTestWatchlistBO() {
    return WatchlistBO.builder()
        .id(1L)
        .userId(1L)
        .animeId(1L)
        .status(WatchStatus.WATCHING)
        .currentEpisode(3)
        .build();
}
```

## 8. 响应验证

```java
result.andExpect(jsonPath("$.status").value(1))
      .andExpect(jsonPath("$.data.id").value(1))
      .andExpect(jsonPath("$.data.list").isArray())
      .andExpect(jsonPath("$.data.list.length()").value(2));
```

## 9. 最佳实践

### 9.1 测试覆盖原则

1. 参数验证：覆盖所有 `@Valid`、`@NotNull`、`@NotBlank` 等验证注解
2. 成功路径：覆盖所有正常业务流程
3. 异常处理：覆盖应用层异常、领域层异常、系统异常
4. 边界条件：覆盖空值、边界值等特殊情况

### 9.2 注意事项

1. 测试隔离：每个测试方法独立，避免相互影响
2. Web层测试不依赖真实数据库，所有外部依赖均被 Mock
3. 涉及鉴权的接口需 Mock 鉴权组件或显式模拟已登录态
