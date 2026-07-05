# Phase 1a 遗留待办：需要你本地手动完成的事项

背景：Phase 1a（番剧目录）已经合并到 main，全部单元/集成测试在沙箱里跑通了。但沙箱环境
**没有公网出口**（连不上 `api.bgm.tv`）、**没有本地 MySQL 密码**，所以计划里 Task 7 Step 3
的"真实起服务 + 真实数据库 + 真实调用 Bangumi API"这一步没法在会话里完成，需要你在自己电脑上跑一遍。

以下按顺序做，每一步都有具体命令和"预期结果应该长什么样"，出问题对照排查部分。

---

## 第 0 步：确认 Maven/JDK 环境（否则编译不过）

本机默认 `PATH` 上的 `mvn` 是老版本（Maven 3.6.0 + JDK 8），编译不了这个项目（要求 JDK 17 + Maven ≥3.6.3）。
每次开新终端窗口操作这个项目前，先执行：

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
export PATH=/opt/homebrew/opt/maven/bin:$PATH
mvn -v
```

看到输出里有 `Apache Maven 3.9.16` 和 `Java version: 17.0.10` 就对了。如果没有这两个版本，
先确认本机是否装了 Temurin 17（`/Library/Java/JavaVirtualMachines/temurin-17.jdk` 这个路径）和
Homebrew 版 Maven（`brew install maven`）。

---

## 第 1 步：准备本地 MySQL

沙箱探测发现你本机的 MySQL 服务**正在运行**，但需要密码（`mysqladmin ping` 返回
"Access denied for user 'root'@'localhost'"）。你需要知道你本机 MySQL root 密码，或者专门建一个
anitrack 用的账号。

### 1.1 确认能连上

```bash
mysql -u root -p -h 127.0.0.1
```

输入你的 MySQL root 密码。如果连不上、忘记密码，或者想单独建一个账号（更干净），执行：

```bash
mysql -u root -p -h 127.0.0.1 <<'SQL'
CREATE USER IF NOT EXISTS 'anitrack'@'localhost' IDENTIFIED BY '你自己定的密码';
GRANT ALL PRIVILEGES ON anitrack.* TO 'anitrack'@'localhost';
FLUSH PRIVILEGES;
SQL
```

### 1.2 建库（如果 Phase 0 联调时已经建过 `anitrack` 库，跳过这一步）

```bash
mysql -u root -p -h 127.0.0.1 -e "CREATE DATABASE IF NOT EXISTS anitrack CHARACTER SET utf8mb4;"
```

Flyway 会在应用启动时自动建表（`V1__create_user_table.sql`、`V2__create_anime_table.sql`），
不需要手动建表。如果 Phase 0 联调时这个库已经跑过 `V1`，这次启动会自动追加跑 `V2`。

---

## 第 2 步：把 User-Agent 占位符换成你自己的

打开 `anitrack-starter/src/main/resources/application.yml`，找到这一段：

```yaml
anitrack:
  bangumi:
    base-url: https://api.bgm.tv
    user-agent: anitrack-practice/1.0 (https://github.com/your-name/anitrack)
    connect-timeout-ms: 3000
    read-timeout-ms: 5000
```

把 `user-agent` 里的 `https://github.com/your-name/anitrack` 换成你自己的仓库地址
（比如 `https://github.com/yuelu-lan/anitrack`）。Bangumi API 官方要求调用方带一个能识别来源的
User-Agent，不遵守可能被限流。这一步不改也能跑，但正式对接前建议换掉。

---

## 第 3 步：设置环境变量并启动应用

```bash
export DB_USERNAME=root                        # 或你在1.1建的anitrack账号
export DB_PASSWORD=你的MySQL密码
export JWT_SECRET=this-is-a-local-dev-secret-key-32bytes-min   # 必须≥32字符，随便编但要够长

cd /Users/ywy/Desktop/my-project/anitrack
mvn -q -pl anitrack-starter -am spring-boot:run
```

**预期结果**：控制台没有异常堆栈，日志里能看到类似
`Flyway ... Successfully applied 1 migration to schema "anitrack"`（如果 `t_user` 表已经建过，
这里只会看到 `V2` 被应用；如果两张表都没建过，会看到 `V1`、`V2` 都被应用）。应用最终停在
"Started ApplicationLoader" 这一行，端口 8080。

如果这一步报错，看"排查"部分。

---

## 第 4 步：跑端到端验证（新开一个终端窗口，不要关掉第 3 步的应用）

### 4.1 注册一个测试用户并拿到登录 token

```bash
curl -s -X POST http://localhost:8080/api/user/register \
  -H "Content-Type: application/json" \
  -d '{"username":"bob","password":"password123","nickname":"Bob"}'
```

预期：`{"status":1,...}`（如果之前联调过 Phase 0 已经注册过 `bob`，这里会返回
`{"status":0,"message":"用户名已存在"}`，换个用户名重试，比如 `bob2`，下面命令里的用户名也要跟着改）。

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/user/login \
  -H "Content-Type: application/json" \
  -d '{"username":"bob","password":"password123"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])")

echo $TOKEN
```

预期：打印出一长串 JWT token 字符串（三段用 `.` 分隔）。如果打印空的，说明上一步注册/登录失败了，
回去看返回内容。

### 4.2 搜索番剧（真实调用 Bangumi API）

```bash
curl -s -X POST http://localhost:8080/api/anime/search \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"keyword":"败犬女主太多了"}'
```

**预期**：`{"status":1,"data":[{"id":1,"bangumiId":...,"titleCn":"败犬女主太多了！",...}]}`
（具体标题/字段值以 Bangumi 实际返回为准）。重点看三件事：
- `status` 是 `1`（不是 `0`）
- 返回列表里每条 `titleCn`/`titleOriginal` 至少有一个非空
- `id` 是一个从 1 开始的小整数（这是本地数据库自增 id，不是 Bangumi 的 `bangumiId`）

记下返回结果里第一条的 `id`（假设是 `1`），下一步要用。

### 4.3 查详情（只读本地缓存，不再调用 Bangumi）

```bash
curl -s -X POST http://localhost:8080/api/anime/detail \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"animeId":1}'
```

预期：返回内容跟上一步搜索结果里 `id=1` 的那条完全一致。

### 4.4 查一个不存在的 animeId

```bash
curl -s -X POST http://localhost:8080/api/anime/detail \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"animeId":99999}'
```

预期：`{"status":0,"message":"番剧不存在","data":null}`

### 4.5 验证鉴权确实生效（不带 token 应该被拒绝）

```bash
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/api/anime/search \
  -H "Content-Type: application/json" \
  -d '{"keyword":"随便"}'
```

预期：打印 `401`（这一条专门验证 Task 6 review 里揪出来又修复的那个 JWT 鉴权问题——现在应该是
生效的，没有 token 直接拒绝，不会走到 Bangumi 调用那一步）。

### 4.6 再搜一次同一个关键字，验证 upsert（不是重复插入报错）

```bash
curl -s -X POST http://localhost:8080/api/anime/search \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"keyword":"败犬女主太多了"}'
```

预期：请求成功（`status=1`），返回的 `id` 跟 4.2 第一次搜索时**完全一样**（说明命中了
`updateById` 分支去刷新已有记录，而不是尝试重复 `insert` 违反 `bangumi_id` 唯一索引报错）。

全部符合预期，就说明 Phase 1a 在真实环境下也是正常工作的。验证完按 `Ctrl+C` 停掉第 3 步启动的应用。

---

## 排查

**应用启动报 `Access denied for user`**：第 3 步 `DB_USERNAME`/`DB_PASSWORD` 跟第 1 步建的账号对不上，
检查拼写。

**应用启动报 Flyway 校验失败（checksum mismatch）**：说明这个数据库之前跑过版本不一样的 `V1`/`V2`
脚本。如果确定是干净环境，可以整个删库重建：
`mysql -u root -p -e "DROP DATABASE anitrack; CREATE DATABASE anitrack CHARACTER SET utf8mb4;"`

**4.2 搜索接口返回 `{"status":0,"message":"番剧信息服务暂时不可用，请稍后重试"}`**：
说明 `BangumiGatewayImpl` 调用 `api.bgm.tv` 失败了（`BangumiApiException` 被转译成了这条消息）。
先确认你本机能不能直接访问：
```bash
curl -m 8 https://api.bgm.tv/v0/subjects/1
```
如果这条也超时/失败，是你本机网络或者 Bangumi 服务本身的问题，不是代码问题；如果这条能通但接口
还是报错，看应用启动那个终端窗口的日志，`BangumiGatewayImpl` 抛异常时会打印堆栈，把关键字段贴出来
进一步排查（比如 Bangumi 返回了非 2xx 状态码，或者响应体格式跟预期的 `BangumiSearchResponseDTO`
对不上）。

**4.1 登录返回 token 但后续接口一直 401**：检查 `Authorization` header 有没有正确拼进
`Bearer <token>` 格式（中间必须有一个空格），以及 `$TOKEN` 变量是不是真的取到值了（`echo $TOKEN`
确认非空）。
