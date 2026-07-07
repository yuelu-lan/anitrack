# anitrack 前端设计

## 背景与目标

anitrack 后端（Spring Boot 3 + DDD）已完成 4 个上下文的接口：用户注册登录、番剧目录（Bangumi ACL）、追番管理（状态机+进度）、评价（评分/评论），共 14 个 REST 接口。目前没有任何前端，需要新建一个前端项目，作为后端功能的操作界面。

**定位**：个人练习项目的配套前端，怎么简单怎么做，覆盖全部已实现接口，不追求生产级的视觉/交互打磨。

## 范围

**包含**：
- 注册 / 登录
- 番剧搜索（调用 Bangumi API 实时搜索）/ 番剧详情
- 加入追番 / 变更追番状态 / 更新观看进度 / 我的追番列表（按状态筛选）
- 新增评价 / 修改评价 / 查看某番剧的评价列表（分页）/ 我的评价列表

**不包含（YAGNI）**：
- 头像上传（后端无对应接口，`avatarUrl` 只读展示）
- 前端侧的状态流转合法性校验（交给后端拒绝 + 报错提示）
- 移动端适配、国际化、暗色主题
- 自动化测试（手动验证即可）

## 技术栈

| 分类 | 选型 |
| --- | --- |
| 框架 | UmiJS Max（`@umijs/max`），内置 request / model / access / layout |
| 语言 | TypeScript |
| UI 组件库 | Ant Design 5（Umi Max 自带插件，不引入 pro-components） |
| 包管理 | npm |
| 位置 | 仓库根目录新建 `webui/`，与 `anitrack-starter` 等同级，完全独立的 npm 工程 |

## 后端 API 契约（前端据此实现，不再重复调研）

**统一响应包装** `{ status: 1|0, message: string|null, data: T|null }`；`status===1` 成功，`status===0` 业务失败（HTTP 状态码仍是 200，message 是错误文案）。参数校验失败（如必填缺失）返回 HTTP 400，body 结构相同。认证失败返回 HTTP 401，无 body。

**认证**：请求头 `Authorization: Bearer <token>`，除 `/api/user/register`、`/api/user/login` 外全部接口必须携带。

**接口清单**（均为 POST + JSON body）：

| 接口 | 请求体 | 响应体 |
| --- | --- | --- |
| `/api/user/register` | `{username, password, nickname}` | `{id, username, nickname, avatarUrl}` |
| `/api/user/login` | `{username, password}` | `{token, userInfo: {id, username, nickname, avatarUrl}}` |
| `/api/anime/search` | `{keyword}` | `Array<{id, bangumiId, titleCn, titleOriginal, coverUrl, totalEpisodes, airDate, summary}>` |
| `/api/anime/detail` | `{animeId}` | 同上单个对象 |
| `/api/watchlist/add` | `{animeId}` | `{id, animeId, status, currentEpisode, updateTime}` |
| `/api/watchlist/change_status` | `{animeId, status}` | 同上 |
| `/api/watchlist/update_progress` | `{animeId, episode}` | 同上 |
| `/api/watchlist/detail` | `{animeId}` | 同上 |
| `/api/watchlist/list` | `{status?}`（不传返回全部，无分页） | `Array<{..., animeTitleCn, animeTitleOriginal, animeCoverUrl}>` |
| `/api/review/add` | `{animeId, score(1-10), content?}` | `{id, animeId, score, content, createTime}` |
| `/api/review/update` | `{animeId, score, content?}` | 同上 |
| `/api/review/detail` | `{animeId}` | 同上 |
| `/api/review/list_by_anime` | `{animeId, page?(默认1), pageSize?(默认10, 1-100)}` | `{pageNum, pageSize, total, list: Array<{id, userId, userNickname, userAvatarUrl, score, content, createTime}>}` |
| `/api/review/my_list` | 无 | `Array<{id, animeId, animeTitleCn, animeTitleOriginal, animeCoverUrl, score, content, createTime}>` |

**`WatchStatus` 枚举**（JSON 为字符串）：`WANT_TO_WATCH`(想看) / `WATCHING`(在看) / `WATCHED`(看完) / `DROPPED`(弃番)。

**其他**：后端监听 `8080`，未配置 CORS，本地开发需靠前端 dev server 代理。

## 目录结构

```
webui/
├── .umirc.ts                 # 路由、代理、antd 插件配置
├── src/
│   ├── pages/
│   │   ├── Login/index.tsx
│   │   ├── Register/index.tsx
│   │   ├── AnimeSearch/index.tsx      # 默认首页
│   │   ├── AnimeDetail/index.tsx      # 路由 /anime/:animeId
│   │   ├── Watchlist/index.tsx
│   │   └── MyReviews/index.tsx
│   ├── models/
│   │   └── currentUser.ts             # 登录用户信息（umi model）
│   ├── services/
│   │   ├── user.ts
│   │   ├── anime.ts
│   │   ├── watchlist.ts
│   │   └── review.ts
│   ├── types/
│   │   ├── user.ts
│   │   ├── anime.ts
│   │   ├── watchlist.ts
│   │   └── review.ts
│   ├── layouts/
│   │   └── index.tsx                  # 侧边栏主布局 + 登录守卫
│   └── app.ts                         # request 运行时配置（token 注入、统一错误处理）
├── package.json
└── tsconfig.json
```

## 请求层设计

`.umirc.ts` 配置：

```ts
proxy: {
  '/api': { target: 'http://localhost:8080', changeOrigin: true },
}
```

`app.ts` 的 `request` 运行时配置：

- `requestInterceptors`：从 `localStorage.getItem('token')` 读取 token，非空时附加 `Authorization: Bearer <token>` 请求头
- `responseInterceptors` / `errorConfig`：
  - HTTP 层面 401 → 清空 `localStorage` 中的 token/userInfo，跳转 `/login`
  - HTTP 200 但 `data.status === 0` → `message.error(data.message)`，并 reject，页面侧 `useRequest` 的 catch/`onError` 里不再重复弹提示
  - HTTP 200 且 `data.status === 1` → 直接返回 `data.data` 作为业务数据（简化页面侧取值，不用每处都写 `res.data.data`）

**例外**：`/api/watchlist/detail`、`/api/review/detail` 在"未追番"/"未评价"时后端是按业务异常返回的（`WatchlistItemNotFoundException`/`ReviewNotFoundException`，即 `status:0`），但这两种情况在前端语义上是正常状态（表示"可以加入追番"/"可以写评价"），不是错误。这两处调用要在请求层面加 `skipErrorHandler` 选项跳过全局的 `message.error`，由页面自己把这个失败结果解释为"不存在"，展示对应的按钮，而不是弹错误提示。

## 状态管理

只有一个全局 model：`currentUser`（`{id, username, nickname, avatarUrl} | null`）。

- 初始化：从 `localStorage` 恢复（若有 `userInfo` 则视为已登录）
- 登录/注册成功：写入 `localStorage` 的 `token` + `userInfo`，更新 model
- 退出登录：清空两者，跳转 `/login`

其余数据（搜索结果、追番列表、评价列表等）均为页面级 `useRequest`，不做全局缓存，符合"简单优先"。

## 路由与页面设计

### 布局与守卫

`src/layouts/index.tsx`：无登录态时（`currentUser` model 为空）访问除 `/login`、`/register` 外的任何路由，重定向到 `/login`。已登录时渲染侧边栏（菜单：番剧搜索 / 我的追番 / 我的评价 + 顶部显示昵称与退出按钮）+ `<Outlet />`。

### `/login`、`/register`

独立页面（不带侧边栏），普通 antd `Form`。登录成功写入登录态后跳转 `/anime/search`；注册成功后跳转 `/login` 并提示。

### `/anime/search`（默认首页）

顶部 `Input.Search`，回车/点击触发 `/api/anime/search`。结果用 `Card` 网格展示（封面图 `coverUrl` + `titleCn` + `totalEpisodes`），点击卡片跳转 `/anime/:animeId`。空状态用 antd `Empty`。

### `/anime/:animeId`

- 顶部展示番剧详情（`/api/anime/detail`）：封面、中文名/原名、集数、放送日期、简介
- 追番状态区块：先查 `/api/watchlist/detail`（未追番时该请求以业务异常方式失败，见"请求层设计"的例外说明，前端将其解释为"未追番"而非报错）；未追番显示"加入追番"按钮（调 `/api/watchlist/add`）；已追番显示状态 `Tag` + `Select`（四选一，调 `change_status`）+ 进度 `InputNumber`（调 `update_progress`）
- 评价列表区块：`Table` 展示 `/api/review/list_by_anime` 分页结果（评论人昵称/头像/评分/内容/时间），`pagination` 走 antd Table 自带分页 + `useRequest` 的 `pagination` 选项
- 若追番状态为 `WATCHED`：显示"写评价"按钮，打开 Modal 表单（`Rate` count=10 + `TextArea`）；若已有本人评价（`/api/review/detail`）则预填并走 `update`，否则走 `add`

### `/watchlist`

顶部 `Tabs`（全部/想看/在看/看完/弃番）切换调用 `/api/watchlist/list` 的 `status` 参数。列表用 `Table` 或 `List`，展示封面+标题+状态 `Tag`+进度，行内可变更状态（`Select`）/ 改进度（`InputNumber`），点击标题跳转番剧详情。

### `/reviews`（我的评价）

`/api/review/my_list` 无分页，直接全部渲染为 `List`（番剧封面+标题+评分+内容+时间），点击"编辑"打开 Modal 复用 `AnimeDetail` 里的评价表单组件，提交走 `update`。

## 错误处理

统一由 `app.ts` 的 request 错误处理消化（见"请求层设计"），页面侧只需处理 `loading` 状态，不需要每处手写 `try/catch` 弹提示。

## 手动验证计划

不写自动化测试，实现完成后手动走一遍：注册 → 登录 → 搜索番剧 → 加入追番 → 变更状态到 `WATCHED` → 写评价 → 我的追番列表筛选 → 我的评价列表编辑 → 退出登录后访问受限路由被重定向。
