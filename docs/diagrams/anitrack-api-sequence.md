# anitrack 接口时序图

覆盖当前 3 个 Controller 共 9 个接口的调用链路,按上下文分组。图中省略了全局横切逻辑(`GlobalExceptionHandler` 统一异常处理),仅在鉴权环节体现 `JwtAuthInterceptor`。

## User 用户上下文

### 注册 `POST /api/user/register`

```mermaid
sequenceDiagram
    participant Client
    participant Controller as UserController
    participant App as UserApplication
    participant Repo as UserRepo
    participant Encoder as PasswordEncoder

    Client->>Controller: register(UserRegisterReq)
    Controller->>App: register(req)
    App->>Repo: getByUsername(username)
    Repo-->>App: 不存在
    App->>Encoder: encode(rawPassword)
    Encoder-->>App: encodedPassword
    App->>Repo: add(User)
    Repo-->>App: userId
    App-->>Controller: UserBO
    Controller-->>Client: 200 注册成功
```

### 登录 `POST /api/user/login`

```mermaid
sequenceDiagram
    participant Client
    participant Controller as UserController
    participant App as UserApplication
    participant Repo as UserRepo
    participant Encoder as PasswordEncoder
    participant Jwt as JwtTokenProvider

    Client->>Controller: login(UserLoginReq)
    Controller->>App: login(req)
    App->>Repo: getByUsername(username)
    Repo-->>App: User
    App->>Encoder: matches(rawPassword, encodedPassword)
    Encoder-->>App: true
    App->>Jwt: generateToken(userId)
    Jwt-->>App: token
    App-->>Controller: LoginResultBO(token)
    Controller-->>Client: 200 token
```

## Anime 番剧上下文

### 搜索并落库 `POST /api/anime/search`

```mermaid
sequenceDiagram
    participant Client
    participant Controller as AnimeController
    participant App as AnimeApplication
    participant Gateway as BangumiGateway
    participant Repo as AnimeRepo

    Client->>Controller: search(AnimeSearchReq)
    Controller->>App: searchAnime(keyword)
    App->>Gateway: search(keyword)
    Gateway-->>App: List~AnimeBO~(ACL转换后)
    App->>Repo: upsert(AnimeBO)
    Repo-->>App: 落库结果
    App-->>Controller: List~AnimeBO~
    Controller-->>Client: 200 番剧列表
```

### 查询详情 `POST /api/anime/detail`

```mermaid
sequenceDiagram
    participant Client
    participant Controller as AnimeController
    participant App as AnimeApplication
    participant Repo as AnimeRepo

    Client->>Controller: detail(AnimeDetailReq)
    Controller->>App: getAnimeDetail(animeId)
    App->>Repo: getById(animeId)
    Repo-->>App: AnimeBO
    App-->>Controller: AnimeBO
    Controller-->>Client: 200 番剧详情
```

## Watchlist 追番上下文

以下接口均先经过 `JwtAuthInterceptor` 鉴权,`userId` 取自 `UserContextHolder.getUserId()`。

### 加入追番 `POST /api/watchlist/add`

```mermaid
sequenceDiagram
    participant Client
    participant Interceptor as JwtAuthInterceptor
    participant Controller as WatchlistController
    participant App as WatchlistApplication
    participant DomainSvc as WatchlistDomainService
    participant AnimeRepo
    participant WatchlistRepo
    participant Item as WatchlistItem(聚合根)

    Client->>Interceptor: POST /add + JWT
    Interceptor-->>Controller: 鉴权通过, userId 存入上下文
    Controller->>App: addToWatchlist(userId, animeId)
    App->>DomainSvc: addToWatchlist(userId, animeId)
    DomainSvc->>AnimeRepo: getById(animeId)
    AnimeRepo-->>DomainSvc: Anime 存在
    DomainSvc->>WatchlistRepo: getByUserAndAnime(userId, animeId)
    WatchlistRepo-->>DomainSvc: 不存在(查重通过)
    DomainSvc->>Item: create(userId, animeId)
    Item-->>DomainSvc: WatchlistItem
    DomainSvc->>WatchlistRepo: add(WatchlistItem)
    WatchlistRepo-->>DomainSvc: itemId
    DomainSvc-->>App: WatchlistItem
    App-->>Controller: WatchlistItemBO
    Controller-->>Client: 200 加入成功

    Note over DomainSvc,AnimeRepo: Anime 不存在 → AnimeNotFoundException
    Note over DomainSvc,WatchlistRepo: 已存在 → WatchlistItemAlreadyExistsException
```

### 变更追看状态 `POST /api/watchlist/change_status`

```mermaid
sequenceDiagram
    participant Client
    participant Interceptor as JwtAuthInterceptor
    participant Controller as WatchlistController
    participant App as WatchlistApplication
    participant WatchlistRepo
    participant Item as WatchlistItem(聚合根)
    participant Publisher as ApplicationEventPublisher

    Client->>Interceptor: POST /change_status + JWT
    Interceptor-->>Controller: 鉴权通过
    Controller->>App: changeStatus(userId, animeId, newStatus)
    App->>WatchlistRepo: getByUserAndAnime(userId, animeId)
    WatchlistRepo-->>App: WatchlistItem
    App->>Item: changeStatus(newStatus)
    Note over Item: 内置状态转移表校验,\n非法迁移抛 IllegalWatchStatusTransitionException
    Item-->>App: 状态已变更
    App->>WatchlistRepo: update(WatchlistItem)
    WatchlistRepo-->>App: 更新成功
    App->>Publisher: publishEvent(WatchStatusChangedEvent)
    Note over Publisher: 当前仓库无任何监听器消费该事件
    App-->>Controller: WatchlistItemBO
    Controller-->>Client: 200 变更成功
```

### 更新观看进度 `POST /api/watchlist/update_progress`

```mermaid
sequenceDiagram
    participant Client
    participant Interceptor as JwtAuthInterceptor
    participant Controller as WatchlistController
    participant App as WatchlistApplication
    participant DomainSvc as WatchlistDomainService
    participant WatchlistRepo
    participant AnimeRepo
    participant Item as WatchlistItem(聚合根)

    Client->>Interceptor: POST /update_progress + JWT
    Interceptor-->>Controller: 鉴权通过
    Controller->>App: updateProgress(userId, animeId, progress)
    App->>DomainSvc: updateProgress(userId, animeId, progress)
    DomainSvc->>WatchlistRepo: getByUserAndAnime(userId, animeId)
    WatchlistRepo-->>DomainSvc: WatchlistItem
    DomainSvc->>AnimeRepo: getById(animeId)
    AnimeRepo-->>DomainSvc: totalEpisodes
    DomainSvc->>Item: updateProgress(progress, totalEpisodes)
    Note over Item: 四条校验规则,\n违反抛 IllegalWatchProgressException
    Item-->>DomainSvc: 进度已更新
    DomainSvc->>WatchlistRepo: update(WatchlistItem)
    WatchlistRepo-->>DomainSvc: 更新成功
    DomainSvc-->>App: WatchlistItem
    App-->>Controller: WatchlistItemBO
    Controller-->>Client: 200 更新成功
```

### 查询单条追番记录 `POST /api/watchlist/detail`

```mermaid
sequenceDiagram
    participant Client
    participant Interceptor as JwtAuthInterceptor
    participant Controller as WatchlistController
    participant App as WatchlistApplication
    participant WatchlistRepo

    Client->>Interceptor: POST /detail + JWT
    Interceptor-->>Controller: 鉴权通过
    Controller->>App: getWatchlistItem(userId, animeId)
    App->>WatchlistRepo: getByUserAndAnime(userId, animeId)
    alt 记录存在
        WatchlistRepo-->>App: WatchlistItem
        App-->>Controller: WatchlistItemBO
        Controller-->>Client: 200 追番详情
    else 记录不存在
        WatchlistRepo-->>App: 空
        App-->>Controller: 抛出 WATCHLIST_ITEM_NOT_FOUND
        Controller-->>Client: 404/业务错误码
    end
```

### 查询我的追番列表 `POST /api/watchlist/list`

```mermaid
sequenceDiagram
    participant Client
    participant Interceptor as JwtAuthInterceptor
    participant Controller as WatchlistController
    participant App as WatchlistApplication
    participant WatchlistRepo
    participant AnimeRepo
    participant Assembler as WatchlistAssembler

    Client->>Interceptor: POST /list + JWT
    Interceptor-->>Controller: 鉴权通过
    Controller->>App: listMyWatchlist(userId, statusFilter)
    App->>WatchlistRepo: listByUser(userId, statusFilter)
    WatchlistRepo-->>App: List~WatchlistItem~
    App->>AnimeRepo: listByIds(animeIds)
    AnimeRepo-->>App: List~AnimeBO~
    App->>Assembler: assemble(items, animes)
    Assembler-->>App: List~WatchlistItemViewBO~
    App-->>Controller: List~WatchlistItemViewBO~
    Controller-->>Client: 200 追番列表
```
