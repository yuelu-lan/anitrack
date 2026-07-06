# Phase 1a 端到端联调结果（已完成）

之前判断沙箱环境连不上 `api.bgm.tv` 也没有本地 MySQL 密码，所以这份文档最早是写给用户手动执行的
待办清单。后来用户提供了 `anitrack-starter/src/main/resources/application-local.yml`（已被
`.gitignore` 排除，不会提交到仓库）里的真实本地 MySQL 密码和 JWT secret，重新探测发现
`api.bgm.tv` 其实是可以访问的（此前的探测结果是一次性网络抖动）。于是补上了 `user-agent`
配置里的真实仓库地址，用 `SPRING_PROFILES_ACTIVE=local` 直接跑了一遍完整的端到端验证。
以下是**实际跑出来的结果**，不再是"预期应该长什么样"。

## 环境

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
export PATH=/opt/homebrew/opt/maven/bin:$PATH
export SPRING_PROFILES_ACTIVE=local
```

`application-local.yml` 提供 `spring.datasource.username/password` 和 `anitrack.jwt.secret`，
激活 `local` profile 后会覆盖 `application.yml` 里对应的 `${DB_USERNAME}`/`${DB_PASSWORD}`/
`${JWT_SECRET}` 占位符，不需要额外手动 `export` 这三个环境变量。

启动（多模块 reactor 下 `spring-boot:run` 认不出主类，需要先 `install` 上游模块，或直接在
`anitrack-starter` 目录下跑并显式指定主类）：

```bash
mvn -q install -DskipTests -pl anitrack-common,anitrack-domain,anitrack-infrastructure,anitrack-application -am
cd anitrack-starter
mvn -q spring-boot:run -Dspring-boot.run.mainClass=com.anitrack.starter.ApplicationLoader
```

## 验证结果

**Flyway 迁移**：`t_anime` 表在真实 MySQL 上被正确建出来（`SHOW TABLES IN anitrack` 确认
`flyway_schema_history`/`t_anime`/`t_user` 三张表都在）。

**注册 + 登录**：
```
POST /api/user/register → {"status":1,...,"data":{"id":3,"username":"e2e_bob",...}}
POST /api/user/login    → 拿到 JWT token（125 字符）
```

**JWT 鉴权确实生效**（不带 token 直接拒绝，没有 token 走不到 Bangumi 调用）：
```
POST /api/anime/search（无 Authorization header）→ HTTP 401
```

**真实调用 Bangumi API 搜索**（`POST /api/anime/search`，`keyword: "败犬女主太多了"`）——
返回 4 条真实数据，包括：

| id | bangumiId | titleCn | titleOriginal | totalEpisodes | airDate |
| --- | --- | --- | --- | --- | --- |
| 1 | 464376 | 败犬女主太多了！ | 負けヒロインが多すぎる！ | 12 | 2024-07-13 |
| 2 | 550507 | 败犬女主太多了！第二季 | 負けヒロインが多すぎる！ 第2期 | 0 | null |
| 3 | 133387 | 最弱无败神装机龙 | 最弱無敗の神装機竜 | 12 | 2016-01-11 |
| 4 | 525491 | 胜与败 | Win or Lose | 8 | 2025-02-19 |

第 2 条（"第二季"，尚未播出/资料不全）印证了设计文档里的判断：`totalEpisodes` 取 Bangumi 的
`eps` 字段，为 0 时代表"总集数未知"，`airDate` 也确实按预期映射为 `null`（Bangumi 该字段非必填）。
`titleCn`/`titleOriginal` 两个字段各自独立映射，没有互相回退。

**详情查询**（`POST /api/anime/detail`，`animeId: 1`）：返回内容与上面搜索结果里 `id=1` 那条
完全一致，确认走的是本地缓存读取，不是重新调用 Bangumi。

**404 场景**（`animeId: 99999`）：`{"status":0,"message":"番剧不存在","data":null}`

**upsert 幂等性**：用同一关键字再搜一次，返回的 4 条记录 `id` 跟第一次完全一致
（`[1,2,3,4]`），说明命中的是 `updateById` 分支刷新已有记录，不是重复 `insert` 违反
`bangumi_id` 唯一索引报错。

## 结论

Phase 1a 番剧目录功能在真实 MySQL + 真实 Bangumi API 环境下端到端验证通过，跟设计文档和
计划文档描述的行为完全一致，没有发现新问题。`anitrack.bangumi.user-agent` 配置已经改成真实
仓库地址（`https://github.com/yuelu-lan/anitrack`），随此次验证一起提交。
