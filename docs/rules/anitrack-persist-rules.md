# anitrack 持久化层规范

## 概述

ORM 框架使用 MyBatis 原生 XML Mapper（不引入 MyBatis-Plus），不允许联表查询，不允许使用外键。

## 一、MyBatis XML 文件定义规范

### 1.1 基本结构

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.anitrack.infra.dal.mapper.WatchlistItemMapper">
</mapper>
```

### 1.2 ResultMap 定义

```xml
<resultMap id="BaseResultMap" type="com.anitrack.infra.dal.po.WatchlistItemPO">
    <id column="id" property="id" jdbcType="BIGINT"/>
    <result column="user_id" property="userId" jdbcType="BIGINT"/>
    <result column="anime_id" property="animeId" jdbcType="BIGINT"/>
    <result column="status" property="status" jdbcType="VARCHAR"/>
    <result column="current_episode" property="currentEpisode" jdbcType="INTEGER"/>
    <result column="create_time" property="createTime" jdbcType="TIMESTAMP"/>
    <result column="update_time" property="updateTime" jdbcType="TIMESTAMP"/>
</resultMap>
```

### 1.3 SQL 片段

```xml
<sql id="Base_Column_List">
    id, user_id, anime_id, status, current_episode, create_time, update_time
</sql>
```

### 1.4 CRUD 操作

```xml
<select id="selectByUserAndAnime" resultMap="BaseResultMap">
    SELECT <include refid="Base_Column_List"/>
    FROM t_watchlist_item
    WHERE user_id = #{userId,jdbcType=BIGINT}
      AND anime_id = #{animeId,jdbcType=BIGINT}
</select>

<insert id="insert" parameterType="com.anitrack.infra.dal.po.WatchlistItemPO" useGeneratedKeys="true" keyProperty="id">
    INSERT INTO t_watchlist_item (user_id, anime_id, status, current_episode, create_time, update_time)
    VALUES (#{userId,jdbcType=BIGINT}, #{animeId,jdbcType=BIGINT}, #{status,jdbcType=VARCHAR},
            #{currentEpisode,jdbcType=INTEGER}, #{createTime,jdbcType=TIMESTAMP}, #{updateTime,jdbcType=TIMESTAMP})
</insert>

<update id="updateById" parameterType="com.anitrack.infra.dal.po.WatchlistItemPO">
    UPDATE t_watchlist_item
    SET status = #{status,jdbcType=VARCHAR},
        current_episode = #{currentEpisode,jdbcType=INTEGER},
        update_time = #{updateTime,jdbcType=TIMESTAMP}
    WHERE id = #{id,jdbcType=BIGINT}
</update>
```

### 1.5 扩展 Mapper 文件

复杂查询（多条件动态组合）放在 `XxxExMapper.xml` 中，与基础 CRUD 的 `XxxMapper.xml` 分离。

### 1.6 动态 SQL

```xml
<select id="listByCondition" resultMap="BaseResultMap">
    SELECT <include refid="Base_Column_List"/>
    FROM t_watchlist_item
    <where>
        <if test="userId != null">AND user_id = #{userId,jdbcType=BIGINT}</if>
        <if test="status != null">AND status = #{status,jdbcType=VARCHAR}</if>
    </where>
</select>
```

## 二、Java Mapper 接口规范

### 2.1 基础 Mapper

```java
public interface WatchlistItemMapper {
    WatchlistItemPO selectByUserAndAnime(@Param("userId") Long userId, @Param("animeId") Long animeId);
    int insert(WatchlistItemPO po);
    int updateById(WatchlistItemPO po);
}
```

### 2.2 扩展 Mapper

```java
public interface WatchlistItemExMapper {
    List<WatchlistItemPO> listByCondition(WatchlistItemQueryPO query);
}
```

## 三、PO 类定义规范

### 3.1 基本结构

- 命名规范：`Xxx` + `PO`，字段与数据库列一一对应，使用驼峰命名
- 使用 Lombok `@Data` 减少样板代码，不写业务逻辑方法

```java
@Data
public class WatchlistItemPO {
    private Long id;
    private Long userId;
    private Long animeId;
    private String status;
    private Integer currentEpisode;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

### 3.2 特殊类型处理

枚举类型（如 `WatchStatus`）在 PO 层用 `String` 存储，通过仓储实现层（`XxxRepoImpl`）用 MapStruct 转换为领域枚举，不在 PO 上直接使用枚举类型，避免持久化层与领域层耦合。

## 四、数据库表设计规范

### 4.1 命名规范

- 表名：`t_` + 小写下划线命名，如 `t_watchlist_item`、`t_anime`、`t_review`、`t_post`、`t_comment`、`t_user`
- 主键：`id`，`BIGINT` 自增
- 关联字段：`表名_id`，如 `user_id`、`anime_id`
- 审计字段：统一为 `create_time`、`update_time`（`DATETIME` 类型）

### 4.2 字段类型规范

| 业务类型 | 数据库类型 |
| --- | --- |
| 主键/外键 | `BIGINT` |
| 状态/枚举 | `VARCHAR(32)` |
| 时间 | `DATETIME` |
| 短文本 | `VARCHAR(255)` |
| 长文本 | `TEXT` |
| 计数 | `INT` |

### 4.3 通用字段设计

每张表均包含 `id`、`create_time`、`update_time` 三个通用字段。

### 4.4 索引设计

- `t_watchlist_item`、`t_review` 对 `(user_id, anime_id)` 建唯一索引，兜底"一用户一记录"的领域不变量
- 高频查询字段（如 `user_id`）单独建普通索引

## 五、TypeHandler 使用规范

如需在 PO 与数据库列之间做自定义类型转换（如 JSON 字段），实现 `TypeHandler<T>` 接口并在 XML 中通过 `typeHandler` 属性指定，全局注册在 MyBatis 配置中。

## 六、分页查询规范

使用 MyBatis 原生 `LIMIT #{offset}, #{pageSize}` 分页，不引入 PageHelper 等第三方分页插件（保持依赖精简）：

```xml
<select id="listByUserWithPage" resultMap="BaseResultMap">
    SELECT <include refid="Base_Column_List"/>
    FROM t_watchlist_item
    WHERE user_id = #{userId,jdbcType=BIGINT}
    LIMIT #{offset,jdbcType=INTEGER}, #{pageSize,jdbcType=INTEGER}
</select>
```

## 七、批量操作规范

批量插入使用 `<foreach>` 标签，单批次不超过 500 条：

```xml
<insert id="batchInsert">
    INSERT INTO t_watchlist_item (user_id, anime_id, status, current_episode, create_time, update_time)
    VALUES
    <foreach collection="list" item="item" separator=",">
        (#{item.userId}, #{item.animeId}, #{item.status}, #{item.currentEpisode}, #{item.createTime}, #{item.updateTime})
    </foreach>
</insert>
```

## 八、事务管理

事务边界统一在 `application` 层的 `XxxApplication` 方法上通过 `@Transactional` 声明，`infrastructure` 层的仓储实现方法不单独声明事务。

## 九、数据源配置

使用 Spring Boot 标准的 HikariCP 连接池 + Flyway 数据库版本管理，配置示例（`application.yml`）：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/anitrack?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
      connection-timeout: 30000
      idle-timeout: 600000
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
```

Flyway 迁移脚本命名规范：`V{version}__{description}.sql`，如 `V1__create_watchlist_item_table.sql`，存放于 `anitrack-starter/src/main/resources/db/migration`。

数据库连接凭证（用户名密码）通过环境变量注入，不硬编码在配置文件中。
