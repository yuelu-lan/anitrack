# anitrack 前端（anitrack-web）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在仓库根目录新建 `anitrack-web/`，用 UmiJS Max + Ant Design 实现覆盖 anitrack 后端全部 14 个接口的操作界面。

**Architecture:** UmiJS Max 提供路由/请求/全局状态开箱即用能力；`src/app.ts` 统一处理 JWT 注入与后端 `{status,message,data}` 响应包装的错误提示；登录态存于 `localStorage` 并映射到 Umi 内置的 `@@initialState`；路由用一个 `wrappers/auth.tsx` 守卫未登录访问；页面按对应上下文划分（AnimeSearch/AnimeDetail/Watchlist/MyReviews），评价新增/编辑复用同一个 `ReviewFormModal` 组件。

**Tech Stack:** `@umijs/max` 4.6.71、`antd` 5.29.3、`react`/`react-dom` 18.3.1、`typescript` 5.9.3。

## Global Constraints

- 项目位置：仓库根目录下 `anitrack-web/`，独立 npm 工程，不属于 Maven 多模块
- 依赖版本严格按下表锁定：`@umijs/max@4.6.71`、`antd@5.29.3`、`react@18.3.1`、`react-dom@18.3.1`、`typescript@5.9.3`、`@types/react@18.3.31`、`@types/react-dom@18.3.7`
- 后端响应包装固定为 `{ status: 1|0, message: string|null, data: T }`，`status===1` 成功、`status===0` 业务失败（HTTP 200）；HTTP 401 表示未登录/token 失效
- `.umirc.ts` 中 `request.dataField` 必须设为 `''`（空字符串），因为项目在 service 层手动解包 `.data`，不使用 Max 默认的 `dataField:'data'` 二次解包
- `/api/watchlist/detail`、`/api/review/detail` 请求必须带 `skipErrorHandler: true`，因为"未追番/未评价"在后端是业务异常（`status:0`），前端要静默处理成"不存在"而非弹错误提示
- `WatchStatus` 枚举值固定为 `WANT_TO_WATCH`(想看) / `WATCHING`(在看) / `WATCHED`(看完) / `DROPPED`(弃番)，评分固定 1-10 整数
- 登录态持久化 key：`localStorage` 的 `token` 和 `userInfo`（JSON 字符串）
- 后端监听 `http://localhost:8080`，无 CORS 配置，前端必须通过 `.umirc.ts` 的 `proxy` 转发 `/api`
- 本项目不写自动化测试（练习项目，简单优先），每个任务用 `npm run build` 作为编译正确性验证

---

## Task 1: 项目骨架初始化

**Files:**
- Create: `anitrack-web/package.json`
- Create: `anitrack-web/tsconfig.json`
- Create: `anitrack-web/.umirc.ts`
- Create: `anitrack-web/.gitignore`
- Create: `anitrack-web/src/layouts/index.tsx`（占位）
- Create: `anitrack-web/src/wrappers/auth.tsx`（占位）
- Create: `anitrack-web/src/pages/Login/index.tsx`（占位）
- Create: `anitrack-web/src/pages/Register/index.tsx`（占位）
- Create: `anitrack-web/src/pages/AnimeSearch/index.tsx`（占位）
- Create: `anitrack-web/src/pages/AnimeDetail/index.tsx`（占位）
- Create: `anitrack-web/src/pages/Watchlist/index.tsx`（占位）
- Create: `anitrack-web/src/pages/MyReviews/index.tsx`（占位）

**Interfaces:**
- Produces: 完整可跑通的路由骨架 —— `/login`、`/register`、`/`（重定向到 `/anime/search`）、`/anime/search`、`/anime/:animeId`、`/watchlist`、`/reviews`；后续任务只替换占位文件内容，不改路由结构

- [ ] **Step 1: 创建 package.json**

```json
{
  "name": "anitrack-web",
  "version": "1.0.0",
  "private": true,
  "scripts": {
    "dev": "max dev",
    "build": "max build",
    "postinstall": "max setup"
  },
  "dependencies": {
    "@umijs/max": "4.6.71",
    "antd": "5.29.3",
    "react": "18.3.1",
    "react-dom": "18.3.1"
  },
  "devDependencies": {
    "@types/react": "18.3.31",
    "@types/react-dom": "18.3.7",
    "typescript": "5.9.3"
  }
}
```

- [ ] **Step 2: 创建 tsconfig.json**

```json
{
  "extends": "./src/.umi/tsconfig.json"
}
```

- [ ] **Step 3: 创建 .umirc.ts**

```ts
import { defineConfig } from '@umijs/max';

export default defineConfig({
  antd: {},
  model: {},
  initialState: {},
  request: {
    dataField: '',
  },
  npmClient: 'npm',
  routes: [
    { path: '/login', component: 'Login' },
    { path: '/register', component: 'Register' },
    {
      path: '/',
      component: '@/layouts/index',
      wrappers: ['@/wrappers/auth'],
      routes: [
        { path: '/', redirect: '/anime/search' },
        { path: '/anime/search', component: 'AnimeSearch' },
        { path: '/anime/:animeId', component: 'AnimeDetail' },
        { path: '/watchlist', component: 'Watchlist' },
        { path: '/reviews', component: 'MyReviews' },
      ],
    },
  ],
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    },
  },
});
```

- [ ] **Step 4: 创建 .gitignore**

```
node_modules/
dist/
.umi/
.umi-production/
.umi-test/
*.local.ts
.DS_Store
```

- [ ] **Step 5: 创建布局与守卫占位文件**

`src/layouts/index.tsx`：

```tsx
import { Outlet } from '@umijs/max';

export default function MainLayout() {
  return <Outlet />;
}
```

`src/wrappers/auth.tsx`：

```tsx
import { Outlet } from '@umijs/max';

export default function AuthWrapper() {
  return <Outlet />;
}
```

- [ ] **Step 6: 创建 6 个页面占位文件**

`src/pages/Login/index.tsx`：

```tsx
export default function LoginPage() {
  return <div>Login 占位</div>;
}
```

`src/pages/Register/index.tsx`：

```tsx
export default function RegisterPage() {
  return <div>Register 占位</div>;
}
```

`src/pages/AnimeSearch/index.tsx`：

```tsx
export default function AnimeSearchPage() {
  return <div>AnimeSearch 占位</div>;
}
```

`src/pages/AnimeDetail/index.tsx`：

```tsx
export default function AnimeDetailPage() {
  return <div>AnimeDetail 占位</div>;
}
```

`src/pages/Watchlist/index.tsx`：

```tsx
export default function WatchlistPage() {
  return <div>Watchlist 占位</div>;
}
```

`src/pages/MyReviews/index.tsx`：

```tsx
export default function MyReviewsPage() {
  return <div>MyReviews 占位</div>;
}
```

- [ ] **Step 7: 安装依赖**

Run: `cd anitrack-web && npm install`
Expected: 安装成功退出码为 0；`postinstall` 钩子自动执行 `max setup`，生成 `src/.umi/` 目录（含 `tsconfig.json`）

- [ ] **Step 8: 验证构建**

Run: `cd anitrack-web && npm run build`
Expected: 退出码为 0，输出中不包含 `ERROR`，生成 `anitrack-web/dist/` 目录

- [ ] **Step 9: 提交**

```bash
cd anitrack-web && cd .. && git add anitrack-web/package.json anitrack-web/package-lock.json anitrack-web/tsconfig.json anitrack-web/.umirc.ts anitrack-web/.gitignore anitrack-web/src
git commit -m "feat(web): 初始化anitrack-web项目骨架与路由结构"
```

---

## Task 2: 类型定义与请求服务层

**Files:**
- Create: `anitrack-web/src/types/common.ts`
- Create: `anitrack-web/src/types/user.ts`
- Create: `anitrack-web/src/types/anime.ts`
- Create: `anitrack-web/src/types/watchlist.ts`
- Create: `anitrack-web/src/types/review.ts`
- Create: `anitrack-web/src/services/user.ts`
- Create: `anitrack-web/src/services/anime.ts`
- Create: `anitrack-web/src/services/watchlist.ts`
- Create: `anitrack-web/src/services/review.ts`

**Interfaces:**
- Consumes: 无（纯类型与请求封装，`request` 来自 `@umijs/max`）
- Produces：
  - `register(params: {username,password,nickname}): Promise<UserInfo>`
  - `login(params: {username,password}): Promise<LoginResult>`
  - `searchAnime(keyword: string): Promise<AnimeInfo[]>`
  - `getAnimeDetail(animeId: number): Promise<AnimeInfo>`
  - `addToWatchlist(animeId: number): Promise<WatchlistItem>`
  - `changeWatchStatus(animeId: number, status: WatchStatus): Promise<WatchlistItem>`
  - `updateWatchProgress(animeId: number, episode: number): Promise<WatchlistItem>`
  - `getWatchlistDetail(animeId: number): Promise<WatchlistItem>`（未追番时 reject，不弹错误提示）
  - `listWatchlist(status?: WatchStatus): Promise<WatchlistItemView[]>`
  - `addReview(animeId: number, score: number, content?: string): Promise<Review>`
  - `updateReview(animeId: number, score: number, content?: string): Promise<Review>`
  - `getMyReviewDetail(animeId: number): Promise<Review>`（未评价时 reject，不弹错误提示）
  - `listReviewsByAnime(animeId: number, page: number, pageSize: number): Promise<PageResult<ReviewWithUser>>`
  - `listMyReviews(): Promise<ReviewWithAnime[]>`

- [ ] **Step 1: 创建公共类型 src/types/common.ts**

```ts
export interface ApiResult<T> {
  status: number;
  message: string | null;
  data: T;
}

export interface PageResult<T> {
  pageNum: number;
  pageSize: number;
  total: number;
  list: T[];
}
```

- [ ] **Step 2: 创建 src/types/user.ts**

```ts
export interface UserInfo {
  id: number;
  username: string;
  nickname: string;
  avatarUrl: string | null;
}

export interface LoginResult {
  token: string;
  userInfo: UserInfo;
}

export interface RegisterParams {
  username: string;
  password: string;
  nickname: string;
}

export interface LoginParams {
  username: string;
  password: string;
}
```

- [ ] **Step 3: 创建 src/types/anime.ts**

```ts
export interface AnimeInfo {
  id: number;
  bangumiId: number;
  titleCn: string;
  titleOriginal: string;
  coverUrl: string | null;
  totalEpisodes: number | null;
  airDate: string | null;
  summary: string | null;
}
```

- [ ] **Step 4: 创建 src/types/watchlist.ts**

```ts
export type WatchStatus = 'WANT_TO_WATCH' | 'WATCHING' | 'WATCHED' | 'DROPPED';

export interface WatchlistItem {
  id: number;
  animeId: number;
  status: WatchStatus;
  currentEpisode: number;
  updateTime: string;
}

export interface WatchlistItemView extends WatchlistItem {
  animeTitleCn: string;
  animeTitleOriginal: string;
  animeCoverUrl: string | null;
}
```

- [ ] **Step 5: 创建 src/types/review.ts**

```ts
export interface Review {
  id: number;
  animeId: number;
  score: number;
  content: string | null;
  createTime: string;
}

export interface ReviewWithUser {
  id: number;
  userId: number;
  userNickname: string;
  userAvatarUrl: string | null;
  score: number;
  content: string | null;
  createTime: string;
}

export interface ReviewWithAnime {
  id: number;
  animeId: number;
  animeTitleCn: string;
  animeTitleOriginal: string;
  animeCoverUrl: string | null;
  score: number;
  content: string | null;
  createTime: string;
}
```

- [ ] **Step 6: 创建 src/services/user.ts**

```ts
import { request } from '@umijs/max';
import type { ApiResult } from '@/types/common';
import type { UserInfo, LoginResult, RegisterParams, LoginParams } from '@/types/user';

export async function register(params: RegisterParams) {
  const res = await request<ApiResult<UserInfo>>('/api/user/register', {
    method: 'POST',
    data: params,
  });
  return res.data;
}

export async function login(params: LoginParams) {
  const res = await request<ApiResult<LoginResult>>('/api/user/login', {
    method: 'POST',
    data: params,
  });
  return res.data;
}
```

- [ ] **Step 7: 创建 src/services/anime.ts**

```ts
import { request } from '@umijs/max';
import type { ApiResult } from '@/types/common';
import type { AnimeInfo } from '@/types/anime';

export async function searchAnime(keyword: string) {
  const res = await request<ApiResult<AnimeInfo[]>>('/api/anime/search', {
    method: 'POST',
    data: { keyword },
  });
  return res.data;
}

export async function getAnimeDetail(animeId: number) {
  const res = await request<ApiResult<AnimeInfo>>('/api/anime/detail', {
    method: 'POST',
    data: { animeId },
  });
  return res.data;
}
```

- [ ] **Step 8: 创建 src/services/watchlist.ts**

```ts
import { request } from '@umijs/max';
import type { ApiResult } from '@/types/common';
import type { WatchlistItem, WatchlistItemView, WatchStatus } from '@/types/watchlist';

export async function addToWatchlist(animeId: number) {
  const res = await request<ApiResult<WatchlistItem>>('/api/watchlist/add', {
    method: 'POST',
    data: { animeId },
  });
  return res.data;
}

export async function changeWatchStatus(animeId: number, status: WatchStatus) {
  const res = await request<ApiResult<WatchlistItem>>('/api/watchlist/change_status', {
    method: 'POST',
    data: { animeId, status },
  });
  return res.data;
}

export async function updateWatchProgress(animeId: number, episode: number) {
  const res = await request<ApiResult<WatchlistItem>>('/api/watchlist/update_progress', {
    method: 'POST',
    data: { animeId, episode },
  });
  return res.data;
}

export async function getWatchlistDetail(animeId: number) {
  const res = await request<ApiResult<WatchlistItem>>('/api/watchlist/detail', {
    method: 'POST',
    data: { animeId },
    skipErrorHandler: true,
  });
  return res.data;
}

export async function listWatchlist(status?: WatchStatus) {
  const res = await request<ApiResult<WatchlistItemView[]>>('/api/watchlist/list', {
    method: 'POST',
    data: { status },
  });
  return res.data;
}
```

- [ ] **Step 9: 创建 src/services/review.ts**

```ts
import { request } from '@umijs/max';
import type { ApiResult, PageResult } from '@/types/common';
import type { Review, ReviewWithUser, ReviewWithAnime } from '@/types/review';

export async function addReview(animeId: number, score: number, content?: string) {
  const res = await request<ApiResult<Review>>('/api/review/add', {
    method: 'POST',
    data: { animeId, score, content },
  });
  return res.data;
}

export async function updateReview(animeId: number, score: number, content?: string) {
  const res = await request<ApiResult<Review>>('/api/review/update', {
    method: 'POST',
    data: { animeId, score, content },
  });
  return res.data;
}

export async function getMyReviewDetail(animeId: number) {
  const res = await request<ApiResult<Review>>('/api/review/detail', {
    method: 'POST',
    data: { animeId },
    skipErrorHandler: true,
  });
  return res.data;
}

export async function listReviewsByAnime(animeId: number, page: number, pageSize: number) {
  const res = await request<ApiResult<PageResult<ReviewWithUser>>>('/api/review/list_by_anime', {
    method: 'POST',
    data: { animeId, page, pageSize },
  });
  return res.data;
}

export async function listMyReviews() {
  const res = await request<ApiResult<ReviewWithAnime[]>>('/api/review/my_list', {
    method: 'POST',
  });
  return res.data;
}
```

- [ ] **Step 10: 验证构建**

Run: `cd anitrack-web && npm run build`
Expected: 退出码为 0，无 TypeScript 报错

- [ ] **Step 11: 提交**

```bash
git add anitrack-web/src/types anitrack-web/src/services
git commit -m "feat(web): 新增类型定义与4个业务域的请求服务层"
```

---

## Task 3: 请求运行时配置（app.ts）

**Files:**
- Create: `anitrack-web/src/app.ts`

**Interfaces:**
- Consumes: `UserInfo`（来自 Task 2 的 `@/types/user`）
- Produces: 全局 `getInitialState()` 返回 `{ currentUser?: UserInfo }`，供后续任务通过 `useModel('@@initialState')` 消费；全局请求错误处理（401 跳转登录、`status:0` 弹提示、`skipErrorHandler` 例外透传）

- [ ] **Step 1: 创建 src/app.ts**

```ts
import { history } from '@umijs/max';
import type { RequestConfig } from '@umijs/max';
import { message } from 'antd';
import type { UserInfo } from '@/types/user';

export async function getInitialState(): Promise<{ currentUser?: UserInfo }> {
  const raw = localStorage.getItem('userInfo');
  if (!raw) return {};
  try {
    return { currentUser: JSON.parse(raw) as UserInfo };
  } catch {
    return {};
  }
}

interface RawApiResponse {
  status: number;
  message: string | null;
  data: unknown;
}

export const request: RequestConfig = {
  timeout: 10000,
  errorConfig: {
    errorThrower: (res: RawApiResponse) => {
      if (res.status === 0) {
        const error: any = new Error(res.message ?? '请求失败');
        error.name = 'BizError';
        throw error;
      }
    },
    errorHandler: (error: any, opts: any) => {
      if (opts?.skipErrorHandler) {
        throw error;
      }
      if (error.name === 'BizError') {
        message.error(error.message);
        return;
      }
      if (error.response) {
        if (error.response.status === 401) {
          localStorage.removeItem('token');
          localStorage.removeItem('userInfo');
          history.push('/login');
          return;
        }
        message.error(`请求失败（HTTP ${error.response.status}）`);
        return;
      }
      message.error('网络异常，请稍后重试');
    },
  },
  requestInterceptors: [
    (config: any) => {
      const token = localStorage.getItem('token');
      if (token) {
        config.headers = { ...(config.headers ?? {}), Authorization: `Bearer ${token}` };
      }
      return config;
    },
  ],
};
```

- [ ] **Step 2: 验证构建**

Run: `cd anitrack-web && npm run build`
Expected: 退出码为 0，无 TypeScript 报错

- [ ] **Step 3: 提交**

```bash
git add anitrack-web/src/app.ts
git commit -m "feat(web): 配置请求运行时的token注入与统一错误处理"
```

---

## Task 4: 登录守卫与主布局

**Files:**
- Modify: `anitrack-web/src/wrappers/auth.tsx`（替换 Task1 占位实现）
- Modify: `anitrack-web/src/layouts/index.tsx`（替换 Task1 占位实现）

**Interfaces:**
- Consumes: `useModel('@@initialState')` 返回的 `{ currentUser?: UserInfo }`（Task 3 产出）
- Produces: 未登录访问受保护路由自动跳转 `/login`；登录后渲染带侧边栏（番剧搜索/我的追番/我的评价三项菜单 + 昵称 + 退出登录）的主布局

- [ ] **Step 1: 替换 src/wrappers/auth.tsx**

```tsx
import { Navigate, Outlet, useModel } from '@umijs/max';

export default function AuthWrapper() {
  const { initialState } = useModel('@@initialState');
  if (initialState?.currentUser) {
    return <Outlet />;
  }
  return <Navigate to="/login" replace />;
}
```

- [ ] **Step 2: 替换 src/layouts/index.tsx**

```tsx
import { Outlet, useModel, useLocation, history, Link } from '@umijs/max';
import { Layout, Menu, Button, Space, Typography } from 'antd';

const { Sider, Content, Header } = Layout;
const { Text } = Typography;

const MENU_ITEMS = [
  { key: '/anime/search', label: <Link to="/anime/search">番剧搜索</Link> },
  { key: '/watchlist', label: <Link to="/watchlist">我的追番</Link> },
  { key: '/reviews', label: <Link to="/reviews">我的评价</Link> },
];

export default function MainLayout() {
  const { initialState, setInitialState } = useModel('@@initialState');
  const location = useLocation();

  const selectedKey =
    MENU_ITEMS.find((item) => location.pathname.startsWith(item.key))?.key ?? '/anime/search';

  const handleLogout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('userInfo');
    setInitialState({ currentUser: undefined });
    history.push('/login');
  };

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider>
        <div style={{ color: '#fff', textAlign: 'center', padding: 16, fontSize: 18 }}>
          anitrack
        </div>
        <Menu theme="dark" mode="inline" selectedKeys={[selectedKey]} items={MENU_ITEMS} />
      </Sider>
      <Layout>
        <Header style={{ background: '#fff', display: 'flex', justifyContent: 'flex-end', alignItems: 'center' }}>
          <Space>
            <Text>{initialState?.currentUser?.nickname}</Text>
            <Button onClick={handleLogout}>退出登录</Button>
          </Space>
        </Header>
        <Content style={{ padding: 24 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
```

- [ ] **Step 3: 验证构建**

Run: `cd anitrack-web && npm run build`
Expected: 退出码为 0，无 TypeScript 报错

- [ ] **Step 4: 提交**

```bash
git add anitrack-web/src/wrappers/auth.tsx anitrack-web/src/layouts/index.tsx
git commit -m "feat(web): 实现登录守卫与侧边栏主布局"
```

---

## Task 5: 登录页面

**Files:**
- Modify: `anitrack-web/src/pages/Login/index.tsx`（替换 Task1 占位实现）

**Interfaces:**
- Consumes: `login(params: LoginParams): Promise<LoginResult>`（Task 2），`useModel('@@initialState').setInitialState`（Task 3/4 建立的模式）
- Produces: 登录成功后写入 `localStorage.token`/`localStorage.userInfo`，更新 `initialState.currentUser`，跳转 `/anime/search`

- [ ] **Step 1: 替换 src/pages/Login/index.tsx**

```tsx
import { history, useModel } from '@umijs/max';
import { Button, Card, Form, Input, message } from 'antd';
import { login } from '@/services/user';
import type { LoginParams } from '@/types/user';

export default function LoginPage() {
  const { setInitialState } = useModel('@@initialState');

  const onFinish = async (values: LoginParams) => {
    const result = await login(values);
    localStorage.setItem('token', result.token);
    localStorage.setItem('userInfo', JSON.stringify(result.userInfo));
    setInitialState({ currentUser: result.userInfo });
    message.success('登录成功');
    history.push('/anime/search');
  };

  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        minHeight: '100vh',
        background: '#f0f2f5',
      }}
    >
      <Card title="登录 anitrack" style={{ width: 360 }}>
        <Form<LoginParams> onFinish={onFinish} layout="vertical">
          <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" block>
              登录
            </Button>
          </Form.Item>
          <Button type="link" block onClick={() => history.push('/register')}>
            还没有账号？去注册
          </Button>
        </Form>
      </Card>
    </div>
  );
}
```

- [ ] **Step 2: 验证构建**

Run: `cd anitrack-web && npm run build`
Expected: 退出码为 0，无 TypeScript 报错

- [ ] **Step 3: 提交**

```bash
git add anitrack-web/src/pages/Login/index.tsx
git commit -m "feat(web): 实现登录页面"
```

---

## Task 6: 注册页面

**Files:**
- Modify: `anitrack-web/src/pages/Register/index.tsx`（替换 Task1 占位实现）

**Interfaces:**
- Consumes: `register(params: RegisterParams): Promise<UserInfo>`（Task 2）
- Produces: 注册成功后提示并跳转 `/login`

- [ ] **Step 1: 替换 src/pages/Register/index.tsx**

```tsx
import { history } from '@umijs/max';
import { Button, Card, Form, Input, message } from 'antd';
import { register } from '@/services/user';
import type { RegisterParams } from '@/types/user';

export default function RegisterPage() {
  const onFinish = async (values: RegisterParams) => {
    await register(values);
    message.success('注册成功，请登录');
    history.push('/login');
  };

  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        minHeight: '100vh',
        background: '#f0f2f5',
      }}
    >
      <Card title="注册 anitrack" style={{ width: 360 }}>
        <Form<RegisterParams> onFinish={onFinish} layout="vertical">
          <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="nickname" label="昵称" rules={[{ required: true, message: '请输入昵称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" block>
              注册
            </Button>
          </Form.Item>
          <Button type="link" block onClick={() => history.push('/login')}>
            已有账号？去登录
          </Button>
        </Form>
      </Card>
    </div>
  );
}
```

- [ ] **Step 2: 验证构建**

Run: `cd anitrack-web && npm run build`
Expected: 退出码为 0，无 TypeScript 报错

- [ ] **Step 3: 提交**

```bash
git add anitrack-web/src/pages/Register/index.tsx
git commit -m "feat(web): 实现注册页面"
```

---

## Task 7: 番剧搜索页面

**Files:**
- Modify: `anitrack-web/src/pages/AnimeSearch/index.tsx`（替换 Task1 占位实现）

**Interfaces:**
- Consumes: `searchAnime(keyword: string): Promise<AnimeInfo[]>`（Task 2）
- Produces: 无（叶子页面，点击卡片用 `history.push('/anime/:animeId')` 跳转，供 Task 9 的路由承接）

- [ ] **Step 1: 替换 src/pages/AnimeSearch/index.tsx**

```tsx
import { useState } from 'react';
import { history } from '@umijs/max';
import { Input, Card, Row, Col, Empty, Spin } from 'antd';
import { searchAnime } from '@/services/anime';
import type { AnimeInfo } from '@/types/anime';

const { Search } = Input;
const { Meta } = Card;

export default function AnimeSearchPage() {
  const [results, setResults] = useState<AnimeInfo[]>([]);
  const [loading, setLoading] = useState(false);
  const [searched, setSearched] = useState(false);

  const handleSearch = async (keyword: string) => {
    if (!keyword.trim()) return;
    setLoading(true);
    try {
      const data = await searchAnime(keyword.trim());
      setResults(data);
    } finally {
      setLoading(false);
      setSearched(true);
    }
  };

  return (
    <div>
      <Search
        placeholder="搜索番剧标题"
        onSearch={handleSearch}
        enterButton
        style={{ maxWidth: 480, marginBottom: 24 }}
      />
      <Spin spinning={loading}>
        {searched && results.length === 0 ? (
          <Empty description="没有找到相关番剧" />
        ) : (
          <Row gutter={[16, 16]}>
            {results.map((anime) => (
              <Col key={anime.id} xs={24} sm={12} md={8} lg={6}>
                <Card
                  hoverable
                  cover={
                    anime.coverUrl ? (
                      <img
                        src={anime.coverUrl}
                        alt={anime.titleCn}
                        style={{ height: 280, objectFit: 'cover' }}
                      />
                    ) : undefined
                  }
                  onClick={() => history.push(`/anime/${anime.id}`)}
                >
                  <Meta title={anime.titleCn} description={`共 ${anime.totalEpisodes ?? '?'} 话`} />
                </Card>
              </Col>
            ))}
          </Row>
        )}
      </Spin>
    </div>
  );
}
```

- [ ] **Step 2: 验证构建**

Run: `cd anitrack-web && npm run build`
Expected: 退出码为 0，无 TypeScript 报错

- [ ] **Step 3: 提交**

```bash
git add anitrack-web/src/pages/AnimeSearch/index.tsx
git commit -m "feat(web): 实现番剧搜索页面"
```

---

## Task 8: 评价表单弹窗组件

**Files:**
- Create: `anitrack-web/src/components/ReviewFormModal.tsx`

**Interfaces:**
- Consumes: `addReview`、`updateReview`（Task 2 `@/services/review`）
- Produces: 组件 `ReviewFormModal(props: { open: boolean; animeId: number; initialValues?: { score: number; content: string | null }; onCancel: () => void; onSuccess: (review: Review) => void }): JSX.Element`。`initialValues` 为空时调用 `addReview`，否则调用 `updateReview`。供 Task 9（AnimeDetail）与 Task 11（MyReviews）复用

- [ ] **Step 1: 创建 src/components/ReviewFormModal.tsx**

```tsx
import { useEffect } from 'react';
import { Modal, Form, Rate, Input, message } from 'antd';
import { addReview, updateReview } from '@/services/review';
import type { Review } from '@/types/review';

interface ReviewFormValues {
  score: number;
  content?: string;
}

interface ReviewFormModalProps {
  open: boolean;
  animeId: number;
  initialValues?: { score: number; content: string | null };
  onCancel: () => void;
  onSuccess: (review: Review) => void;
}

export default function ReviewFormModal({
  open,
  animeId,
  initialValues,
  onCancel,
  onSuccess,
}: ReviewFormModalProps) {
  const [form] = Form.useForm<ReviewFormValues>();

  useEffect(() => {
    if (open) {
      form.setFieldsValue({
        score: initialValues?.score ?? 5,
        content: initialValues?.content ?? '',
      });
    }
  }, [open, initialValues, form]);

  const handleOk = async () => {
    const values = await form.validateFields();
    const review = initialValues
      ? await updateReview(animeId, values.score, values.content)
      : await addReview(animeId, values.score, values.content);
    message.success('评价已保存');
    onSuccess(review);
  };

  return (
    <Modal
      title={initialValues ? '编辑评价' : '写评价'}
      open={open}
      onCancel={onCancel}
      onOk={handleOk}
      destroyOnClose
    >
      <Form<ReviewFormValues> form={form} layout="vertical">
        <Form.Item name="score" label="评分" rules={[{ required: true, message: '请打分' }]}>
          <Rate count={10} />
        </Form.Item>
        <Form.Item name="content" label="评论内容">
          <Input.TextArea rows={4} />
        </Form.Item>
      </Form>
    </Modal>
  );
}
```

- [ ] **Step 2: 验证构建**

Run: `cd anitrack-web && npm run build`
Expected: 退出码为 0，无 TypeScript 报错

- [ ] **Step 3: 提交**

```bash
git add anitrack-web/src/components/ReviewFormModal.tsx
git commit -m "feat(web): 新增可复用的评价表单弹窗组件"
```

---

## Task 9: 番剧详情页面

**Files:**
- Modify: `anitrack-web/src/pages/AnimeDetail/index.tsx`（替换 Task1 占位实现）

**Interfaces:**
- Consumes: `getAnimeDetail`（Task2 anime）、`addToWatchlist`/`changeWatchStatus`/`updateWatchProgress`/`getWatchlistDetail`（Task2 watchlist）、`getMyReviewDetail`/`listReviewsByAnime`（Task2 review）、`ReviewFormModal`（Task8）
- Produces: 无（叶子页面）

- [ ] **Step 1: 替换 src/pages/AnimeDetail/index.tsx**

```tsx
import { useEffect, useState } from 'react';
import { useParams, useRequest } from '@umijs/max';
import {
  Card,
  Descriptions,
  Image,
  Tag,
  Select,
  InputNumber,
  Button,
  Table,
  Space,
  Typography,
  message,
} from 'antd';
import { getAnimeDetail } from '@/services/anime';
import {
  addToWatchlist,
  changeWatchStatus,
  updateWatchProgress,
  getWatchlistDetail,
} from '@/services/watchlist';
import { getMyReviewDetail, listReviewsByAnime } from '@/services/review';
import type { WatchStatus } from '@/types/watchlist';
import type { ReviewWithUser } from '@/types/review';
import ReviewFormModal from '@/components/ReviewFormModal';

const { Title, Paragraph } = Typography;

const STATUS_OPTIONS: { label: string; value: WatchStatus }[] = [
  { label: '想看', value: 'WANT_TO_WATCH' },
  { label: '在看', value: 'WATCHING' },
  { label: '看完', value: 'WATCHED' },
  { label: '弃番', value: 'DROPPED' },
];

const STATUS_LABEL: Record<WatchStatus, string> = {
  WANT_TO_WATCH: '想看',
  WATCHING: '在看',
  WATCHED: '看完',
  DROPPED: '弃番',
};

const REVIEW_COLUMNS = [
  { title: '用户', dataIndex: 'userNickname', key: 'userNickname' },
  { title: '评分', dataIndex: 'score', key: 'score' },
  { title: '内容', dataIndex: 'content', key: 'content' },
  { title: '时间', dataIndex: 'createTime', key: 'createTime' },
];

export default function AnimeDetailPage() {
  const { animeId: animeIdParam } = useParams<{ animeId: string }>();
  const animeId = Number(animeIdParam);
  const [reviewModalOpen, setReviewModalOpen] = useState(false);
  const [progress, setProgress] = useState(0);
  const [page, setPage] = useState(1);
  const pageSize = 10;

  const { data: anime, loading: animeLoading } = useRequest(() => getAnimeDetail(animeId), {
    refreshDeps: [animeId],
  });

  const {
    data: watchlistItem,
    loading: watchlistLoading,
    run: refetchWatchlist,
  } = useRequest(() => getWatchlistDetail(animeId).catch(() => undefined), {
    refreshDeps: [animeId],
  });

  const { data: myReview, run: refetchMyReview } = useRequest(
    () => getMyReviewDetail(animeId).catch(() => undefined),
    { refreshDeps: [animeId] },
  );

  const {
    data: reviewPage,
    loading: reviewLoading,
    run: refetchReviews,
  } = useRequest(() => listReviewsByAnime(animeId, page, pageSize), {
    refreshDeps: [animeId, page],
  });

  useEffect(() => {
    if (watchlistItem) {
      setProgress(watchlistItem.currentEpisode);
    }
  }, [watchlistItem]);

  const handleAdd = async () => {
    await addToWatchlist(animeId);
    message.success('已加入追番');
    refetchWatchlist();
  };

  const handleStatusChange = async (status: WatchStatus) => {
    await changeWatchStatus(animeId, status);
    message.success('状态已更新');
    refetchWatchlist();
  };

  const handleProgressSave = async () => {
    await updateWatchProgress(animeId, progress);
    message.success('进度已更新');
    refetchWatchlist();
  };

  return (
    <div>
      <Card loading={animeLoading}>
        {anime && (
          <Space align="start" size={24}>
            {anime.coverUrl && <Image src={anime.coverUrl} width={200} alt={anime.titleCn} />}
            <div>
              <Title level={3}>{anime.titleCn}</Title>
              <Paragraph type="secondary">{anime.titleOriginal}</Paragraph>
              <Descriptions column={1}>
                <Descriptions.Item label="集数">{anime.totalEpisodes ?? '未知'}</Descriptions.Item>
                <Descriptions.Item label="放送日期">{anime.airDate ?? '未知'}</Descriptions.Item>
              </Descriptions>
              <Paragraph>{anime.summary}</Paragraph>
            </div>
          </Space>
        )}
      </Card>

      <Card title="追番状态" loading={watchlistLoading} style={{ marginTop: 16 }}>
        {watchlistItem ? (
          <Space>
            <Tag color="blue">{STATUS_LABEL[watchlistItem.status]}</Tag>
            <Select
              value={watchlistItem.status}
              options={STATUS_OPTIONS}
              style={{ width: 120 }}
              onChange={handleStatusChange}
            />
            <span>第</span>
            <InputNumber min={0} value={progress} onChange={(v) => setProgress(v ?? 0)} />
            <span>话</span>
            <Button onClick={handleProgressSave}>更新进度</Button>
            {watchlistItem.status === 'WATCHED' && (
              <Button type="primary" onClick={() => setReviewModalOpen(true)}>
                {myReview ? '编辑评价' : '写评价'}
              </Button>
            )}
          </Space>
        ) : (
          <Button type="primary" onClick={handleAdd}>
            加入追番
          </Button>
        )}
      </Card>

      <Card title="评价列表" style={{ marginTop: 16 }}>
        <Table<ReviewWithUser>
          rowKey="id"
          loading={reviewLoading}
          columns={REVIEW_COLUMNS}
          dataSource={reviewPage?.list}
          pagination={{
            current: page,
            pageSize,
            total: reviewPage?.total,
            onChange: setPage,
          }}
        />
      </Card>

      <ReviewFormModal
        open={reviewModalOpen}
        animeId={animeId}
        initialValues={myReview ? { score: myReview.score, content: myReview.content } : undefined}
        onCancel={() => setReviewModalOpen(false)}
        onSuccess={() => {
          setReviewModalOpen(false);
          refetchMyReview();
          refetchReviews();
        }}
      />
    </div>
  );
}
```

- [ ] **Step 2: 验证构建**

Run: `cd anitrack-web && npm run build`
Expected: 退出码为 0，无 TypeScript 报错

- [ ] **Step 3: 提交**

```bash
git add anitrack-web/src/pages/AnimeDetail/index.tsx
git commit -m "feat(web): 实现番剧详情页面（追番状态与评价列表）"
```

---

## Task 10: 我的追番列表页面

**Files:**
- Modify: `anitrack-web/src/pages/Watchlist/index.tsx`（替换 Task1 占位实现）

**Interfaces:**
- Consumes: `listWatchlist`、`changeWatchStatus`、`updateWatchProgress`（Task 2 `@/services/watchlist`）
- Produces: 无（叶子页面）

- [ ] **Step 1: 替换 src/pages/Watchlist/index.tsx**

```tsx
import { useState } from 'react';
import { history, useRequest } from '@umijs/max';
import { Tabs, List, Tag, Select, InputNumber, Button, Space, message } from 'antd';
import { listWatchlist, changeWatchStatus, updateWatchProgress } from '@/services/watchlist';
import type { WatchStatus, WatchlistItemView } from '@/types/watchlist';

const TABS: { key: string; label: string; status?: WatchStatus }[] = [
  { key: 'ALL', label: '全部' },
  { key: 'WANT_TO_WATCH', label: '想看', status: 'WANT_TO_WATCH' },
  { key: 'WATCHING', label: '在看', status: 'WATCHING' },
  { key: 'WATCHED', label: '看完', status: 'WATCHED' },
  { key: 'DROPPED', label: '弃番', status: 'DROPPED' },
];

const STATUS_OPTIONS: { label: string; value: WatchStatus }[] = [
  { label: '想看', value: 'WANT_TO_WATCH' },
  { label: '在看', value: 'WATCHING' },
  { label: '看完', value: 'WATCHED' },
  { label: '弃番', value: 'DROPPED' },
];

function WatchlistRow({ item, onChanged }: { item: WatchlistItemView; onChanged: () => void }) {
  const [progress, setProgress] = useState(item.currentEpisode);

  const handleStatusChange = async (status: WatchStatus) => {
    await changeWatchStatus(item.animeId, status);
    message.success('状态已更新');
    onChanged();
  };

  const handleProgressSave = async () => {
    await updateWatchProgress(item.animeId, progress);
    message.success('进度已更新');
    onChanged();
  };

  return (
    <List.Item
      actions={[
        <Select
          key="status"
          value={item.status}
          options={STATUS_OPTIONS}
          style={{ width: 100 }}
          onChange={handleStatusChange}
        />,
        <Space key="progress">
          <InputNumber min={0} value={progress} onChange={(v) => setProgress(v ?? 0)} />
          <Button onClick={handleProgressSave}>更新进度</Button>
        </Space>,
      ]}
    >
      <List.Item.Meta
        avatar={
          item.animeCoverUrl ? (
            <img src={item.animeCoverUrl} width={48} alt={item.animeTitleCn} />
          ) : undefined
        }
        title={<a onClick={() => history.push(`/anime/${item.animeId}`)}>{item.animeTitleCn}</a>}
        description={<Tag>{STATUS_OPTIONS.find((s) => s.value === item.status)?.label}</Tag>}
      />
    </List.Item>
  );
}

export default function WatchlistPage() {
  const [activeTab, setActiveTab] = useState('ALL');
  const activeStatus = TABS.find((t) => t.key === activeTab)?.status;

  const {
    data: items,
    loading,
    run: refetch,
  } = useRequest(() => listWatchlist(activeStatus), { refreshDeps: [activeStatus] });

  return (
    <div>
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={TABS.map((t) => ({ key: t.key, label: t.label }))}
      />
      <List
        loading={loading}
        dataSource={items}
        renderItem={(item: WatchlistItemView) => (
          <WatchlistRow key={item.id} item={item} onChanged={refetch} />
        )}
      />
    </div>
  );
}
```

- [ ] **Step 2: 验证构建**

Run: `cd anitrack-web && npm run build`
Expected: 退出码为 0，无 TypeScript 报错

- [ ] **Step 3: 提交**

```bash
git add anitrack-web/src/pages/Watchlist/index.tsx
git commit -m "feat(web): 实现我的追番列表页面"
```

---

## Task 11: 我的评价列表页面

**Files:**
- Modify: `anitrack-web/src/pages/MyReviews/index.tsx`（替换 Task1 占位实现）

**Interfaces:**
- Consumes: `listMyReviews`（Task 2 `@/services/review`）、`ReviewFormModal`（Task 8）
- Produces: 无（叶子页面，anitrack-web 全部页面实现完成）

- [ ] **Step 1: 替换 src/pages/MyReviews/index.tsx**

```tsx
import { useState } from 'react';
import { history, useRequest } from '@umijs/max';
import { List, Rate, Typography, Button, Space } from 'antd';
import { listMyReviews } from '@/services/review';
import ReviewFormModal from '@/components/ReviewFormModal';
import type { ReviewWithAnime } from '@/types/review';

const { Paragraph, Text } = Typography;

export default function MyReviewsPage() {
  const [editing, setEditing] = useState<ReviewWithAnime | null>(null);

  const { data: reviews, loading, run: refetch } = useRequest(() => listMyReviews());

  return (
    <div>
      <List
        loading={loading}
        dataSource={reviews}
        renderItem={(review: ReviewWithAnime) => (
          <List.Item actions={[<Button key="edit" onClick={() => setEditing(review)}>编辑</Button>]}>
            <List.Item.Meta
              avatar={
                review.animeCoverUrl ? (
                  <img src={review.animeCoverUrl} width={48} alt={review.animeTitleCn} />
                ) : undefined
              }
              title={
                <a onClick={() => history.push(`/anime/${review.animeId}`)}>{review.animeTitleCn}</a>
              }
              description={
                <Space direction="vertical">
                  <Rate disabled count={10} value={review.score} />
                  <Paragraph>{review.content}</Paragraph>
                  <Text type="secondary">{review.createTime}</Text>
                </Space>
              }
            />
          </List.Item>
        )}
      />

      {editing && (
        <ReviewFormModal
          open
          animeId={editing.animeId}
          initialValues={{ score: editing.score, content: editing.content }}
          onCancel={() => setEditing(null)}
          onSuccess={() => {
            setEditing(null);
            refetch();
          }}
        />
      )}
    </div>
  );
}
```

- [ ] **Step 2: 验证构建**

Run: `cd anitrack-web && npm run build`
Expected: 退出码为 0，无 TypeScript 报错

- [ ] **Step 3: 提交**

```bash
git add anitrack-web/src/pages/MyReviews/index.tsx
git commit -m "feat(web): 实现我的评价列表页面"
```

---

## Task 12: 端到端手动验证

**Files:**
- 无代码改动

**Interfaces:**
- Consumes: 全部已完成任务的产出
- Produces: 确认整个应用可用，作为计划的收尾任务

- [ ] **Step 1: 全量构建检查**

Run: `cd anitrack-web && npm run build`
Expected: 退出码为 0，无 TypeScript 报错，`anitrack-web/dist/` 生成完整

- [ ] **Step 2: 启动后端**

Run（需要用户提供真实的数据库凭证）：

```bash
DB_USERNAME=xxx DB_PASSWORD=xxx JWT_SECRET=xxx mvn -pl anitrack-starter spring-boot:run
```

Expected: 服务监听 `8080`，Flyway 自动建表成功

- [ ] **Step 3: 启动前端**

Run: `cd anitrack-web && npm run dev`
Expected: 监听 `8000`，浏览器打开 `http://localhost:8000` 自动跳转 `/login`

- [ ] **Step 4: 手动走一遍完整链路**

按顺序在浏览器中操作并确认：

1. 在 `/register` 注册一个新账号 → 跳转 `/login`
2. 用刚注册的账号登录 → 跳转 `/anime/search`，侧边栏显示昵称
3. 搜索一个真实存在的番剧关键词（触发 Bangumi API 实时搜索）→ 出现结果卡片
4. 点击卡片进入详情页 → 显示番剧信息，点击"加入追番"
5. 在详情页把状态切到"看完"，更新进度
6. 点击"写评价"，打 5 分并填写评论 → 保存成功，评价列表里出现这条记录
7. 进入"我的追番"，按状态筛选能看到刚才那条记录，可在列表页直接改状态/进度
8. 进入"我的评价"，能看到刚才写的评价，点击"编辑"能修改评分和内容
9. 点击"退出登录" → 跳转 `/login`；直接在地址栏访问 `/watchlist` → 被重定向回 `/login`（验证登录守卫生效）

Expected: 以上 9 步全部符合预期，无控制台报错、无网络请求 500

- [ ] **Step 5: 关闭服务**

确认无误后，`Ctrl+C` 停止前后端进程。此任务无需 git 提交（无代码改动）。
