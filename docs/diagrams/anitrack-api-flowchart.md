# anitrack 业务流程图

与 [anitrack-api-sequence.md](./anitrack-api-sequence.md) 互补:时序图展示接口的调用链路,本文档展示每个接口内部的业务决策/校验分支。

**通用约定**:除 `@Valid` 参数校验失败(如 `@NotBlank`)统一返回 **HTTP 400** 外,其余业务失败分支的 HTTP 状态码均为 **200**,靠响应体 `status=0` + `message` 区分成败(见各图终止节点标注)。

## User 用户上下文

### 注册 `POST /api/user/register`

```mermaid
flowchart TD
    Start([请求 register]) --> Valid{"username/password/nickname\n是否为空(@NotBlank)"}
    Valid -- 任一为空 --> Err400["400 参数校验失败"]
    Valid -- 均非空 --> Exists{"userRepo.existsByUsername\n用户名已存在?"}
    Exists -- 是 --> Err1["200 status=0\nUSERNAME_ALREADY_EXISTS(40001)\n用户名已存在"]
    Exists -- 否 --> Encode["passwordEncoder.encode(password)"]
    Encode --> Create["User.register(username, hash, nickname)\nrole=USER"]
    Create --> Save["userRepo.save(user)"]
    Save --> Ok["200 status=1 注册成功"]
```

### 登录 `POST /api/user/login`

```mermaid
flowchart TD
    Start([请求 login]) --> Valid{"username/password\n是否为空(@NotBlank)"}
    Valid -- 任一为空 --> Err400["400 参数校验失败"]
    Valid -- 均非空 --> Get["userRepo.getByUsername(username)"]
    Get --> Check{"user不存在 或\n密码不匹配?"}
    Check -- 是(两种情况合并) --> Err1["200 status=0\nLOGIN_FAILED(40002)\n用户名或密码错误"]
    Check -- 否 --> Gen["jwtTokenProvider.generateToken(userId)"]
    Gen --> Ok["200 status=1 返回token"]
```

> 注:用户不存在与密码错误对外表现完全一致(同一错误码/文案),避免用户名枚举。`User` 模型无账号状态字段,当前代码未实现锁定/禁用校验。

## Anime 番剧上下文

### 搜索并落库 `POST /api/anime/search`

```mermaid
flowchart TD
    Start([请求 search]) --> Call["bangumiGateway.search(keyword)"]
    Call --> Fail{"调用失败?\n(BangumiApiException)"}
    Fail -- 是 --> Log["log.error记录异常"]
    Log --> Err1["200 status=0\nBANGUMI_SERVICE_UNAVAILABLE(40101)\n番剧信息服务暂时不可用"]
    Fail -- 否 --> Empty{"结果为空列表?"}
    Empty -- 是 --> Ok1["200 status=1 空列表(非错误)"]
    Empty -- 否 --> Upsert["逐条 animeRepo.upsert(anime)\n落库/更新"]
    Upsert --> Ok2["200 status=1 返回番剧列表"]
```

### 查询详情 `POST /api/anime/detail`

```mermaid
flowchart TD
    Start([请求 detail]) --> Get["animeRepo.getById(animeId)"]
    Get --> Exists{"番剧存在?"}
    Exists -- 否 --> Err1["200 status=0\nANIME_NOT_FOUND(40102)\n番剧不存在"]
    Exists -- 是 --> Ok["200 status=1 返回番剧详情"]
```

> 注:仅查本地库,不存在时不会回退调用 Bangumi 网关兜底。

## Watchlist 追番上下文

### 加入追番 `POST /api/watchlist/add`

```mermaid
flowchart TD
    Start([请求 add]) --> AnimeCheck["animeRepo.getById(animeId)"]
    AnimeCheck --> AnimeExists{"番剧存在?"}
    AnimeExists -- 否 --> Err1["200 status=0\nANIME_NOT_FOUND(40102)\n番剧不存在"]
    AnimeExists -- 是 --> DupCheck["watchlistRepo.getByUserAndAnime(userId, animeId)"]
    DupCheck --> Dup{"记录已存在?"}
    Dup -- 是 --> Err2["200 status=0\nWATCHLIST_ITEM_ALREADY_EXISTS(40201)\n该番剧已在追番列表中"]
    Dup -- 否 --> Create["WatchlistItem.create(userId, animeId)\nstatus=WANT_TO_WATCH, currentEpisode=0"]
    Create --> Add["watchlistRepo.add(item)"]
    Add --> Ok["200 status=1 加入成功"]
```

> 注:校验顺序是先查番剧存在性,再查重复追番。

### 变更追看状态 `POST /api/watchlist/change_status`

```mermaid
flowchart TD
    Start([请求 change_status]) --> Get["watchlistRepo.getByUserAndAnime(userId, animeId)"]
    Get --> Exists{"记录存在?"}
    Exists -- 否 --> Err1["200 status=0\nWATCHLIST_ITEM_NOT_FOUND(40202)\n追番记录不存在"]
    Exists -- 是 --> Legal{"newStatus 在当前状态的\n合法转移集合内?\n(见下方状态图)"}
    Legal -- 否 --> Err2["200 status=0\nILLEGAL_WATCH_STATUS_TRANSITION(40203)\n非法的追番状态转移"]
    Legal -- 是 --> Update["item.status = newStatus\nwatchlistRepo.update(item)"]
    Update --> Publish["eventPublisher.publishEvent\n(WatchStatusChangedEvent)"]
    Publish --> Ok["200 status=1 变更成功"]
```

#### 追番状态机(合法转移关系)

```mermaid
stateDiagram-v2
    [*] --> WANT_TO_WATCH: create()
    WANT_TO_WATCH --> WATCHING
    WATCHING --> WATCHED
    WATCHING --> DROPPED
    DROPPED --> WATCHING
    WATCHED --> [*]: 终态,无出边

    note right of WATCHED
        终态:任何转移(含自环)均非法
        抛 IllegalWatchStatusTransitionException
    end note
```

### 更新观看进度 `POST /api/watchlist/update_progress`

```mermaid
flowchart TD
    Start([请求 update_progress]) --> Get["watchlistRepo.getByUserAndAnime(userId, animeId)"]
    Get --> Exists{"记录存在?"}
    Exists -- 否 --> Err1["200 status=0\nWATCHLIST_ITEM_NOT_FOUND(40202)\n追番记录不存在"]
    Exists -- 是 --> AnimeGet["animeRepo.getById(animeId)"]
    AnimeGet --> AnimeExists{"番剧存在?"}
    AnimeExists -- 否 --> Err2["200 status=0\nANIME_NOT_FOUND(40102)\n番剧不存在"]
    AnimeExists -- 是 --> R1{"status != WATCHING?"}
    R1 -- 是 --> Err3["200 status=0\nILLEGAL_WATCH_PROGRESS(40204)\n只有观看中的番剧才能更新进度"]
    R1 -- 否 --> R2{"episode为空 或 episode<=0?"}
    R2 -- 是 --> Err4["200 status=0\nILLEGAL_WATCH_PROGRESS(40204)\n观看进度必须大于0"]
    R2 -- 否 --> R3{"episode < currentEpisode?"}
    R3 -- 是 --> Err5["200 status=0\nILLEGAL_WATCH_PROGRESS(40204)\n观看进度不能倒退"]
    R3 -- 否 --> R4{"totalEpisodes有效(非null且>0)\n且 episode > totalEpisodes?"}
    R4 -- 是 --> Err6["200 status=0\nILLEGAL_WATCH_PROGRESS(40204)\n观看进度不能超过总集数"]
    R4 -- 否 --> Update["item.currentEpisode = episode\nwatchlistRepo.update(item)"]
    Update --> Ok["200 status=1 更新成功"]
```

> 注:与 add 相反,此接口校验顺序是先查追番记录、再查番剧存在性。四条进度校验按代码顺序短路执行,`episode == currentEpisode` 允许通过。

### 查询单条追番记录 `POST /api/watchlist/detail`

```mermaid
flowchart TD
    Start([请求 detail]) --> Get["watchlistRepo.getByUserAndAnime(userId, animeId)"]
    Get --> Exists{"记录存在?"}
    Exists -- 否 --> Err1["200 status=0\nWATCHLIST_ITEM_NOT_FOUND(40202)\n追番记录不存在"]
    Exists -- 是 --> Ok["200 status=1 返回追番详情"]
```

### 查询我的追番列表 `POST /api/watchlist/list`

```mermaid
flowchart TD
    Start([请求 list]) --> Get["watchlistRepo.listByUser(userId, statusFilter)"]
    Get --> AnimeGet["animeRepo.listByIds(animeIds)"]
    AnimeGet --> Assemble["WatchlistAssembler.assemble\n(items, animes) 跨聚合拼装"]
    Assemble --> Ok["200 status=1 返回追番列表\n(可能为空列表,非错误)"]
```
