# anitrack Phase 1a：番剧目录（Anime，Bangumi ACL）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现番剧目录（`Anime`）上下文：通过 ACL 防腐层对接真实 Bangumi API 完成搜索/详情查询，转换为本地只读缓存领域模型，并暴露 `/api/anime/search`、`/api/anime/detail` 两个接口。

**Architecture:** `anitrack-domain.anime` 定义 `Anime` 聚合根、`AnimeRepo` 仓储接口、`BangumiGateway` 网关接口与 `BangumiApiException`，不依赖任何框架；`anitrack-infrastructure` 用 MyBatis 原生 XML 实现 `AnimeRepo`（本地缓存 upsert），用 Spring `RestClient` 实现 `BangumiGateway`（真实调用 `POST /v0/search/subjects`）；`anitrack-application` 的 `AnimeApplication` 编排搜索（网关拉取 + 落库）与详情查询（只读本地），并将 `BangumiApiException` 转译为 `AnitrackAppException`；`anitrack-starter` 承载 `AnimeController` 与请求/响应对象。

**Tech Stack:** Java 17、Spring Boot 3.5.3、Spring `RestClient`（`spring-web` + `spring-boot-starter-json`，Jackson 序列化）、MyBatis 原生 XML（沿用 `t_user` 同款结构）、MapStruct 1.6.3（`AnimePO <-> Anime`）、Lombok、JUnit5 + Mockito + AssertJ、`@WebMvcTest` + MockMvc。

## Global Constraints

- 依赖方向：`starter → application → domain`；`infrastructure → domain`；仓储/网关接口定义在 `domain`，`infrastructure` 实现；`application` 不直接依赖 `infrastructure`（`docs/rules/anitrack-project-rules.md`）
- `BangumiGateway` 是唯一的外部系统防腐层：外部 DTO（`Bangumi*DTO`）只在 `infrastructure` 层出现，不泄漏到 `domain`/`application`（`docs/rules/anitrack-domain-rules.md`）
- `Anime` 领域模型字段不允许被业务代码直接修改，只能通过 ACL 同步更新（`docs/superpowers/specs/2026-07-05-anitrack-phase1-anime-catalog-design.md`，下称"本 spec"）
- `totalEpisodes` 取 Bangumi `eps` 字段（非 `total_episodes`）；`titleCn`/`titleOriginal` 分别对应 `name_cn`/`name`，不做回退判断（本 spec）
- `searchAnime` 每次都直接调用 Bangumi 实时搜索，结果逐条 `upsert` 回填本地缓存；`getAnimeDetail` 只读本地缓存，不回退调用 Bangumi（本 spec）
- 不使用 MyBatis-Plus，不允许联表查询，不允许使用外键（`docs/rules/anitrack-project-rules.md`）
- 聚合根内部字段通过构造方法或工厂方法保证初始状态合法，不暴露无校验的 setter（`docs/rules/anitrack-model-rules.md`）
- 断言统一使用 AssertJ `assertThat()`，禁止 `Assertions.assertEquals()`（`docs/rules/anitrack-unittest-rules.md`）
- Mock 交互验证必须明确指定次数（`times(1)` 等），不 Mock 值对象/DTO（`docs/rules/anitrack-unittest-rules.md`）
- 应用层统一异常使用 `AnitrackAppException(AppExceptionEnum)` 构造函数（沿用已有 `UserApplication` 实现，`AppExceptionEnum` 新增常量延续 40xxx 编码段，Anime 上下文使用 401xx）
- 版本号统一由根 `pom.xml` 的 `<dependencyManagement>` 管理，子模块 `<dependency>` 不单独指定 `<version>`（`docs/rules/anitrack-init-dependency-rules.md`）
- 本机默认 `PATH` 上的 `mvn` 解析到 `/Users/ywy/opt/apache-maven-3.6.0/bin/mvn`（绑定 JDK 8），无法编译本项目（要求 JDK 17 + Maven ≥3.6.3）。执行本计划所有 `mvn` 命令前需先执行：
  ```bash
  export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
  export PATH=/opt/homebrew/opt/maven/bin:$PATH
  ```
  （已验证 `mvn -v` 输出 `Apache Maven 3.9.16`／`Java version: 17.0.10` 即为正确环境）

---

## File Structure

```
anitrack-domain/src/main/java/com/anitrack/domain/
├── common/AnitrackDomainException.java                          # Modify: 新增 (message, cause) 构造函数
└── anime/
    ├── model/Anime.java                                         # 聚合根：fromBangumi()/reconstitute()
    ├── gateway/BangumiGateway.java                               # 接口：search(keyword)
    ├── repo/AnimeRepo.java                                       # 接口：getById/getByBangumiId/upsert
    └── exception/BangumiApiException.java                        # extends AnitrackDomainException
anitrack-domain/src/test/java/com/anitrack/domain/anime/model/AnimeTest.java

anitrack-starter/src/main/resources/db/migration/V2__create_anime_table.sql
anitrack-infrastructure/src/main/java/com/anitrack/infra/
├── dal/po/AnimePO.java
├── dal/mapper/AnimeMapper.java                                   # + resources/mapper/AnimeMapper.xml
├── converter/AnimeConverter.java                                 # MapStruct: AnimePO <-> Anime
├── repo/AnimeRepoImpl.java                                       # implements AnimeRepo，upsert 逻辑
├── config/BangumiProperties.java                                 # @ConfigurationProperties(prefix="anitrack.bangumi")
├── config/BangumiClientConfig.java                                # RestClient bean
└── gateway/bangumi/
    ├── dto/BangumiSearchRequestDTO.java
    ├── dto/BangumiSearchResponseDTO.java
    ├── dto/BangumiSubjectDTO.java
    ├── dto/BangumiImagesDTO.java
    ├── BangumiConverter.java                                     # DTO -> Anime
    └── BangumiGatewayImpl.java                                   # implements BangumiGateway
anitrack-infrastructure/src/test/java/com/anitrack/infra/
├── repo/AnimeRepoImplTest.java
└── gateway/bangumi/BangumiConverterTest.java

anitrack-application/src/main/java/com/anitrack/application/
├── exception/AppExceptionEnum.java                               # Modify: 新增 BANGUMI_SERVICE_UNAVAILABLE/ANIME_NOT_FOUND
├── model/AnimeBO.java
└── service/AnimeApplication.java                                 # searchAnime()/getAnimeDetail()
anitrack-application/src/test/java/com/anitrack/application/service/AnimeApplicationTest.java

anitrack-starter/src/main/java/com/anitrack/starter/
├── request/AnimeSearchReq.java
├── request/AnimeDetailReq.java
├── response/AnimeResponse.java
├── converter/HttpConverter.java                                  # Modify: 新增 animeBO2Response/animeBOList2Response
└── controller/AnimeController.java
anitrack-starter/src/test/java/com/anitrack/starter/controller/AnimeControllerTest.java
anitrack-starter/src/main/resources/application.yml                # Modify: 新增 anitrack.bangumi 配置
```

---

### Task 1: Anime 聚合根 + AnimeRepo/BangumiGateway 接口 + BangumiApiException

**Files:**
- Modify: `anitrack-domain/src/main/java/com/anitrack/domain/common/AnitrackDomainException.java`
- Create: `anitrack-domain/src/main/java/com/anitrack/domain/anime/model/Anime.java`
- Create: `anitrack-domain/src/main/java/com/anitrack/domain/anime/gateway/BangumiGateway.java`
- Create: `anitrack-domain/src/main/java/com/anitrack/domain/anime/repo/AnimeRepo.java`
- Create: `anitrack-domain/src/main/java/com/anitrack/domain/anime/exception/BangumiApiException.java`
- Test: `anitrack-domain/src/test/java/com/anitrack/domain/anime/model/AnimeTest.java`

**Interfaces:**
- Produces：`Anime.fromBangumi(Long bangumiId, String titleCn, String titleOriginal, String coverUrl, Integer totalEpisodes, LocalDate airDate, String summary): Anime`；`Anime.reconstitute(Long id, Long bangumiId, String titleCn, String titleOriginal, String coverUrl, Integer totalEpisodes, LocalDate airDate, String summary): Anime`；`BangumiGateway.search(String keyword): List<Anime>`；`AnimeRepo.getById(Long)`/`getByBangumiId(Long)`/`upsert(Anime): Anime`；`BangumiApiException(String message, Throwable cause)`；`AnitrackDomainException(String message, Throwable cause)` 供 `BangumiApiException` 使用

- [ ] **Step 1: 给 AnitrackDomainException 新增带 cause 的构造函数**

```java
package com.anitrack.domain.common;

public class AnitrackDomainException extends RuntimeException {

    public AnitrackDomainException(String message) {
        super(message);
    }

    public AnitrackDomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 2: 编写失败的 AnimeTest**

```java
package com.anitrack.domain.anime.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AnimeTest {

    @Test
    void fromBangumi_whenCalled_shouldCreateAnimeWithoutLocalId() {
        // when
        Anime anime = Anime.fromBangumi(100L, "中文名", "Original Title", "http://cover.jpg", 12,
            LocalDate.of(2024, 1, 1), "简介");

        // then
        assertThat(anime.getId()).isNull();
        assertThat(anime.getBangumiId()).isEqualTo(100L);
        assertThat(anime.getTitleCn()).isEqualTo("中文名");
        assertThat(anime.getTitleOriginal()).isEqualTo("Original Title");
        assertThat(anime.getCoverUrl()).isEqualTo("http://cover.jpg");
        assertThat(anime.getTotalEpisodes()).isEqualTo(12);
        assertThat(anime.getAirDate()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(anime.getSummary()).isEqualTo("简介");
    }

    @Test
    void reconstitute_whenCalled_shouldCreateAnimeWithLocalId() {
        // when
        Anime anime = Anime.reconstitute(1L, 100L, "中文名", "Original Title", "http://cover.jpg", 12,
            LocalDate.of(2024, 1, 1), "简介");

        // then
        assertThat(anime.getId()).isEqualTo(1L);
        assertThat(anime.getBangumiId()).isEqualTo(100L);
        assertThat(anime.getTitleCn()).isEqualTo("中文名");
    }
}
```

- [ ] **Step 3: 运行测试，确认失败**

Run: `mvn -q -pl anitrack-domain -am test -Dtest=AnimeTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL（`Anime` 类不存在，编译错误）

- [ ] **Step 4: 编写 Anime 聚合根**

```java
package com.anitrack.domain.anime.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Anime {

    private Long id;
    private Long bangumiId;
    private String titleCn;
    private String titleOriginal;
    private String coverUrl;
    private Integer totalEpisodes;
    private LocalDate airDate;
    private String summary;

    public static Anime fromBangumi(Long bangumiId, String titleCn, String titleOriginal, String coverUrl,
                                     Integer totalEpisodes, LocalDate airDate, String summary) {
        return Anime.builder()
            .bangumiId(bangumiId)
            .titleCn(titleCn)
            .titleOriginal(titleOriginal)
            .coverUrl(coverUrl)
            .totalEpisodes(totalEpisodes)
            .airDate(airDate)
            .summary(summary)
            .build();
    }

    public static Anime reconstitute(Long id, Long bangumiId, String titleCn, String titleOriginal, String coverUrl,
                                      Integer totalEpisodes, LocalDate airDate, String summary) {
        return Anime.builder()
            .id(id)
            .bangumiId(bangumiId)
            .titleCn(titleCn)
            .titleOriginal(titleOriginal)
            .coverUrl(coverUrl)
            .totalEpisodes(totalEpisodes)
            .airDate(airDate)
            .summary(summary)
            .build();
    }
}
```

- [ ] **Step 5: 编写 BangumiGateway 接口**

```java
package com.anitrack.domain.anime.gateway;

import com.anitrack.domain.anime.model.Anime;

import java.util.List;

public interface BangumiGateway {

    List<Anime> search(String keyword);
}
```

- [ ] **Step 6: 编写 AnimeRepo 接口**

```java
package com.anitrack.domain.anime.repo;

import com.anitrack.domain.anime.model.Anime;

public interface AnimeRepo {

    Anime getById(Long id);

    Anime getByBangumiId(Long bangumiId);

    Anime upsert(Anime anime);
}
```

- [ ] **Step 7: 编写 BangumiApiException**

```java
package com.anitrack.domain.anime.exception;

import com.anitrack.domain.common.AnitrackDomainException;

public class BangumiApiException extends AnitrackDomainException {

    public BangumiApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 8: 运行测试，确认通过**

Run: `mvn -q -pl anitrack-domain -am test -Dtest=AnimeTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS（2 个测试通过）

- [ ] **Step 9: Commit**

```bash
git add anitrack-domain/src/main/java/com/anitrack/domain/common/AnitrackDomainException.java anitrack-domain/src/main/java/com/anitrack/domain/anime anitrack-domain/src/test/java/com/anitrack/domain/anime
git commit -m "feat: 新增Anime聚合根、AnimeRepo/BangumiGateway仓储网关接口与BangumiApiException"
```

---

### Task 2: t_anime 表 Flyway 脚本 + AnimePO + AnimeMapper

**Files:**
- Create: `anitrack-starter/src/main/resources/db/migration/V2__create_anime_table.sql`
- Create: `anitrack-infrastructure/src/main/java/com/anitrack/infra/dal/po/AnimePO.java`
- Create: `anitrack-infrastructure/src/main/java/com/anitrack/infra/dal/mapper/AnimeMapper.java`
- Create: `anitrack-infrastructure/src/main/resources/mapper/AnimeMapper.xml`

**Interfaces:**
- Consumes：无（本任务不依赖 Task 1 的 Java 类型）
- Produces：`AnimeMapper.selectById(Long)`、`selectByBangumiId(Long)`、`insert(AnimePO)`、`updateById(AnimePO)`，供 Task 3 的 `AnimeRepoImpl` 调用

- [ ] **Step 1: 编写 Flyway 建表脚本**

```sql
CREATE TABLE t_anime (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    bangumi_id BIGINT NOT NULL COMMENT 'Bangumi外部条目ID',
    title_cn VARCHAR(255) DEFAULT NULL COMMENT '中文名，对应Bangumi name_cn',
    title_original VARCHAR(255) NOT NULL COMMENT '原名，对应Bangumi name',
    cover_url VARCHAR(512) DEFAULT NULL COMMENT '封面图URL',
    total_episodes INT DEFAULT NULL COMMENT '总集数，取Bangumi的eps字段，0或NULL表示未知',
    air_date DATE DEFAULT NULL COMMENT '放送日期，Bangumi该字段非必填',
    summary TEXT COMMENT '简介',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_bangumi_id (bangumi_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='番剧目录本地缓存表';
```

- [ ] **Step 2: 编写 AnimePO**

```java
package com.anitrack.infra.dal.po;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class AnimePO {

    private Long id;
    private Long bangumiId;
    private String titleCn;
    private String titleOriginal;
    private String coverUrl;
    private Integer totalEpisodes;
    private LocalDate airDate;
    private String summary;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

- [ ] **Step 3: 编写 AnimeMapper 接口**

```java
package com.anitrack.infra.dal.mapper;

import com.anitrack.infra.dal.po.AnimePO;
import org.apache.ibatis.annotations.Param;

public interface AnimeMapper {

    AnimePO selectById(@Param("id") Long id);

    AnimePO selectByBangumiId(@Param("bangumiId") Long bangumiId);

    int insert(AnimePO po);

    int updateById(AnimePO po);
}
```

- [ ] **Step 4: 编写 AnimeMapper.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.anitrack.infra.dal.mapper.AnimeMapper">

    <resultMap id="BaseResultMap" type="com.anitrack.infra.dal.po.AnimePO">
        <id column="id" property="id"/>
        <result column="bangumi_id" property="bangumiId"/>
        <result column="title_cn" property="titleCn"/>
        <result column="title_original" property="titleOriginal"/>
        <result column="cover_url" property="coverUrl"/>
        <result column="total_episodes" property="totalEpisodes"/>
        <result column="air_date" property="airDate"/>
        <result column="summary" property="summary"/>
        <result column="create_time" property="createTime"/>
        <result column="update_time" property="updateTime"/>
    </resultMap>

    <sql id="Base_Column_List">
        id, bangumi_id, title_cn, title_original, cover_url, total_episodes, air_date, summary, create_time, update_time
    </sql>

    <select id="selectById" resultMap="BaseResultMap">
        SELECT
        <include refid="Base_Column_List"/>
        FROM t_anime
        WHERE id = #{id}
    </select>

    <select id="selectByBangumiId" resultMap="BaseResultMap">
        SELECT
        <include refid="Base_Column_List"/>
        FROM t_anime
        WHERE bangumi_id = #{bangumiId}
    </select>

    <insert id="insert" parameterType="com.anitrack.infra.dal.po.AnimePO" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO t_anime (bangumi_id, title_cn, title_original, cover_url, total_episodes, air_date, summary)
        VALUES (#{bangumiId}, #{titleCn}, #{titleOriginal}, #{coverUrl}, #{totalEpisodes}, #{airDate}, #{summary})
    </insert>

    <update id="updateById" parameterType="com.anitrack.infra.dal.po.AnimePO">
        UPDATE t_anime
        SET title_cn = #{titleCn},
            title_original = #{titleOriginal},
            cover_url = #{coverUrl},
            total_episodes = #{totalEpisodes},
            air_date = #{airDate},
            summary = #{summary}
        WHERE id = #{id}
    </update>

</mapper>
```

- [ ] **Step 5: 验证模块编译通过**

Run: `mvn -q -pl anitrack-infrastructure -am compile`
Expected: `BUILD SUCCESS`（此阶段无法验证 SQL/XML 与真实数据库的一致性，需等 Task 7 端到端联调时用真实 MySQL 验证）

- [ ] **Step 6: Commit**

```bash
git add anitrack-starter/src/main/resources/db/migration/V2__create_anime_table.sql anitrack-infrastructure/src/main/java/com/anitrack/infra/dal/po/AnimePO.java anitrack-infrastructure/src/main/java/com/anitrack/infra/dal/mapper/AnimeMapper.java anitrack-infrastructure/src/main/resources/mapper/AnimeMapper.xml
git commit -m "feat: 新增t_anime表结构与AnimePO/AnimeMapper"
```

---

### Task 3: AnimeConverter（MapStruct）+ AnimeRepoImpl（含 upsert 单测）

**Files:**
- Create: `anitrack-infrastructure/src/main/java/com/anitrack/infra/converter/AnimeConverter.java`
- Create: `anitrack-infrastructure/src/main/java/com/anitrack/infra/repo/AnimeRepoImpl.java`
- Test: `anitrack-infrastructure/src/test/java/com/anitrack/infra/repo/AnimeRepoImplTest.java`

**Interfaces:**
- Consumes：`Anime`/`AnimeRepo`（Task 1）、`AnimePO`/`AnimeMapper`（Task 2）
- Produces：`AnimeRepoImpl implements AnimeRepo`（`@Repository`），供 `anitrack-application` 通过 `AnimeRepo` 接口间接使用

- [ ] **Step 1: 编写 AnimeConverter**

```java
package com.anitrack.infra.converter;

import com.anitrack.domain.anime.model.Anime;
import com.anitrack.infra.dal.po.AnimePO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AnimeConverter {

    AnimePO toPO(Anime anime);

    Anime toDomain(AnimePO po);
}
```

`Anime.reconstitute(id, bangumiId, titleCn, titleOriginal, coverUrl, totalEpisodes, airDate, summary)` 的参数名与 `AnimePO` 字段一一对应，MapStruct 会自动选用该静态工厂方法构造 `Anime`（与现有 `UserConverter` 使用 `User.reconstitute()` 的方式一致）。

- [ ] **Step 2: 编写失败的 AnimeRepoImplTest**

```java
package com.anitrack.infra.repo;

import com.anitrack.domain.anime.model.Anime;
import com.anitrack.infra.converter.AnimeConverter;
import com.anitrack.infra.dal.mapper.AnimeMapper;
import com.anitrack.infra.dal.po.AnimePO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnimeRepoImplTest {

    @Mock
    private AnimeMapper mockAnimeMapper;

    @Mock
    private AnimeConverter mockAnimeConverter;

    @InjectMocks
    private AnimeRepoImpl sut;

    @Test
    void upsert_whenBangumiIdNotExists_shouldInsert() {
        // given
        Anime anime = Anime.fromBangumi(100L, "中文名", "Original Title", "http://cover.jpg", 12,
            LocalDate.of(2024, 1, 1), "简介");
        AnimePO po = new AnimePO();
        po.setBangumiId(100L);
        Anime persisted = Anime.reconstitute(1L, 100L, "中文名", "Original Title", "http://cover.jpg", 12,
            LocalDate.of(2024, 1, 1), "简介");
        when(mockAnimeMapper.selectByBangumiId(100L)).thenReturn(null);
        when(mockAnimeConverter.toPO(anime)).thenReturn(po);
        when(mockAnimeConverter.toDomain(po)).thenReturn(persisted);

        // when
        Anime result = sut.upsert(anime);

        // then
        verify(mockAnimeMapper, times(1)).insert(po);
        verify(mockAnimeMapper, never()).updateById(any());
        assertThat(result).isEqualTo(persisted);
    }

    @Test
    void upsert_whenBangumiIdExists_shouldUpdateWithExistingId() {
        // given
        Anime anime = Anime.fromBangumi(100L, "中文名", "Original Title", "http://cover.jpg", 12,
            LocalDate.of(2024, 1, 1), "简介");
        AnimePO existing = new AnimePO();
        existing.setId(1L);
        existing.setBangumiId(100L);
        AnimePO po = new AnimePO();
        po.setBangumiId(100L);
        when(mockAnimeMapper.selectByBangumiId(100L)).thenReturn(existing);
        when(mockAnimeConverter.toPO(anime)).thenReturn(po);
        when(mockAnimeConverter.toDomain(po)).thenReturn(anime);

        // when
        sut.upsert(anime);

        // then
        ArgumentCaptor<AnimePO> captor = ArgumentCaptor.forClass(AnimePO.class);
        verify(mockAnimeMapper, times(1)).updateById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(1L);
        verify(mockAnimeMapper, never()).insert(any());
    }

    @Test
    void getById_whenNotFound_shouldReturnNull() {
        // given
        when(mockAnimeMapper.selectById(999L)).thenReturn(null);

        // when
        Anime result = sut.getById(999L);

        // then
        assertThat(result).isNull();
    }
}
```

- [ ] **Step 3: 运行测试，确认失败**

Run: `mvn -q -pl anitrack-infrastructure -am test -Dtest=AnimeRepoImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL（`AnimeRepoImpl` 类不存在，编译错误）

- [ ] **Step 4: 编写 AnimeRepoImpl**

```java
package com.anitrack.infra.repo;

import com.anitrack.domain.anime.model.Anime;
import com.anitrack.domain.anime.repo.AnimeRepo;
import com.anitrack.infra.converter.AnimeConverter;
import com.anitrack.infra.dal.mapper.AnimeMapper;
import com.anitrack.infra.dal.po.AnimePO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AnimeRepoImpl implements AnimeRepo {

    private final AnimeMapper animeMapper;
    private final AnimeConverter animeConverter;

    @Override
    public Anime getById(Long id) {
        AnimePO po = animeMapper.selectById(id);
        return po == null ? null : animeConverter.toDomain(po);
    }

    @Override
    public Anime getByBangumiId(Long bangumiId) {
        AnimePO po = animeMapper.selectByBangumiId(bangumiId);
        return po == null ? null : animeConverter.toDomain(po);
    }

    @Override
    public Anime upsert(Anime anime) {
        AnimePO existing = animeMapper.selectByBangumiId(anime.getBangumiId());
        AnimePO po = animeConverter.toPO(anime);
        if (existing == null) {
            animeMapper.insert(po);
        } else {
            po.setId(existing.getId());
            animeMapper.updateById(po);
        }
        return animeConverter.toDomain(po);
    }
}
```

- [ ] **Step 5: 运行测试，确认通过**

Run: `mvn -q -pl anitrack-infrastructure -am test -Dtest=AnimeRepoImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS（3 个测试通过），且 `target/generated-sources/annotations` 下生成 `AnimeConverterImpl`

- [ ] **Step 6: Commit**

```bash
git add anitrack-infrastructure/src/main/java/com/anitrack/infra/converter/AnimeConverter.java anitrack-infrastructure/src/main/java/com/anitrack/infra/repo/AnimeRepoImpl.java anitrack-infrastructure/src/test/java/com/anitrack/infra/repo
git commit -m "feat: 新增AnimeConverter与AnimeRepoImpl仓储实现"
```

---

### Task 4: Bangumi DTO + RestClient配置 + BangumiConverter + BangumiGatewayImpl

**Files:**
- Modify: `anitrack-infrastructure/pom.xml`（新增 `spring-web`、`spring-boot-starter-json` 依赖）
- Create: `anitrack-infrastructure/src/main/java/com/anitrack/infra/gateway/bangumi/dto/BangumiSearchRequestDTO.java`
- Create: `anitrack-infrastructure/src/main/java/com/anitrack/infra/gateway/bangumi/dto/BangumiSearchResponseDTO.java`
- Create: `anitrack-infrastructure/src/main/java/com/anitrack/infra/gateway/bangumi/dto/BangumiSubjectDTO.java`
- Create: `anitrack-infrastructure/src/main/java/com/anitrack/infra/gateway/bangumi/dto/BangumiImagesDTO.java`
- Create: `anitrack-infrastructure/src/main/java/com/anitrack/infra/config/BangumiProperties.java`
- Create: `anitrack-infrastructure/src/main/java/com/anitrack/infra/config/BangumiClientConfig.java`
- Create: `anitrack-infrastructure/src/main/java/com/anitrack/infra/gateway/bangumi/BangumiConverter.java`
- Create: `anitrack-infrastructure/src/main/java/com/anitrack/infra/gateway/bangumi/BangumiGatewayImpl.java`
- Test: `anitrack-infrastructure/src/test/java/com/anitrack/infra/gateway/bangumi/BangumiConverterTest.java`

**Interfaces:**
- Consumes：`Anime`/`BangumiGateway`/`BangumiApiException`（Task 1）
- Produces：`BangumiGatewayImpl implements BangumiGateway`（`@Component`），供 `anitrack-application` 通过 `BangumiGateway` 接口间接使用；`RestClient` bean（供本任务内部使用，不对外暴露）

- [ ] **Step 1: 给 anitrack-infrastructure/pom.xml 新增依赖**

在 `<dependencies>` 中 `spring-boot-starter` 依赖之后新增：

```xml
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-web</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-json</artifactId>
</dependency>
```

`spring-web` 提供 `RestClient`；`spring-boot-starter-json` 提供 Jackson（`RestClient` 序列化/反序列化请求体依赖 classpath 上的 Jackson）。两者版本均由父 POM 的 `spring-boot-starter-parent` BOM 管理，不单独指定版本号。

- [ ] **Step 2: 编写 Bangumi 外部 DTO**

```java
package com.anitrack.infra.gateway.bangumi.dto;

import lombok.Data;

import java.util.List;

@Data
public class BangumiSearchRequestDTO {

    private String keyword;
    private Filter filter;

    @Data
    public static class Filter {
        private List<Integer> type;
    }

    public static BangumiSearchRequestDTO forAnimeKeyword(String keyword) {
        BangumiSearchRequestDTO request = new BangumiSearchRequestDTO();
        request.setKeyword(keyword);
        Filter filter = new Filter();
        filter.setType(List.of(2));
        request.setFilter(filter);
        return request;
    }
}
```

```java
package com.anitrack.infra.gateway.bangumi.dto;

import lombok.Data;

@Data
public class BangumiImagesDTO {

    private String large;
    private String common;
    private String medium;
    private String small;
    private String grid;
}
```

```java
package com.anitrack.infra.gateway.bangumi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class BangumiSubjectDTO {

    private Long id;
    private String name;

    @JsonProperty("name_cn")
    private String nameCn;

    private String summary;
    private String date;
    private Integer eps;

    @JsonProperty("total_episodes")
    private Integer totalEpisodes;

    private BangumiImagesDTO images;
}
```

```java
package com.anitrack.infra.gateway.bangumi.dto;

import lombok.Data;

import java.util.List;

@Data
public class BangumiSearchResponseDTO {

    private Integer total;
    private Integer limit;
    private Integer offset;
    private List<BangumiSubjectDTO> data;
}
```

- [ ] **Step 3: 编写 BangumiProperties 与 BangumiClientConfig**

```java
package com.anitrack.infra.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "anitrack.bangumi")
public class BangumiProperties {

    private String baseUrl;
    private String userAgent;
    private Integer connectTimeoutMs;
    private Integer readTimeoutMs;
}
```

```java
package com.anitrack.infra.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(BangumiProperties.class)
@RequiredArgsConstructor
public class BangumiClientConfig {

    private final BangumiProperties bangumiProperties;

    @Bean
    public RestClient bangumiRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(bangumiProperties.getConnectTimeoutMs());
        requestFactory.setReadTimeout(bangumiProperties.getReadTimeoutMs());

        return RestClient.builder()
            .baseUrl(bangumiProperties.getBaseUrl())
            .defaultHeader(HttpHeaders.USER_AGENT, bangumiProperties.getUserAgent())
            .requestFactory(requestFactory)
            .build();
    }
}
```

- [ ] **Step 4: 编写失败的 BangumiConverterTest**

```java
package com.anitrack.infra.gateway.bangumi;

import com.anitrack.domain.anime.model.Anime;
import com.anitrack.infra.gateway.bangumi.dto.BangumiImagesDTO;
import com.anitrack.infra.gateway.bangumi.dto.BangumiSubjectDTO;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class BangumiConverterTest {

    private final BangumiConverter sut = new BangumiConverter();

    @Test
    void toDomain_whenAllFieldsPresent_shouldMapAllFields() {
        // given
        BangumiSubjectDTO dto = new BangumiSubjectDTO();
        dto.setId(100L);
        dto.setName("Original Title");
        dto.setNameCn("中文名");
        dto.setSummary("简介");
        dto.setDate("2024-01-01");
        dto.setEps(12);
        dto.setTotalEpisodes(10);
        BangumiImagesDTO images = new BangumiImagesDTO();
        images.setLarge("http://cover.jpg");
        dto.setImages(images);

        // when
        Anime anime = sut.toDomain(dto);

        // then
        assertThat(anime.getBangumiId()).isEqualTo(100L);
        assertThat(anime.getTitleOriginal()).isEqualTo("Original Title");
        assertThat(anime.getTitleCn()).isEqualTo("中文名");
        assertThat(anime.getSummary()).isEqualTo("简介");
        assertThat(anime.getAirDate()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(anime.getTotalEpisodes()).isEqualTo(12);
        assertThat(anime.getCoverUrl()).isEqualTo("http://cover.jpg");
    }

    @Test
    void toDomain_whenDateIsMissing_shouldMapAirDateAsNull() {
        // given
        BangumiSubjectDTO dto = new BangumiSubjectDTO();
        dto.setId(100L);
        dto.setName("Original Title");
        dto.setNameCn("");
        dto.setEps(0);
        dto.setDate(null);

        // when
        Anime anime = sut.toDomain(dto);

        // then
        assertThat(anime.getAirDate()).isNull();
        assertThat(anime.getTitleCn()).isEmpty();
        assertThat(anime.getTotalEpisodes()).isZero();
    }

    @Test
    void toDomain_whenImagesIsNull_shouldMapCoverUrlAsNull() {
        // given
        BangumiSubjectDTO dto = new BangumiSubjectDTO();
        dto.setId(100L);
        dto.setName("Original Title");
        dto.setImages(null);

        // when
        Anime anime = sut.toDomain(dto);

        // then
        assertThat(anime.getCoverUrl()).isNull();
    }
}
```

- [ ] **Step 5: 运行测试，确认失败**

Run: `mvn -q -pl anitrack-infrastructure -am test -Dtest=BangumiConverterTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL（`BangumiConverter` 类不存在，编译错误）

- [ ] **Step 6: 编写 BangumiConverter**

```java
package com.anitrack.infra.gateway.bangumi;

import com.anitrack.domain.anime.model.Anime;
import com.anitrack.infra.gateway.bangumi.dto.BangumiSubjectDTO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

@Component
public class BangumiConverter {

    public Anime toDomain(BangumiSubjectDTO dto) {
        String coverUrl = dto.getImages() == null ? null : dto.getImages().getLarge();
        return Anime.fromBangumi(
            dto.getId(),
            dto.getNameCn(),
            dto.getName(),
            coverUrl,
            dto.getEps(),
            parseAirDate(dto.getDate()),
            dto.getSummary()
        );
    }

    private LocalDate parseAirDate(String date) {
        return StringUtils.hasText(date) ? LocalDate.parse(date) : null;
    }
}
```

- [ ] **Step 7: 运行测试，确认通过**

Run: `mvn -q -pl anitrack-infrastructure -am test -Dtest=BangumiConverterTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS（3 个测试通过）

- [ ] **Step 8: 编写 BangumiGatewayImpl**

```java
package com.anitrack.infra.gateway.bangumi;

import com.anitrack.domain.anime.exception.BangumiApiException;
import com.anitrack.domain.anime.gateway.BangumiGateway;
import com.anitrack.domain.anime.model.Anime;
import com.anitrack.infra.gateway.bangumi.dto.BangumiSearchRequestDTO;
import com.anitrack.infra.gateway.bangumi.dto.BangumiSearchResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Component
@RequiredArgsConstructor
public class BangumiGatewayImpl implements BangumiGateway {

    private final RestClient bangumiRestClient;
    private final BangumiConverter bangumiConverter;

    @Override
    public List<Anime> search(String keyword) {
        BangumiSearchResponseDTO response;
        try {
            response = bangumiRestClient.post()
                .uri("/v0/search/subjects?limit=20")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BangumiSearchRequestDTO.forAnimeKeyword(keyword))
                .retrieve()
                .body(BangumiSearchResponseDTO.class);
        } catch (RestClientException e) {
            throw new BangumiApiException("调用Bangumi搜索接口失败, keyword=" + keyword, e);
        }

        if (response == null || response.getData() == null) {
            return List.of();
        }
        return response.getData().stream()
            .map(bangumiConverter::toDomain)
            .toList();
    }
}
```

- [ ] **Step 9: 验证模块编译通过（含 MapStruct/Jackson 注解处理）**

Run: `mvn -q -pl anitrack-infrastructure -am compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 10: Commit**

```bash
git add anitrack-infrastructure/pom.xml anitrack-infrastructure/src/main/java/com/anitrack/infra/gateway anitrack-infrastructure/src/main/java/com/anitrack/infra/config anitrack-infrastructure/src/test/java/com/anitrack/infra/gateway
git commit -m "feat: 新增BangumiGatewayImpl实现真实Bangumi API搜索调用与DTO转换"
```

---

### Task 5: AppExceptionEnum 扩展 + AnimeBO + AnimeApplication

**Files:**
- Modify: `anitrack-application/src/main/java/com/anitrack/application/exception/AppExceptionEnum.java`
- Create: `anitrack-application/src/main/java/com/anitrack/application/model/AnimeBO.java`
- Create: `anitrack-application/src/main/java/com/anitrack/application/service/AnimeApplication.java`
- Test: `anitrack-application/src/test/java/com/anitrack/application/service/AnimeApplicationTest.java`

**Interfaces:**
- Consumes：`Anime`/`AnimeRepo`/`BangumiGateway`/`BangumiApiException`（Task 1）、`AnitrackAppException`（已存在）
- Produces：`AnimeApplication.searchAnime(String keyword): List<AnimeBO>`、`AnimeApplication.getAnimeDetail(Long animeId): AnimeBO`，供 Task 6 的 `AnimeController` 调用

- [ ] **Step 1: 给 AppExceptionEnum 新增常量**

```java
package com.anitrack.application.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AppExceptionEnum {

    USERNAME_ALREADY_EXISTS(40001, "用户名已存在"),
    LOGIN_FAILED(40002, "用户名或密码错误"),
    BANGUMI_SERVICE_UNAVAILABLE(40101, "番剧信息服务暂时不可用，请稍后重试"),
    ANIME_NOT_FOUND(40102, "番剧不存在");

    private final int code;
    private final String message;
}
```

- [ ] **Step 2: 编写 AnimeBO**

```java
package com.anitrack.application.model;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class AnimeBO {

    private Long id;
    private Long bangumiId;
    private String titleCn;
    private String titleOriginal;
    private String coverUrl;
    private Integer totalEpisodes;
    private LocalDate airDate;
    private String summary;
}
```

- [ ] **Step 3: 编写失败的 AnimeApplicationTest**

```java
package com.anitrack.application.service;

import com.anitrack.application.exception.AnitrackAppException;
import com.anitrack.application.model.AnimeBO;
import com.anitrack.domain.anime.exception.BangumiApiException;
import com.anitrack.domain.anime.gateway.BangumiGateway;
import com.anitrack.domain.anime.model.Anime;
import com.anitrack.domain.anime.repo.AnimeRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnimeApplicationTest {

    @Mock
    private BangumiGateway mockBangumiGateway;

    @Mock
    private AnimeRepo mockAnimeRepo;

    @InjectMocks
    private AnimeApplication sut;

    @Test
    void searchAnime_whenGatewaySucceeds_shouldUpsertAndReturnBOList() {
        // given
        Anime searchResult = Anime.fromBangumi(100L, "中文名", "Original Title", "http://cover.jpg", 12,
            LocalDate.of(2024, 1, 1), "简介");
        Anime persisted = Anime.reconstitute(1L, 100L, "中文名", "Original Title", "http://cover.jpg", 12,
            LocalDate.of(2024, 1, 1), "简介");
        when(mockBangumiGateway.search("关键字")).thenReturn(List.of(searchResult));
        when(mockAnimeRepo.upsert(searchResult)).thenReturn(persisted);

        // when
        List<AnimeBO> result = sut.searchAnime("关键字");

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(0).getTitleCn()).isEqualTo("中文名");
        verify(mockAnimeRepo, times(1)).upsert(searchResult);
    }

    @Test
    void searchAnime_whenGatewayThrowsBangumiApiException_shouldThrowAppException() {
        // given
        when(mockBangumiGateway.search("关键字"))
            .thenThrow(new BangumiApiException("调用失败", new RuntimeException("timeout")));

        // when & then
        assertThatThrownBy(() -> sut.searchAnime("关键字"))
            .isInstanceOf(AnitrackAppException.class)
            .hasMessageContaining("番剧信息服务暂时不可用");

        verify(mockAnimeRepo, never()).upsert(any());
    }

    @Test
    void getAnimeDetail_whenAnimeExists_shouldReturnBO() {
        // given
        Anime anime = Anime.reconstitute(1L, 100L, "中文名", "Original Title", "http://cover.jpg", 12,
            LocalDate.of(2024, 1, 1), "简介");
        when(mockAnimeRepo.getById(1L)).thenReturn(anime);

        // when
        AnimeBO result = sut.getAnimeDetail(1L);

        // then
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getTitleOriginal()).isEqualTo("Original Title");
    }

    @Test
    void getAnimeDetail_whenAnimeNotFound_shouldThrowException() {
        // given
        when(mockAnimeRepo.getById(999L)).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> sut.getAnimeDetail(999L))
            .isInstanceOf(AnitrackAppException.class)
            .hasMessageContaining("番剧不存在");
    }
}
```

- [ ] **Step 4: 运行测试，确认失败**

Run: `mvn -q -pl anitrack-application -am test -Dtest=AnimeApplicationTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL（`AnimeApplication` 类不存在，编译错误）

- [ ] **Step 5: 编写 AnimeApplication**

```java
package com.anitrack.application.service;

import com.anitrack.application.exception.AnitrackAppException;
import com.anitrack.application.exception.AppExceptionEnum;
import com.anitrack.application.model.AnimeBO;
import com.anitrack.domain.anime.exception.BangumiApiException;
import com.anitrack.domain.anime.gateway.BangumiGateway;
import com.anitrack.domain.anime.model.Anime;
import com.anitrack.domain.anime.repo.AnimeRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnimeApplication {

    private final BangumiGateway bangumiGateway;
    private final AnimeRepo animeRepo;

    public List<AnimeBO> searchAnime(String keyword) {
        List<Anime> searchResults;
        try {
            searchResults = bangumiGateway.search(keyword);
        } catch (BangumiApiException e) {
            log.error("调用Bangumi搜索接口失败, keyword={}", keyword, e);
            throw new AnitrackAppException(AppExceptionEnum.BANGUMI_SERVICE_UNAVAILABLE);
        }
        return searchResults.stream()
            .map(animeRepo::upsert)
            .map(this::toBO)
            .toList();
    }

    public AnimeBO getAnimeDetail(Long animeId) {
        Anime anime = animeRepo.getById(animeId);
        if (anime == null) {
            throw new AnitrackAppException(AppExceptionEnum.ANIME_NOT_FOUND);
        }
        return toBO(anime);
    }

    private AnimeBO toBO(Anime anime) {
        return AnimeBO.builder()
            .id(anime.getId())
            .bangumiId(anime.getBangumiId())
            .titleCn(anime.getTitleCn())
            .titleOriginal(anime.getTitleOriginal())
            .coverUrl(anime.getCoverUrl())
            .totalEpisodes(anime.getTotalEpisodes())
            .airDate(anime.getAirDate())
            .summary(anime.getSummary())
            .build();
    }
}
```

- [ ] **Step 6: 运行测试，确认通过**

Run: `mvn -q -pl anitrack-application -am test -Dtest=AnimeApplicationTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS（4 个测试通过）

- [ ] **Step 7: Commit**

```bash
git add anitrack-application/src/main/java/com/anitrack/application/exception/AppExceptionEnum.java anitrack-application/src/main/java/com/anitrack/application/model/AnimeBO.java anitrack-application/src/main/java/com/anitrack/application/service/AnimeApplication.java anitrack-application/src/test/java/com/anitrack/application/service/AnimeApplicationTest.java
git commit -m "feat: 新增AnimeApplication搜索/详情用例编排"
```

---

### Task 6: AnimeController（搜索/详情接口）+ Web 层集成测试

**Files:**
- Create: `anitrack-starter/src/main/java/com/anitrack/starter/request/AnimeSearchReq.java`
- Create: `anitrack-starter/src/main/java/com/anitrack/starter/request/AnimeDetailReq.java`
- Create: `anitrack-starter/src/main/java/com/anitrack/starter/response/AnimeResponse.java`
- Modify: `anitrack-starter/src/main/java/com/anitrack/starter/converter/HttpConverter.java`
- Create: `anitrack-starter/src/main/java/com/anitrack/starter/controller/AnimeController.java`
- Test: `anitrack-starter/src/test/java/com/anitrack/starter/controller/AnimeControllerTest.java`

**Interfaces:**
- Consumes：`AnimeApplication`（Task 5）、`ResponseResult`（已存在）
- Produces：`POST /api/anime/search`、`POST /api/anime/detail`

- [ ] **Step 1: 编写请求对象**

```java
package com.anitrack.starter.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class AnimeSearchReq {

    @NotBlank(message = "搜索关键字不能为空")
    private String keyword;
}
```

```java
package com.anitrack.starter.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class AnimeDetailReq {

    @NotNull(message = "番剧ID不能为空")
    private Long animeId;
}
```

- [ ] **Step 2: 编写 AnimeResponse**

```java
package com.anitrack.starter.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class AnimeResponse {

    private Long id;
    private Long bangumiId;
    private String titleCn;
    private String titleOriginal;
    private String coverUrl;
    private Integer totalEpisodes;
    private LocalDate airDate;
    private String summary;
}
```

- [ ] **Step 3: 给 HttpConverter 新增转换方法**

在现有 `HttpConverter` 类中新增以下两个方法（保留已有 User 相关方法不变）：

```java
public AnimeResponse animeBO2Response(AnimeBO bo) {
    return AnimeResponse.builder()
        .id(bo.getId())
        .bangumiId(bo.getBangumiId())
        .titleCn(bo.getTitleCn())
        .titleOriginal(bo.getTitleOriginal())
        .coverUrl(bo.getCoverUrl())
        .totalEpisodes(bo.getTotalEpisodes())
        .airDate(bo.getAirDate())
        .summary(bo.getSummary())
        .build();
}

public List<AnimeResponse> animeBOList2Response(List<AnimeBO> boList) {
    return boList.stream().map(this::animeBO2Response).toList();
}
```

对应新增 import：`com.anitrack.application.model.AnimeBO`、`com.anitrack.starter.response.AnimeResponse`、`java.util.List`。

- [ ] **Step 4: 编写失败的 AnimeControllerTest**

```java
package com.anitrack.starter.controller;

import com.anitrack.application.model.AnimeBO;
import com.anitrack.application.service.AnimeApplication;
import com.anitrack.starter.converter.HttpConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnimeController.class)
class AnimeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AnimeApplication mockAnimeApplication;

    @MockBean
    private HttpConverter mockHttpConverter;

    private AnimeBO createTestAnimeBO() {
        return AnimeBO.builder()
            .id(1L)
            .bangumiId(100L)
            .titleCn("中文名")
            .titleOriginal("Original Title")
            .coverUrl("http://cover.jpg")
            .totalEpisodes(12)
            .airDate(LocalDate.of(2024, 1, 1))
            .summary("简介")
            .build();
    }

    @Test
    void postSearch_whenRequestBodyIsEmptyObject_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/anime/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void postSearch_whenRequestIsValid_shouldReturnAnimeList() throws Exception {
        // given
        AnimeBO animeBO = createTestAnimeBO();
        when(mockAnimeApplication.searchAnime("关键字")).thenReturn(List.of(animeBO));
        when(mockHttpConverter.animeBOList2Response(List.of(animeBO))).thenCallRealMethod();
        when(mockHttpConverter.animeBO2Response(animeBO)).thenCallRealMethod();

        // when & then
        mockMvc.perform(post("/api/anime/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("keyword", "关键字"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(1))
            .andExpect(jsonPath("$.data[0].titleCn").value("中文名"));

        verify(mockAnimeApplication, times(1)).searchAnime("关键字");
    }

    @Test
    void postDetail_whenRequestBodyIsEmptyObject_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/anime/detail")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void postDetail_whenRequestIsValid_shouldReturnAnimeDetail() throws Exception {
        // given
        AnimeBO animeBO = createTestAnimeBO();
        when(mockAnimeApplication.getAnimeDetail(1L)).thenReturn(animeBO);
        when(mockHttpConverter.animeBO2Response(animeBO)).thenCallRealMethod();

        // when & then
        mockMvc.perform(post("/api/anime/detail")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 1L))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(1))
            .andExpect(jsonPath("$.data.titleOriginal").value("Original Title"));

        verify(mockAnimeApplication, times(1)).getAnimeDetail(1L);
    }

    @Test
    void postDetail_whenAnimeNotFound_shouldReturnBusinessError() throws Exception {
        // given
        doThrow(new com.anitrack.application.exception.AnitrackAppException(
                com.anitrack.application.exception.AppExceptionEnum.ANIME_NOT_FOUND))
            .when(mockAnimeApplication).getAnimeDetail(999L);

        // when & then
        mockMvc.perform(post("/api/anime/detail")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("animeId", 999L))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(0));
    }
}
```

- [ ] **Step 5: 运行测试，确认失败**

Run: `mvn -q -pl anitrack-starter -am test -Dtest=AnimeControllerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL（`AnimeController` 类不存在，编译错误）

- [ ] **Step 6: 编写 AnimeController**

```java
package com.anitrack.starter.controller;

import com.anitrack.application.model.AnimeBO;
import com.anitrack.application.service.AnimeApplication;
import com.anitrack.starter.converter.HttpConverter;
import com.anitrack.starter.request.AnimeDetailReq;
import com.anitrack.starter.request.AnimeSearchReq;
import com.anitrack.starter.response.AnimeResponse;
import com.anitrack.starter.response.ResponseResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/anime")
@RequiredArgsConstructor
public class AnimeController {

    private final AnimeApplication animeApplication;
    private final HttpConverter httpConverter;

    @PostMapping("/search")
    public ResponseResult<List<AnimeResponse>> search(@Valid @RequestBody AnimeSearchReq req) {
        List<AnimeBO> result = animeApplication.searchAnime(req.getKeyword());
        return ResponseResult.success(httpConverter.animeBOList2Response(result));
    }

    @PostMapping("/detail")
    public ResponseResult<AnimeResponse> detail(@Valid @RequestBody AnimeDetailReq req) {
        AnimeBO result = animeApplication.getAnimeDetail(req.getAnimeId());
        return ResponseResult.success(httpConverter.animeBO2Response(result));
    }
}
```

- [ ] **Step 7: 运行测试，确认通过**

Run: `mvn -q -pl anitrack-starter -am test -Dtest=AnimeControllerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS（5 个测试通过）

- [ ] **Step 8: Commit**

```bash
git add anitrack-starter/src/main/java/com/anitrack/starter/request/AnimeSearchReq.java anitrack-starter/src/main/java/com/anitrack/starter/request/AnimeDetailReq.java anitrack-starter/src/main/java/com/anitrack/starter/response/AnimeResponse.java anitrack-starter/src/main/java/com/anitrack/starter/converter/HttpConverter.java anitrack-starter/src/main/java/com/anitrack/starter/controller/AnimeController.java anitrack-starter/src/test/java/com/anitrack/starter/controller/AnimeControllerTest.java
git commit -m "feat: 新增AnimeController番剧搜索/详情接口与Web层集成测试"
```

---

### Task 7: Bangumi 配置接入 application.yml + 端到端联调

**Files:**
- Modify: `anitrack-starter/src/main/resources/application.yml`

**Interfaces:**
- Consumes：`BangumiProperties`（Task 4）
- Produces：可运行的、真正对接 Bangumi API 的完整应用

- [ ] **Step 1: 给 application.yml 新增 Bangumi 配置**

在现有 `anitrack:` 顶级键下（与 `jwt:` 同级）新增 `bangumi:` 配置：

```yaml
anitrack:
  jwt:
    secret: ${JWT_SECRET}
    expiration: 86400000
  bangumi:
    base-url: https://api.bgm.tv
    user-agent: anitrack-practice/1.0 (https://github.com/your-name/anitrack)
    connect-timeout-ms: 3000
    read-timeout-ms: 5000
```

- [ ] **Step 2: 验证整个 Reactor 编译并测试通过**

Run: `mvn -q compile test`
Expected: 所有模块 `BUILD SUCCESS`，Task 1/3/4/5/6 编写的全部单测通过

- [ ] **Step 3: 端到端手动联调（需要本地 MySQL 与可访问 api.bgm.tv 的网络环境）**

准备本地 MySQL（沿用 Phase 0 的 `anitrack` 数据库，无需重建），设置环境变量：

```bash
export DB_USERNAME=root
export DB_PASSWORD=your-local-password
export JWT_SECRET=this-is-a-local-dev-secret-key-32bytes-min
```

启动应用：

```bash
mvn -q -pl anitrack-starter -am spring-boot:run
```

Expected: 控制台无异常，日志显示 Flyway 执行 `V2__create_anime_table.sql` 成功，`t_anime` 表已创建

新开终端，先注册并登录一个测试用户拿到 token（沿用 Phase 0 接口）：

```bash
curl -s -X POST http://localhost:8080/api/user/register \
  -H "Content-Type: application/json" \
  -d '{"username":"bob","password":"password123","nickname":"Bob"}'

TOKEN=$(curl -s -X POST http://localhost:8080/api/user/login \
  -H "Content-Type: application/json" \
  -d '{"username":"bob","password":"password123"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])")
```

测试搜索接口（真实调用 Bangumi API）：

```bash
curl -s -X POST http://localhost:8080/api/anime/search \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"keyword":"败犬女主太多了"}'
```

Expected: `{"status":1,"message":null,"data":[{"id":1,"bangumiId":...,"titleCn":"败犬女主太多了！","titleOriginal":"负けヒロインが多すぎる！",...}]}`（具体字段值以 Bangumi 实际返回为准，重点验证 `status=1`、`titleCn`/`titleOriginal` 均非空、返回的 `id` 是本地自增id）

记下返回结果中的 `id`（本地 animeId），测试详情接口（只读本地，不再调用 Bangumi）：

```bash
curl -s -X POST http://localhost:8080/api/anime/detail \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"animeId":1}'
```

Expected: 返回与搜索结果中该条目一致的详情

测试详情接口对不存在的 animeId 的处理：

```bash
curl -s -X POST http://localhost:8080/api/anime/detail \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"animeId":99999}'
```

Expected: `{"status":0,"message":"番剧不存在","data":null}`

再次执行同一关键字的搜索请求，验证 `upsert` 更新分支（而非重复插入违反 `uk_bangumi_id` 唯一索引）：

```bash
curl -s -X POST http://localhost:8080/api/anime/search \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"keyword":"败犬女主太多了"}'
```

Expected: 请求成功（`status=1`），返回的 `id` 与第一次搜索结果一致（说明命中 `updateById` 分支而非重复 `insert` 报唯一索引冲突）

验证完成后按 `Ctrl+C` 停止应用。

**若当前网络环境无法访问 `api.bgm.tv`**：本 Step 3 需要在具备外网访问权限的环境中执行；Step 1-2（配置与自动化测试）不依赖外网，可独立完成并验证。

- [ ] **Step 4: Commit**

```bash
git add anitrack-starter/src/main/resources/application.yml
git commit -m "feat: 接入Bangumi API配置，完成Phase 1a番剧目录端到端联调"
```

---

## Self-Review

**1. Spec 覆盖度**：本 spec（`docs/superpowers/specs/2026-07-05-anitrack-phase1-anime-catalog-design.md`）要求的领域模型（`Anime`/`AnimeRepo`/`BangumiGateway`/`BangumiApiException`，Task 1）、持久化层（`t_anime`/`AnimePO`/`AnimeMapper`/`AnimeRepoImpl`，Task 2-3）、ACL 防腐层（真实 Bangumi API 调用、DTO 隔离、`BangumiConverter` 字段映射，Task 4）、应用层（`AnimeApplication` 编排 + 异常转译，Task 5）、Web 层（`/api/anime/search`、`/api/anime/detail`，Task 6）、异常处理链路（`BangumiApiException → AnitrackAppException`，Task 4-5）均已覆盖；`titleCn`/`titleOriginal` 不做回退判断（Task 4 `BangumiConverterTest` 显式覆盖 `nameCn` 为空字符串场景）、`totalEpisodes` 取 `eps`（Task 4 `BangumiConverterTest` 覆盖）、`getAnimeDetail` 不回退调用 Bangumi（Task 5 `AnimeApplication.getAnimeDetail` 只依赖 `AnimeRepo`）均已在对应任务体现。测试策略中列出的 Domain/Infrastructure/Application/Web 四层测试均对应到 Task 1/3/4/5/6。

**2. 占位符扫描**：全文所有 Step 均为可直接运行的完整代码/命令，无 TODO/"参考 Task N 类似实现"/"添加适当异常处理"等占位表述。

**3. 类型一致性检查**：`Anime.fromBangumi`/`Anime.reconstitute`（Task 1 定义）在 Task 3 的 `AnimeRepoImplTest`、Task 4 的 `BangumiConverter`/`BangumiConverterTest`、Task 5 的 `AnimeApplicationTest` 中参数顺序（`bangumiId, titleCn, titleOriginal, coverUrl, totalEpisodes, airDate, summary`，`reconstitute` 前置 `id`）保持一致；`AnimeRepo` 三个方法（`getById`/`getByBangumiId`/`upsert`，Task 1 定义）与 `AnimeRepoImpl`（Task 3）、`AnimeApplication`（Task 5）中的调用签名一致；`BangumiGateway.search(String): List<Anime>`（Task 1）与 `BangumiGatewayImpl`（Task 4）、`AnimeApplicationTest`（Task 5）中的调用签名一致；`AnimeBO` 字段（Task 5 定义：`id/bangumiId/titleCn/titleOriginal/coverUrl/totalEpisodes/airDate/summary`）与 Task 6 的 `HttpConverter.animeBO2Response`/`AnimeResponse`/`AnimeControllerTest` 中的字段完全对应；`AppExceptionEnum.BANGUMI_SERVICE_UNAVAILABLE`/`ANIME_NOT_FOUND`（Task 5 新增）与 Task 5/6 测试中的断言消息（"番剧信息服务暂时不可用"/"番剧不存在"）一致。

---

## 与用户的既定流程约束（重申）

- 本机默认 `mvn` 版本过旧（见 Global Constraints），执行本计划前必须先切换 `JAVA_HOME`/`PATH`，否则所有 `mvn` 命令都会因 Maven/JDK 版本不满足要求而失败。
- Task 7 Step 3 的端到端联调依赖真实网络访问 `api.bgm.tv`，若当前环境不具备外网访问条件，可先完成 Step 1-2（配置与自动化测试），Step 3 留到有网络的环境中执行。
