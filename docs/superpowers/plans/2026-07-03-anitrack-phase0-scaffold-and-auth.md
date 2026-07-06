# anitrack Phase 0：脚手架搭建 + 用户注册登录 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 搭建 anitrack 项目的 5 模块 Maven 骨架，并实现用户注册/登录功能（Spring Security + JWT），作为后续 Phase 1-4 开发的地基。

**Architecture:** DDD 分层 + 依赖倒置。`anitrack-domain` 定义 `User` 聚合根与 `UserRepo` 仓储接口，不依赖任何框架；`anitrack-infrastructure` 用 MyBatis 原生 XML 实现仓储、用 jjwt 实现 JWT 签发/校验；`anitrack-application` 编排注册/登录用例（密码加密比对、异常翻译），不涉及 JWT（JWT 签发职责在 Controller 层，因为应用层不能依赖 infrastructure 的实现类）；`anitrack-starter` 承载 Controller、统一响应、全局异常处理、JWT 拦截器与 Spring Security 基础配置；`anitrack-common` 提供零依赖的 `UserContextHolder`。

**Tech Stack:** Java 17、Spring Boot 3.5.3、Spring Security（仅用 `BCryptPasswordEncoder` + 放行的 `SecurityFilterChain`，实际鉴权由自定义 `JwtAuthInterceptor` 完成）、jjwt 0.12.6、MyBatis 原生 XML（`mybatis-spring-boot-starter` 3.0.4）、MySQL 8.x + HikariCP、Flyway、MapStruct 1.6.3、Lombok、JUnit5 + Mockito + AssertJ、`@WebMvcTest` + MockMvc。

## Global Constraints

- 依赖方向：`starter → application → domain`；`infrastructure → domain`；仓储/网关接口定义在 `domain`，`infrastructure` 实现；`application` 不直接依赖 `infrastructure`；`domain` 不依赖任何业务模块与 Spring 类型；`common` 被所有模块依赖（`docs/rules/anitrack-project-rules.md`）
- 数据库连接凭证（用户名密码）通过环境变量注入，不硬编码在配置文件中（`docs/rules/anitrack-persist-rules.md`）
- 不使用 MyBatis-Plus，不允许联表查询，不允许使用外键（`docs/rules/anitrack-project-rules.md`）
- 领域层不依赖 Spring 框架类型（如 `PasswordEncoder`、`ApplicationEventPublisher`）（`docs/rules/anitrack-project-rules.md`）
- 聚合根内部字段通过构造方法或工厂方法保证初始状态合法，不暴露无校验的 setter（`docs/rules/anitrack-model-rules.md`）
- 断言统一使用 AssertJ `assertThat()`，禁止 `Assertions.assertEquals()`（`docs/rules/anitrack-unittest-rules.md`）
- Mock 交互验证必须明确指定次数（`times(1)` 等），不 Mock 值对象/DTO（`docs/rules/anitrack-unittest-rules.md`）
- 鉴权失败由 `JwtAuthInterceptor` 直接返回 HTTP 401，不经过 `ResponseResult`（`docs/rules/anitrack-web-rules.md`）
- 不允许在 Controller 之外的层直接调用 `UserContextHolder`；应用层如需当前用户信息由 Controller 取出后作为参数传入（`docs/rules/anitrack-web-rules.md`）
- 用户密码统一用 `BCryptPasswordEncoder` 加密存储与校验，不使用 MD5 或明文（`docs/rules/anitrack-web-rules.md`）
- 版本号统一由根 `pom.xml` 的 `<dependencyManagement>` 管理，子模块 `<dependency>` 不单独指定 `<version>`（`docs/rules/anitrack-init-dependency-rules.md`）

---

## File Structure

```
anitrack/
├── pom.xml                                                          # 根聚合pom，parent=spring-boot-starter-parent:3.5.3，管理5模块+第三方库版本
├── .gitignore
├── anitrack-common/
│   ├── pom.xml
│   └── src/main/java/com/anitrack/common/utils/UserContextHolder.java   # ThreadLocal存储当前登录用户ID
├── anitrack-domain/
│   ├── pom.xml
│   └── src/main/java/com/anitrack/domain/
│       ├── common/AnitrackDomainException.java                      # 领域异常基类
│       └── user/
│           ├── model/User.java                                      # 聚合根，静态工厂register()
│           ├── enums/UserRole.java                                  # USER/ADMIN
│           └── repo/UserRepo.java                                   # 仓储接口
├── anitrack-infrastructure/
│   ├── pom.xml
│   └── src/main/java/com/anitrack/infra/
│       ├── dal/po/UserPO.java
│       ├── dal/mapper/UserMapper.java                               # + resources/mapper/UserMapper.xml
│       ├── converter/UserConverter.java                             # MapStruct: UserPO <-> User
│       ├── repo/UserRepoImpl.java                                   # implements UserRepo
│       └── auth/JwtTokenProvider.java                               # jjwt签发/校验
├── anitrack-application/
│   ├── pom.xml
│   └── src/main/java/com/anitrack/application/
│       ├── exception/AnitrackAppException.java
│       ├── exception/AppExceptionEnum.java
│       ├── model/UserRegisterBO.java
│       ├── model/UserLoginBO.java
│       ├── model/UserBO.java
│       └── service/UserApplication.java                            # register()/login()，不涉及JWT
└── anitrack-starter/
    ├── pom.xml
    └── src/main/
        ├── java/com/anitrack/starter/
        │   ├── ApplicationLoader.java
        │   ├── controller/UserController.java                      # /api/user/register, /api/user/login
        │   ├── converter/HttpConverter.java
        │   ├── request/UserRegisterReq.java
        │   ├── request/UserLoginReq.java
        │   ├── response/ResponseResult.java
        │   ├── response/UserInfoResponse.java
        │   ├── response/LoginResponse.java
        │   ├── interceptor/JwtAuthInterceptor.java
        │   └── config/
        │       ├── GlobalExceptionHandler.java
        │       ├── WebMvcConfig.java                                # 注册JwtAuthInterceptor，排除register/login
        │       └── SecurityConfig.java                              # PasswordEncoder bean + 放行的SecurityFilterChain
        └── resources/
            ├── application.yml
            └── db/migration/V1__create_user_table.sql
```

---

### Task 1: Maven 多模块骨架

**Files:**
- Create: `pom.xml`
- Create: `.gitignore`
- Create: `anitrack-common/pom.xml`
- Create: `anitrack-domain/pom.xml`
- Create: `anitrack-infrastructure/pom.xml`
- Create: `anitrack-application/pom.xml`
- Create: `anitrack-starter/pom.xml`

**Interfaces:**
- Produces：5 个可编译的空模块，供后续任务在其下新增 Java 文件

- [ ] **Step 1: 创建 .gitignore**

```
target/
*.class
.idea/
*.iml
.DS_Store
```

- [ ] **Step 2: 创建根 pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.3</version>
        <relativePath/>
    </parent>

    <groupId>com.anitrack</groupId>
    <artifactId>anitrack</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <modules>
        <module>anitrack-common</module>
        <module>anitrack-domain</module>
        <module>anitrack-infrastructure</module>
        <module>anitrack-application</module>
        <module>anitrack-starter</module>
    </modules>

    <properties>
        <java.version>17</java.version>
        <mapstruct.version>1.6.3</mapstruct.version>
        <lombok-mapstruct-binding.version>0.2.0</lombok-mapstruct-binding.version>
        <jjwt.version>0.12.6</jjwt.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>com.anitrack</groupId>
                <artifactId>anitrack-common</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.anitrack</groupId>
                <artifactId>anitrack-domain</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.anitrack</groupId>
                <artifactId>anitrack-infrastructure</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.anitrack</groupId>
                <artifactId>anitrack-application</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>org.mybatis.spring.boot</groupId>
                <artifactId>mybatis-spring-boot-starter</artifactId>
                <version>3.0.4</version>
            </dependency>
            <dependency>
                <groupId>org.mapstruct</groupId>
                <artifactId>mapstruct</artifactId>
                <version>${mapstruct.version}</version>
            </dependency>
            <dependency>
                <groupId>org.mapstruct</groupId>
                <artifactId>mapstruct-processor</artifactId>
                <version>${mapstruct.version}</version>
            </dependency>
            <dependency>
                <groupId>io.jsonwebtoken</groupId>
                <artifactId>jjwt-api</artifactId>
                <version>${jjwt.version}</version>
            </dependency>
            <dependency>
                <groupId>io.jsonwebtoken</groupId>
                <artifactId>jjwt-impl</artifactId>
                <version>${jjwt.version}</version>
                <scope>runtime</scope>
            </dependency>
            <dependency>
                <groupId>io.jsonwebtoken</groupId>
                <artifactId>jjwt-jackson</artifactId>
                <version>${jjwt.version}</version>
                <scope>runtime</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

- [ ] **Step 3: 创建 anitrack-common/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.anitrack</groupId>
        <artifactId>anitrack</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>anitrack-common</artifactId>
    <packaging>jar</packaging>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 4: 创建 anitrack-domain/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.anitrack</groupId>
        <artifactId>anitrack</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>anitrack-domain</artifactId>
    <packaging>jar</packaging>

    <dependencies>
        <dependency>
            <groupId>com.anitrack</groupId>
            <artifactId>anitrack-common</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 5: 创建 anitrack-infrastructure/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.anitrack</groupId>
        <artifactId>anitrack</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>anitrack-infrastructure</artifactId>
    <packaging>jar</packaging>

    <dependencies>
        <dependency>
            <groupId>com.anitrack</groupId>
            <artifactId>anitrack-domain</artifactId>
        </dependency>
        <dependency>
            <groupId>com.anitrack</groupId>
            <artifactId>anitrack-common</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.mybatis.spring.boot</groupId>
            <artifactId>mybatis-spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.mapstruct</groupId>
            <artifactId>mapstruct</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </path>
                        <path>
                            <groupId>org.mapstruct</groupId>
                            <artifactId>mapstruct-processor</artifactId>
                            <version>${mapstruct.version}</version>
                        </path>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok-mapstruct-binding</artifactId>
                            <version>${lombok-mapstruct-binding.version}</version>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 6: 创建 anitrack-application/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.anitrack</groupId>
        <artifactId>anitrack</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>anitrack-application</artifactId>
    <packaging>jar</packaging>

    <dependencies>
        <dependency>
            <groupId>com.anitrack</groupId>
            <artifactId>anitrack-domain</artifactId>
        </dependency>
        <dependency>
            <groupId>com.anitrack</groupId>
            <artifactId>anitrack-common</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-crypto</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 7: 创建 anitrack-starter/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.anitrack</groupId>
        <artifactId>anitrack</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>anitrack-starter</artifactId>
    <packaging>jar</packaging>

    <dependencies>
        <dependency>
            <groupId>com.anitrack</groupId>
            <artifactId>anitrack-application</artifactId>
        </dependency>
        <dependency>
            <groupId>com.anitrack</groupId>
            <artifactId>anitrack-infrastructure</artifactId>
        </dependency>
        <dependency>
            <groupId>com.anitrack</groupId>
            <artifactId>anitrack-common</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-mysql</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 8: 验证 Maven Reactor 编译通过**

Run: `mvn -q compile`
Expected: 无报错，5 个模块全部 `BUILD SUCCESS`（此时各模块尚无 Java 源文件，属于空编译）

- [ ] **Step 9: Commit**

```bash
git add pom.xml .gitignore anitrack-common/pom.xml anitrack-domain/pom.xml anitrack-infrastructure/pom.xml anitrack-application/pom.xml anitrack-starter/pom.xml
git commit -m "chore: 搭建anitrack 5模块Maven骨架"
```

---

### Task 2: User 聚合根 + UserRole 枚举 + UserRepo 接口 + 领域异常基类

**Files:**
- Create: `anitrack-domain/src/main/java/com/anitrack/domain/common/AnitrackDomainException.java`
- Create: `anitrack-domain/src/main/java/com/anitrack/domain/user/enums/UserRole.java`
- Create: `anitrack-domain/src/main/java/com/anitrack/domain/user/model/User.java`
- Create: `anitrack-domain/src/main/java/com/anitrack/domain/user/repo/UserRepo.java`
- Test: `anitrack-domain/src/test/java/com/anitrack/domain/user/model/UserTest.java`

**Interfaces:**
- Produces：`User.register(String username, String passwordHash, String nickname)` 静态工厂方法；`UserRepo.getByUsername(String)`、`UserRepo.save(User)`、`UserRepo.existsByUsername(String)`；`AnitrackDomainException(String message)` 供后续所有领域异常继承

- [ ] **Step 1: 编写 AnitrackDomainException**

```java
package com.anitrack.domain.common;

public class AnitrackDomainException extends RuntimeException {

    public AnitrackDomainException(String message) {
        super(message);
    }
}
```

- [ ] **Step 2: 编写 UserRole 枚举**

```java
package com.anitrack.domain.user.enums;

public enum UserRole {
    USER,
    ADMIN
}
```

- [ ] **Step 3: 编写失败的 UserTest**

```java
package com.anitrack.domain.user.model;

import com.anitrack.domain.user.enums.UserRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    void register_whenCalled_shouldCreateUserWithDefaultRole() {
        // when
        User user = User.register("alice", "hashed-password", "Alice");

        // then
        assertThat(user.getUsername()).isEqualTo("alice");
        assertThat(user.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(user.getNickname()).isEqualTo("Alice");
        assertThat(user.getRole()).isEqualTo(UserRole.USER);
        assertThat(user.getAvatarUrl()).isNull();
    }
}
```

- [ ] **Step 4: 运行测试，确认失败**

Run: `mvn -q -pl anitrack-domain -am test -Dtest=UserTest`
Expected: FAIL（`User` 类不存在，编译错误）

- [ ] **Step 5: 编写 User 聚合根**

```java
package com.anitrack.domain.user.model;

import com.anitrack.domain.user.enums.UserRole;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class User {

    private Long id;
    private String username;
    private String passwordHash;
    private String nickname;
    private String avatarUrl;
    private UserRole role;

    public static User register(String username, String passwordHash, String nickname) {
        return User.builder()
            .username(username)
            .passwordHash(passwordHash)
            .nickname(nickname)
            .role(UserRole.USER)
            .build();
    }
}
```

- [ ] **Step 6: 编写 UserRepo 接口**

```java
package com.anitrack.domain.user.repo;

import com.anitrack.domain.user.model.User;

public interface UserRepo {

    User getByUsername(String username);

    void save(User user);

    boolean existsByUsername(String username);
}
```

- [ ] **Step 7: 运行测试，确认通过**

Run: `mvn -q -pl anitrack-domain -am test -Dtest=UserTest`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add anitrack-domain/src/main/java/com/anitrack/domain/common/AnitrackDomainException.java anitrack-domain/src/main/java/com/anitrack/domain/user anitrack-domain/src/test/java/com/anitrack/domain/user
git commit -m "feat: 新增User聚合根、UserRepo仓储接口与领域异常基类"
```

---

### Task 3: t_user 表 Flyway 脚本 + UserPO + UserMapper

**Files:**
- Create: `anitrack-starter/src/main/resources/db/migration/V1__create_user_table.sql`
- Create: `anitrack-infrastructure/src/main/java/com/anitrack/infra/dal/po/UserPO.java`
- Create: `anitrack-infrastructure/src/main/java/com/anitrack/infra/dal/mapper/UserMapper.java`
- Create: `anitrack-infrastructure/src/main/resources/mapper/UserMapper.xml`

**Interfaces:**
- Consumes：无（本任务不依赖前面任务的 Java 类型）
- Produces：`UserMapper.insert(UserPO)`、`UserMapper.selectByUsername(String)`、`UserMapper.existsByUsername(String)`，供 Task 4 的 `UserRepoImpl` 调用

- [ ] **Step 1: 编写 Flyway 建表脚本**

```sql
CREATE TABLE t_user (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    username VARCHAR(64) NOT NULL COMMENT '用户名',
    password_hash VARCHAR(255) NOT NULL COMMENT 'BCrypt密码哈希',
    nickname VARCHAR(64) NOT NULL COMMENT '昵称',
    avatar_url VARCHAR(255) DEFAULT NULL COMMENT '头像地址',
    role VARCHAR(32) NOT NULL COMMENT '角色：USER/ADMIN',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
```

- [ ] **Step 2: 编写 UserPO**

```java
package com.anitrack.infra.dal.po;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserPO {

    private Long id;
    private String username;
    private String passwordHash;
    private String nickname;
    private String avatarUrl;
    private String role;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

- [ ] **Step 3: 编写 UserMapper 接口**

```java
package com.anitrack.infra.dal.mapper;

import com.anitrack.infra.dal.po.UserPO;
import org.apache.ibatis.annotations.Param;

public interface UserMapper {

    int insert(UserPO po);

    UserPO selectByUsername(@Param("username") String username);

    boolean existsByUsername(@Param("username") String username);
}
```

- [ ] **Step 4: 编写 UserMapper.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.anitrack.infra.dal.mapper.UserMapper">

    <resultMap id="BaseResultMap" type="com.anitrack.infra.dal.po.UserPO">
        <id column="id" property="id"/>
        <result column="username" property="username"/>
        <result column="password_hash" property="passwordHash"/>
        <result column="nickname" property="nickname"/>
        <result column="avatar_url" property="avatarUrl"/>
        <result column="role" property="role"/>
        <result column="create_time" property="createTime"/>
        <result column="update_time" property="updateTime"/>
    </resultMap>

    <sql id="Base_Column_List">
        id, username, password_hash, nickname, avatar_url, role, create_time, update_time
    </sql>

    <insert id="insert" parameterType="com.anitrack.infra.dal.po.UserPO" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO t_user (username, password_hash, nickname, avatar_url, role)
        VALUES (#{username}, #{passwordHash}, #{nickname}, #{avatarUrl}, #{role})
    </insert>

    <select id="selectByUsername" resultMap="BaseResultMap">
        SELECT
        <include refid="Base_Column_List"/>
        FROM t_user
        WHERE username = #{username}
    </select>

    <select id="existsByUsername" resultType="boolean">
        SELECT COUNT(1) > 0
        FROM t_user
        WHERE username = #{username}
    </select>

</mapper>
```

- [ ] **Step 5: 验证模块编译通过**

Run: `mvn -q -pl anitrack-infrastructure -am compile`
Expected: `BUILD SUCCESS`（此阶段无法验证 SQL/XML 与真实数据库的一致性，需等 Task 10 端到端联调时用真实 MySQL 验证）

- [ ] **Step 6: Commit**

```bash
git add anitrack-starter/src/main/resources/db/migration anitrack-infrastructure/src/main/java/com/anitrack/infra/dal anitrack-infrastructure/src/main/resources/mapper
git commit -m "feat: 新增t_user表结构与UserPO/UserMapper"
```

---

### Task 4: UserConverter（MapStruct）+ UserRepoImpl

**Files:**
- Create: `anitrack-infrastructure/src/main/java/com/anitrack/infra/converter/UserConverter.java`
- Create: `anitrack-infrastructure/src/main/java/com/anitrack/infra/repo/UserRepoImpl.java`

**Interfaces:**
- Consumes：`User`/`UserRole`（Task 2）、`UserPO`/`UserMapper`（Task 3）
- Produces：`UserRepoImpl implements UserRepo`，供 `anitrack-application` 通过 `UserRepo` 接口间接使用（`@Repository` 注册为 Spring Bean）

- [ ] **Step 1: 编写 UserConverter**

```java
package com.anitrack.infra.converter;

import com.anitrack.domain.user.enums.UserRole;
import com.anitrack.domain.user.model.User;
import com.anitrack.infra.dal.po.UserPO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserConverter {

    UserPO toPO(User user);

    User toDomain(UserPO po);

    default String map(UserRole role) {
        return role == null ? null : role.name();
    }

    default UserRole map(String role) {
        return role == null ? null : UserRole.valueOf(role);
    }
}
```

- [ ] **Step 2: 编写 UserRepoImpl**

```java
package com.anitrack.infra.repo;

import com.anitrack.domain.user.model.User;
import com.anitrack.domain.user.repo.UserRepo;
import com.anitrack.infra.converter.UserConverter;
import com.anitrack.infra.dal.mapper.UserMapper;
import com.anitrack.infra.dal.po.UserPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserRepoImpl implements UserRepo {

    private final UserMapper userMapper;
    private final UserConverter userConverter;

    @Override
    public User getByUsername(String username) {
        UserPO po = userMapper.selectByUsername(username);
        return po == null ? null : userConverter.toDomain(po);
    }

    @Override
    public void save(User user) {
        userMapper.insert(userConverter.toPO(user));
    }

    @Override
    public boolean existsByUsername(String username) {
        return userMapper.existsByUsername(username);
    }
}
```

- [ ] **Step 3: 验证模块编译通过（含 MapStruct 注解处理）**

Run: `mvn -q -pl anitrack-infrastructure -am compile`
Expected: `BUILD SUCCESS`，`target/generated-sources/annotations` 下生成 `UserConverterImpl`

- [ ] **Step 4: Commit**

```bash
git add anitrack-infrastructure/src/main/java/com/anitrack/infra/converter anitrack-infrastructure/src/main/java/com/anitrack/infra/repo
git commit -m "feat: 新增UserConverter与UserRepoImpl仓储实现"
```

---

### Task 5: 应用层统一异常（AnitrackAppException + AppExceptionEnum）

**Files:**
- Create: `anitrack-application/src/main/java/com/anitrack/application/exception/AnitrackAppException.java`
- Create: `anitrack-application/src/main/java/com/anitrack/application/exception/AppExceptionEnum.java`

**Interfaces:**
- Produces：`AnitrackAppException(AppExceptionEnum)`、`AppExceptionEnum.USERNAME_ALREADY_EXISTS`、`AppExceptionEnum.LOGIN_FAILED`，供 Task 6 的 `UserApplication` 与 Task 8 的 `GlobalExceptionHandler` 使用

- [ ] **Step 1: 编写 AppExceptionEnum**

```java
package com.anitrack.application.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AppExceptionEnum {

    USERNAME_ALREADY_EXISTS(40001, "用户名已存在"),
    LOGIN_FAILED(40002, "用户名或密码错误");

    private final int code;
    private final String message;
}
```

- [ ] **Step 2: 编写 AnitrackAppException**

```java
package com.anitrack.application.exception;

import lombok.Getter;

@Getter
public class AnitrackAppException extends RuntimeException {

    private final int code;

    public AnitrackAppException(AppExceptionEnum exceptionEnum) {
        super(exceptionEnum.getMessage());
        this.code = exceptionEnum.getCode();
    }
}
```

- [ ] **Step 3: 验证模块编译通过**

Run: `mvn -q -pl anitrack-application -am compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add anitrack-application/src/main/java/com/anitrack/application/exception
git commit -m "feat: 新增应用层统一异常AnitrackAppException与AppExceptionEnum"
```

---

### Task 6: UserApplication（注册/登录用例编排）

**Files:**
- Create: `anitrack-application/src/main/java/com/anitrack/application/model/UserRegisterBO.java`
- Create: `anitrack-application/src/main/java/com/anitrack/application/model/UserLoginBO.java`
- Create: `anitrack-application/src/main/java/com/anitrack/application/model/UserBO.java`
- Create: `anitrack-application/src/main/java/com/anitrack/application/service/UserApplication.java`
- Test: `anitrack-application/src/test/java/com/anitrack/application/service/UserApplicationTest.java`

**Interfaces:**
- Consumes：`User`/`UserRole`/`UserRepo`（Task 2）、`AnitrackAppException`/`AppExceptionEnum`（Task 5）、`PasswordEncoder`（`spring-security-crypto`）
- Produces：`UserApplication.register(UserRegisterBO): UserBO`、`UserApplication.login(UserLoginBO): UserBO`，供 Task 9 的 `UserController` 调用

- [ ] **Step 1: 编写 BO 类**

```java
package com.anitrack.application.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserRegisterBO {
    private String username;
    private String password;
    private String nickname;
}
```

```java
package com.anitrack.application.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserLoginBO {
    private String username;
    private String password;
}
```

```java
package com.anitrack.application.model;

import com.anitrack.domain.user.enums.UserRole;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserBO {
    private Long id;
    private String username;
    private String nickname;
    private String avatarUrl;
    private UserRole role;
}
```

- [ ] **Step 2: 编写失败的 UserApplicationTest**

```java
package com.anitrack.application.service;

import com.anitrack.application.exception.AnitrackAppException;
import com.anitrack.application.model.UserBO;
import com.anitrack.application.model.UserLoginBO;
import com.anitrack.application.model.UserRegisterBO;
import com.anitrack.domain.user.enums.UserRole;
import com.anitrack.domain.user.model.User;
import com.anitrack.domain.user.repo.UserRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserApplicationTest {

    @Mock
    private UserRepo mockUserRepo;

    @Mock
    private PasswordEncoder mockPasswordEncoder;

    private UserApplication sut;

    @Test
    void register_whenUsernameNotExists_shouldSaveAndReturnUserBO() {
        // given
        sut = new UserApplication(mockUserRepo, mockPasswordEncoder);
        UserRegisterBO registerBO = UserRegisterBO.builder()
            .username("alice")
            .password("raw-password")
            .nickname("Alice")
            .build();
        when(mockUserRepo.existsByUsername("alice")).thenReturn(false);
        when(mockPasswordEncoder.encode("raw-password")).thenReturn("hashed-password");

        // when
        UserBO result = sut.register(registerBO);

        // then
        assertThat(result.getUsername()).isEqualTo("alice");
        assertThat(result.getNickname()).isEqualTo("Alice");
        assertThat(result.getRole()).isEqualTo(UserRole.USER);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(mockUserRepo, times(1)).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("hashed-password");
    }

    @Test
    void register_whenUsernameExists_shouldThrowException() {
        // given
        sut = new UserApplication(mockUserRepo, mockPasswordEncoder);
        UserRegisterBO registerBO = UserRegisterBO.builder()
            .username("alice")
            .password("raw-password")
            .nickname("Alice")
            .build();
        when(mockUserRepo.existsByUsername("alice")).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> sut.register(registerBO))
            .isInstanceOf(AnitrackAppException.class)
            .hasMessageContaining("用户名已存在");

        verify(mockUserRepo, never()).save(any());
    }

    @Test
    void login_whenCredentialsAreValid_shouldReturnUserBO() {
        // given
        sut = new UserApplication(mockUserRepo, mockPasswordEncoder);
        UserLoginBO loginBO = UserLoginBO.builder().username("alice").password("raw-password").build();
        User existingUser = User.builder()
            .id(1L)
            .username("alice")
            .passwordHash("hashed-password")
            .nickname("Alice")
            .role(UserRole.USER)
            .build();
        when(mockUserRepo.getByUsername("alice")).thenReturn(existingUser);
        when(mockPasswordEncoder.matches("raw-password", "hashed-password")).thenReturn(true);

        // when
        UserBO result = sut.login(loginBO);

        // then
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getUsername()).isEqualTo("alice");
    }

    @Test
    void login_whenUserNotFound_shouldThrowException() {
        // given
        sut = new UserApplication(mockUserRepo, mockPasswordEncoder);
        UserLoginBO loginBO = UserLoginBO.builder().username("unknown").password("raw-password").build();
        when(mockUserRepo.getByUsername("unknown")).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> sut.login(loginBO))
            .isInstanceOf(AnitrackAppException.class)
            .hasMessageContaining("用户名或密码错误");
    }

    @Test
    void login_whenPasswordIncorrect_shouldThrowException() {
        // given
        sut = new UserApplication(mockUserRepo, mockPasswordEncoder);
        UserLoginBO loginBO = UserLoginBO.builder().username("alice").password("wrong-password").build();
        User existingUser = User.builder()
            .id(1L)
            .username("alice")
            .passwordHash("hashed-password")
            .role(UserRole.USER)
            .build();
        when(mockUserRepo.getByUsername("alice")).thenReturn(existingUser);
        when(mockPasswordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> sut.login(loginBO))
            .isInstanceOf(AnitrackAppException.class)
            .hasMessageContaining("用户名或密码错误");
    }
}
```

- [ ] **Step 3: 运行测试，确认失败**

Run: `mvn -q -pl anitrack-application -am test -Dtest=UserApplicationTest`
Expected: FAIL（`UserApplication` 类不存在，编译错误）

- [ ] **Step 4: 编写 UserApplication**

```java
package com.anitrack.application.service;

import com.anitrack.application.exception.AnitrackAppException;
import com.anitrack.application.exception.AppExceptionEnum;
import com.anitrack.application.model.UserBO;
import com.anitrack.application.model.UserLoginBO;
import com.anitrack.application.model.UserRegisterBO;
import com.anitrack.domain.user.model.User;
import com.anitrack.domain.user.repo.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserApplication {

    private final UserRepo userRepo;
    private final PasswordEncoder passwordEncoder;

    public UserBO register(UserRegisterBO registerBO) {
        if (userRepo.existsByUsername(registerBO.getUsername())) {
            throw new AnitrackAppException(AppExceptionEnum.USERNAME_ALREADY_EXISTS);
        }
        String passwordHash = passwordEncoder.encode(registerBO.getPassword());
        User user = User.register(registerBO.getUsername(), passwordHash, registerBO.getNickname());
        userRepo.save(user);
        return toBO(user);
    }

    public UserBO login(UserLoginBO loginBO) {
        User user = userRepo.getByUsername(loginBO.getUsername());
        if (user == null || !passwordEncoder.matches(loginBO.getPassword(), user.getPasswordHash())) {
            throw new AnitrackAppException(AppExceptionEnum.LOGIN_FAILED);
        }
        return toBO(user);
    }

    private UserBO toBO(User user) {
        return UserBO.builder()
            .id(user.getId())
            .username(user.getUsername())
            .nickname(user.getNickname())
            .avatarUrl(user.getAvatarUrl())
            .role(user.getRole())
            .build();
    }
}
```

- [ ] **Step 5: 运行测试，确认通过**

Run: `mvn -q -pl anitrack-application -am test -Dtest=UserApplicationTest`
Expected: PASS（5 个测试全部通过）

- [ ] **Step 6: Commit**

```bash
git add anitrack-application/src/main/java/com/anitrack/application/model anitrack-application/src/main/java/com/anitrack/application/service anitrack-application/src/test/java/com/anitrack/application/service
git commit -m "feat: 新增UserApplication注册登录用例编排"
```

---

### Task 7: JwtTokenProvider（jjwt 签发/校验）

**Files:**
- Create: `anitrack-infrastructure/src/main/java/com/anitrack/infra/auth/JwtTokenProvider.java`
- Test: `anitrack-infrastructure/src/test/java/com/anitrack/infra/auth/JwtTokenProviderTest.java`

**Interfaces:**
- Produces：`JwtTokenProvider.generateToken(Long userId): String`、`JwtTokenProvider.validateToken(String): boolean`、`JwtTokenProvider.getUserId(String): Long`，供 Task 9 的 `UserController`（签发）与 Task 10 的 `JwtAuthInterceptor`（校验）使用
- 配置项：`anitrack.jwt.secret`（必须 ≥32 字符，Task 10 的 `application.yml` 中通过 `${JWT_SECRET}` 注入）、`anitrack.jwt.expiration`（毫秒）

- [ ] **Step 1: 编写失败的 JwtTokenProviderTest**

```java
package com.anitrack.infra.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private JwtTokenProvider sut;

    @BeforeEach
    void setUp() {
        sut = new JwtTokenProvider(
            "test-secret-key-must-be-at-least-32-bytes-long",
            86400000L
        );
    }

    @Test
    void generateToken_thenValidateToken_shouldReturnTrue() {
        // when
        String token = sut.generateToken(1L);

        // then
        assertThat(sut.validateToken(token)).isTrue();
    }

    @Test
    void generateToken_thenGetUserId_shouldReturnOriginalUserId() {
        // when
        String token = sut.generateToken(42L);

        // then
        assertThat(sut.getUserId(token)).isEqualTo(42L);
    }

    @Test
    void validateToken_whenTokenIsMalformed_shouldReturnFalse() {
        // when & then
        assertThat(sut.validateToken("not-a-valid-token")).isFalse();
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

Run: `mvn -q -pl anitrack-infrastructure -am test -Dtest=JwtTokenProviderTest`
Expected: FAIL（`JwtTokenProvider` 类不存在，编译错误）

- [ ] **Step 3: 编写 JwtTokenProvider**

```java
package com.anitrack.infra.auth;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long expirationMillis;

    public JwtTokenProvider(
            @Value("${anitrack.jwt.secret}") String secret,
            @Value("${anitrack.jwt.expiration}") long expirationMillis) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMillis = expirationMillis;
    }

    public String generateToken(Long userId) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationMillis);
        return Jwts.builder()
            .subject(String.valueOf(userId))
            .issuedAt(now)
            .expiration(expiration)
            .signWith(key)
            .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Long getUserId(String token) {
        String subject = Jwts.parser().verifyWith(key).build()
            .parseSignedClaims(token)
            .getPayload()
            .getSubject();
        return Long.valueOf(subject);
    }
}
```

- [ ] **Step 4: 运行测试，确认通过**

Run: `mvn -q -pl anitrack-infrastructure -am test -Dtest=JwtTokenProviderTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add anitrack-infrastructure/src/main/java/com/anitrack/infra/auth anitrack-infrastructure/src/test/java/com/anitrack/infra/auth
git commit -m "feat: 新增JwtTokenProvider实现JWT签发与校验"
```

---

### Task 8: 统一响应体 + 全局异常处理

**Files:**
- Create: `anitrack-starter/src/main/java/com/anitrack/starter/response/ResponseResult.java`
- Create: `anitrack-starter/src/main/java/com/anitrack/starter/response/UserInfoResponse.java`
- Create: `anitrack-starter/src/main/java/com/anitrack/starter/response/LoginResponse.java`
- Create: `anitrack-starter/src/main/java/com/anitrack/starter/config/GlobalExceptionHandler.java`

**Interfaces:**
- Consumes：`AnitrackAppException`（Task 5）、`AnitrackDomainException`（Task 2）
- Produces：`ResponseResult.success(data)`/`ResponseResult.success()`/`ResponseResult.fail(message)`，供 Task 9 的 `UserController` 使用；`UserInfoResponse`、`LoginResponse{token, userInfo}` 供 Task 9 组装返回值

- [ ] **Step 1: 编写 ResponseResult**

```java
package com.anitrack.starter.response;

import lombok.Getter;

@Getter
public class ResponseResult<T> {

    private final int status;
    private final String message;
    private final T data;

    private ResponseResult(int status, String message, T data) {
        this.status = status;
        this.message = message;
        this.data = data;
    }

    public static <T> ResponseResult<T> success(T data) {
        return new ResponseResult<>(1, null, data);
    }

    public static <T> ResponseResult<T> success() {
        return new ResponseResult<>(1, null, null);
    }

    public static <T> ResponseResult<T> fail(String message) {
        return new ResponseResult<>(0, message, null);
    }
}
```

- [ ] **Step 2: 编写 UserInfoResponse 与 LoginResponse**

```java
package com.anitrack.starter.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserInfoResponse {
    private Long id;
    private String username;
    private String nickname;
    private String avatarUrl;
}
```

```java
package com.anitrack.starter.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResponse {
    private String token;
    private UserInfoResponse userInfo;
}
```

- [ ] **Step 3: 编写 GlobalExceptionHandler**

```java
package com.anitrack.starter.config;

import com.anitrack.application.exception.AnitrackAppException;
import com.anitrack.domain.common.AnitrackDomainException;
import com.anitrack.starter.response.ResponseResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AnitrackAppException.class)
    public ResponseResult<Void> handleAppException(AnitrackAppException e) {
        log.warn("应用层异常: code={}, message={}", e.getCode(), e.getMessage());
        return ResponseResult.fail(e.getMessage());
    }

    @ExceptionHandler(AnitrackDomainException.class)
    public ResponseResult<Void> handleDomainException(AnitrackDomainException e) {
        log.warn("领域层异常: message={}", e.getMessage());
        return ResponseResult.fail(e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseResult<Void> handleValidationException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError == null ? "参数校验失败" : fieldError.getDefaultMessage();
        return ResponseResult.fail(message);
    }

    @ExceptionHandler(Exception.class)
    public ResponseResult<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return ResponseResult.fail("系统异常，请稍后重试");
    }
}
```

- [ ] **Step 4: 验证模块编译通过**

Run: `mvn -q -pl anitrack-starter -am compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add anitrack-starter/src/main/java/com/anitrack/starter/response anitrack-starter/src/main/java/com/anitrack/starter/config/GlobalExceptionHandler.java
git commit -m "feat: 新增ResponseResult统一响应体与全局异常处理"
```

---

### Task 9: UserController（注册/登录接口）+ Web 层集成测试

**Files:**
- Create: `anitrack-starter/src/main/java/com/anitrack/starter/request/UserRegisterReq.java`
- Create: `anitrack-starter/src/main/java/com/anitrack/starter/request/UserLoginReq.java`
- Create: `anitrack-starter/src/main/java/com/anitrack/starter/converter/HttpConverter.java`
- Create: `anitrack-starter/src/main/java/com/anitrack/starter/controller/UserController.java`
- Test: `anitrack-starter/src/test/java/com/anitrack/starter/controller/UserControllerTest.java`

**Interfaces:**
- Consumes：`UserApplication`（Task 6）、`JwtTokenProvider`（Task 7）、`ResponseResult`/`UserInfoResponse`/`LoginResponse`/`GlobalExceptionHandler`（Task 8）
- Produces：`POST /api/user/register`、`POST /api/user/login`

- [ ] **Step 1: 编写请求对象**

```java
package com.anitrack.starter.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class UserRegisterReq {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    @NotBlank(message = "昵称不能为空")
    private String nickname;
}
```

```java
package com.anitrack.starter.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class UserLoginReq {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}
```

- [ ] **Step 2: 编写 HttpConverter**

```java
package com.anitrack.starter.converter;

import com.anitrack.application.model.UserBO;
import com.anitrack.application.model.UserLoginBO;
import com.anitrack.application.model.UserRegisterBO;
import com.anitrack.starter.request.UserLoginReq;
import com.anitrack.starter.request.UserRegisterReq;
import com.anitrack.starter.response.LoginResponse;
import com.anitrack.starter.response.UserInfoResponse;
import org.springframework.stereotype.Component;

@Component
public class HttpConverter {

    public UserRegisterBO req2BO(UserRegisterReq req) {
        return UserRegisterBO.builder()
            .username(req.getUsername())
            .password(req.getPassword())
            .nickname(req.getNickname())
            .build();
    }

    public UserLoginBO req2BO(UserLoginReq req) {
        return UserLoginBO.builder()
            .username(req.getUsername())
            .password(req.getPassword())
            .build();
    }

    public UserInfoResponse bo2Response(UserBO bo) {
        return UserInfoResponse.builder()
            .id(bo.getId())
            .username(bo.getUsername())
            .nickname(bo.getNickname())
            .avatarUrl(bo.getAvatarUrl())
            .build();
    }

    public LoginResponse toLoginResponse(String token, UserBO bo) {
        return LoginResponse.builder()
            .token(token)
            .userInfo(bo2Response(bo))
            .build();
    }
}
```

- [ ] **Step 3: 编写失败的 UserControllerTest**

```java
package com.anitrack.starter.controller;

import com.anitrack.application.exception.AnitrackAppException;
import com.anitrack.application.exception.AppExceptionEnum;
import com.anitrack.application.model.UserBO;
import com.anitrack.application.service.UserApplication;
import com.anitrack.domain.user.enums.UserRole;
import com.anitrack.infra.auth.JwtTokenProvider;
import com.anitrack.starter.converter.HttpConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserApplication mockUserApplication;

    @MockBean
    private JwtTokenProvider mockJwtTokenProvider;

    @MockBean
    private HttpConverter mockHttpConverter;

    @Test
    void postRegister_whenRequestBodyIsEmptyObject_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/user/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @ParameterizedTest(name = "username={0} should return 400")
    @CsvSource({"'', password, nickname", "username, '', nickname", "username, password, ''"})
    void postRegister_whenFieldIsBlank_shouldReturnBadRequest(String username, String password, String nickname) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("username", username);
        request.put("password", password);
        request.put("nickname", nickname);

        mockMvc.perform(post("/api/user/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void postRegister_whenRequestIsValid_shouldReturnOk() throws Exception {
        // given
        Map<String, Object> request = Map.of("username", "alice", "password", "raw-password", "nickname", "Alice");
        UserBO userBO = UserBO.builder().id(1L).username("alice").nickname("Alice").role(UserRole.USER).build();
        when(mockHttpConverter.req2BO(any(com.anitrack.starter.request.UserRegisterReq.class))).thenCallRealMethod();
        when(mockUserApplication.register(any())).thenReturn(userBO);

        // when & then
        mockMvc.perform(post("/api/user/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(1));

        verify(mockUserApplication, times(1)).register(any());
    }

    @Test
    void postRegister_whenUsernameAlreadyExists_shouldReturnBusinessError() throws Exception {
        // given
        Map<String, Object> request = Map.of("username", "alice", "password", "raw-password", "nickname", "Alice");
        when(mockHttpConverter.req2BO(any(com.anitrack.starter.request.UserRegisterReq.class))).thenCallRealMethod();
        doThrow(new AnitrackAppException(AppExceptionEnum.USERNAME_ALREADY_EXISTS))
            .when(mockUserApplication).register(any());

        // when & then
        mockMvc.perform(post("/api/user/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(0));
    }

    @Test
    void postLogin_whenRequestIsValid_shouldReturnTokenAndUserInfo() throws Exception {
        // given
        Map<String, Object> request = Map.of("username", "alice", "password", "raw-password");
        UserBO userBO = UserBO.builder().id(1L).username("alice").nickname("Alice").role(UserRole.USER).build();
        when(mockHttpConverter.req2BO(any(com.anitrack.starter.request.UserLoginReq.class))).thenCallRealMethod();
        when(mockUserApplication.login(any())).thenReturn(userBO);
        when(mockJwtTokenProvider.generateToken(1L)).thenReturn("mock-token");
        when(mockHttpConverter.toLoginResponse(eq("mock-token"), eq(userBO))).thenCallRealMethod();

        // when & then
        mockMvc.perform(post("/api/user/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(1))
            .andExpect(jsonPath("$.data.token").value("mock-token"))
            .andExpect(jsonPath("$.data.userInfo.username").value("alice"));

        verify(mockUserApplication, times(1)).login(any());
    }

    @Test
    void postLogin_whenCredentialsInvalid_shouldReturnBusinessError() throws Exception {
        // given
        Map<String, Object> request = Map.of("username", "alice", "password", "wrong-password");
        when(mockHttpConverter.req2BO(any(com.anitrack.starter.request.UserLoginReq.class))).thenCallRealMethod();
        doThrow(new AnitrackAppException(AppExceptionEnum.LOGIN_FAILED))
            .when(mockUserApplication).login(any());

        // when & then
        mockMvc.perform(post("/api/user/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(0));

        verify(mockJwtTokenProvider, never()).generateToken(any());
    }
}
```

- [ ] **Step 4: 运行测试，确认失败**

Run: `mvn -q -pl anitrack-starter -am test -Dtest=UserControllerTest`
Expected: FAIL（`UserController` 类不存在，编译错误）

- [ ] **Step 5: 编写 UserController**

```java
package com.anitrack.starter.controller;

import com.anitrack.application.model.UserBO;
import com.anitrack.application.service.UserApplication;
import com.anitrack.infra.auth.JwtTokenProvider;
import com.anitrack.starter.converter.HttpConverter;
import com.anitrack.starter.request.UserLoginReq;
import com.anitrack.starter.request.UserRegisterReq;
import com.anitrack.starter.response.LoginResponse;
import com.anitrack.starter.response.ResponseResult;
import com.anitrack.starter.response.UserInfoResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserApplication userApplication;
    private final JwtTokenProvider jwtTokenProvider;
    private final HttpConverter httpConverter;

    @PostMapping("/register")
    public ResponseResult<UserInfoResponse> register(@Valid @RequestBody UserRegisterReq req) {
        UserBO userBO = userApplication.register(httpConverter.req2BO(req));
        return ResponseResult.success(httpConverter.bo2Response(userBO));
    }

    @PostMapping("/login")
    public ResponseResult<LoginResponse> login(@Valid @RequestBody UserLoginReq req) {
        UserBO userBO = userApplication.login(httpConverter.req2BO(req));
        String token = jwtTokenProvider.generateToken(userBO.getId());
        return ResponseResult.success(httpConverter.toLoginResponse(token, userBO));
    }
}
```

- [ ] **Step 6: 运行测试，确认通过**

Run: `mvn -q -pl anitrack-starter -am test -Dtest=UserControllerTest`
Expected: PASS（7 个测试全部通过）

- [ ] **Step 7: Commit**

```bash
git add anitrack-starter/src/main/java/com/anitrack/starter/request anitrack-starter/src/main/java/com/anitrack/starter/converter anitrack-starter/src/main/java/com/anitrack/starter/controller anitrack-starter/src/test/java/com/anitrack/starter/controller
git commit -m "feat: 新增UserController注册登录接口与Web层集成测试"
```

---

### Task 10: UserContextHolder + JWT 拦截器 + Security 配置 + 应用启动 + 端到端联调

**Files:**
- Create: `anitrack-common/src/main/java/com/anitrack/common/utils/UserContextHolder.java`
- Test: `anitrack-common/src/test/java/com/anitrack/common/utils/UserContextHolderTest.java`
- Create: `anitrack-starter/src/main/java/com/anitrack/starter/interceptor/JwtAuthInterceptor.java`
- Create: `anitrack-starter/src/main/java/com/anitrack/starter/config/WebMvcConfig.java`
- Create: `anitrack-starter/src/main/java/com/anitrack/starter/config/SecurityConfig.java`
- Create: `anitrack-starter/src/main/java/com/anitrack/starter/ApplicationLoader.java`
- Create: `anitrack-starter/src/main/resources/application.yml`

**Interfaces:**
- Consumes：`JwtTokenProvider`（Task 7）
- Produces：可运行的 Spring Boot 应用；`UserContextHolder.setUserId(Long)`/`getUserId()`/`clear()`，供 Phase 1 及以后的 Controller 使用

**实施后修订说明（Task 10 实际执行时发现的计划盲区）**：`ApplicationLoader.java` 已在 Task 9 提前创建（`@WebMvcTest(UserController.class)` 需要可被发现的 `@SpringBootConfiguration`），Task 10 实际是 Modify 而非 Create。且 `@MapperScan` 未如本节原计划直接加在 `ApplicationLoader` 上，而是新建 `anitrack-starter/src/main/java/com/anitrack/starter/config/MyBatisConfig.java` 单独承载：`@MapperScan` 通过 `@Import(MapperScannerRegistrar.class)` 生效，这类导入不受 `@WebMvcTest` 的 `TypeExcludeFilter` 约束，若挂在作为 `@SpringBootConfiguration` 根类的 `ApplicationLoader` 上，会导致所有以其为上下文源的切片测试无条件注册 `UserMapper` bean，因无数据源报 `BeanCreationException`（已复现验证：`UserControllerTest` 8 个用例全部失败，根因 `Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required`）。抽取到独立 `@Configuration` 类后该问题消失，且不影响完整应用启动时的 Mapper 扫描效果（`MyBatisConfig` 位于 `scanBasePackages = "com.anitrack"` 范围内，会被正常组件扫描并生效）。

- [ ] **Step 1: 编写失败的 UserContextHolderTest**

```java
package com.anitrack.common.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserContextHolderTest {

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void getUserId_whenUserIdIsSet_shouldReturnIt() {
        // given
        UserContextHolder.setUserId(1L);

        // when & then
        assertThat(UserContextHolder.getUserId()).isEqualTo(1L);
    }

    @Test
    void getUserId_whenNotLoggedIn_shouldThrowException() {
        assertThatThrownBy(UserContextHolder::getUserId)
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void clear_whenCalled_shouldRemoveUserId() {
        // given
        UserContextHolder.setUserId(1L);

        // when
        UserContextHolder.clear();

        // then
        assertThatThrownBy(UserContextHolder::getUserId)
            .isInstanceOf(IllegalStateException.class);
    }
}
```

- [ ] **Step 2: 运行测试，确认失败**

Run: `mvn -q -pl anitrack-common -am test -Dtest=UserContextHolderTest`
Expected: FAIL（`UserContextHolder` 类不存在，编译错误）

- [ ] **Step 3: 编写 UserContextHolder**

```java
package com.anitrack.common.utils;

public class UserContextHolder {

    private static final ThreadLocal<Long> USER_ID_HOLDER = new ThreadLocal<>();

    private UserContextHolder() {
    }

    public static void setUserId(Long userId) {
        USER_ID_HOLDER.set(userId);
    }

    public static Long getUserId() {
        Long userId = USER_ID_HOLDER.get();
        if (userId == null) {
            throw new IllegalStateException("当前用户未登录");
        }
        return userId;
    }

    public static void clear() {
        USER_ID_HOLDER.remove();
    }
}
```

- [ ] **Step 4: 运行测试，确认通过**

Run: `mvn -q -pl anitrack-common -am test -Dtest=UserContextHolderTest`
Expected: PASS

- [ ] **Step 5: 编写 JwtAuthInterceptor**

```java
package com.anitrack.starter.interceptor;

import com.anitrack.common.utils.UserContextHolder;
import com.anitrack.infra.auth.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class JwtAuthInterceptor implements HandlerInterceptor {

    private static final String AUTH_HEADER = "Authorization";
    private static final String TOKEN_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authHeader = request.getHeader(AUTH_HEADER);
        if (authHeader == null || !authHeader.startsWith(TOKEN_PREFIX)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        }
        String token = authHeader.substring(TOKEN_PREFIX.length());
        if (!jwtTokenProvider.validateToken(token)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        }
        UserContextHolder.setUserId(jwtTokenProvider.getUserId(token));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContextHolder.clear();
    }
}
```

- [ ] **Step 6: 编写 WebMvcConfig**

```java
package com.anitrack.starter.config;

import com.anitrack.starter.interceptor.JwtAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtAuthInterceptor jwtAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthInterceptor)
            .addPathPatterns("/api/**")
            .excludePathPatterns("/api/user/register", "/api/user/login");
    }
}
```

- [ ] **Step 7: 编写 SecurityConfig**

```java
package com.anitrack.starter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .build();
    }
}
```

- [ ] **Step 8: 编写 ApplicationLoader**

```java
package com.anitrack.starter;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.anitrack")
@MapperScan("com.anitrack.infra.dal.mapper")
public class ApplicationLoader {

    public static void main(String[] args) {
        SpringApplication.run(ApplicationLoader.class, args);
    }
}
```

- [ ] **Step 9: 编写 application.yml**

```yaml
spring:
  application:
    name: anitrack
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

mybatis:
  mapper-locations: classpath:mapper/*.xml
  configuration:
    map-underscore-to-camel-case: true

anitrack:
  jwt:
    secret: ${JWT_SECRET}
    expiration: 86400000

server:
  port: 8080
```

- [ ] **Step 10: 验证整个 Reactor 编译并测试通过**

Run: `mvn -q compile test`
Expected: 所有模块 `BUILD SUCCESS`，此前 Task 2/6/7/9/10 编写的全部单测通过

- [ ] **Step 11: 端到端手动联调（需要本地 MySQL）**

准备本地 MySQL（`CREATE DATABASE anitrack;`），设置环境变量（`JWT_SECRET` 必须 ≥32 字符）：

```bash
export DB_USERNAME=root
export DB_PASSWORD=your-local-password
export JWT_SECRET=this-is-a-local-dev-secret-key-32bytes-min
```

启动应用：

```bash
mvn -q -pl anitrack-starter -am spring-boot:run
```

Expected: 控制台无异常，日志显示 Flyway 执行 `V1__create_user_table.sql` 成功，`t_user` 表已创建

新开终端，测试注册接口：

```bash
curl -s -X POST http://localhost:8080/api/user/register \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"password123","nickname":"Alice"}'
```

Expected: `{"status":1,"message":null,"data":{"id":1,"username":"alice","nickname":"Alice","avatarUrl":null}}`

测试登录接口：

```bash
curl -s -X POST http://localhost:8080/api/user/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"password123"}'
```

Expected: `{"status":1,"message":null,"data":{"token":"<jwt-token>","userInfo":{"id":1,"username":"alice","nickname":"Alice","avatarUrl":null}}}`

测试重复注册（校验唯一性约束）：

```bash
curl -s -X POST http://localhost:8080/api/user/register \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"password123","nickname":"Alice2"}'
```

Expected: `{"status":0,"message":"用户名已存在","data":null}`

验证完成后按 `Ctrl+C` 停止应用。

- [ ] **Step 12: Commit**

```bash
git add anitrack-common/src/main/java/com/anitrack/common/utils anitrack-common/src/test/java/com/anitrack/common/utils anitrack-starter/src/main/java/com/anitrack/starter/interceptor anitrack-starter/src/main/java/com/anitrack/starter/config anitrack-starter/src/main/java/com/anitrack/starter/ApplicationLoader.java anitrack-starter/src/main/resources/application.yml
git commit -m "feat: 新增JWT拦截器、Security配置与应用启动类，完成Phase 0端到端联调"
```

---

## Self-Review

**1. Spec 覆盖度**：设计文档 Phase 0 要求"脚手架（5模块搭建）+ 用户注册登录（JWT）"——Task 1 覆盖 5 模块骨架；Task 2-9 覆盖 User 全链路（领域/持久化/应用/Web）；Task 10 覆盖 JWT 拦截器、Security 基础配置、应用启动与端到端联调。`docs/rules/*.md` 中与 Phase 0 相关的规则（充血模型工厂方法、仓储接口定义在 domain、领域层不依赖 Spring、UserContextHolder 使用边界、密码加密方式、事务边界、数据库凭证走环境变量、MyBatis 原生 XML、Flyway 脚本命名与位置）均已在对应 Task 中体现。

**2. 占位符扫描**：全文所有 Step 均为可直接运行的完整代码/命令，无 TODO/"参考 Task N 类似实现"/"添加适当异常处理"等占位表述。

**3. 类型一致性检查**：`UserBO`（Task 6 定义）在 Task 9 的 `HttpConverter.bo2Response`/`toLoginResponse` 与 `UserControllerTest` 中的字段（`id`/`username`/`nickname`/`avatarUrl`/`role`）保持一致；`JwtTokenProvider` 的构造函数签名（`String secret, long expirationMillis`）与 Task 10 `application.yml` 中的 `anitrack.jwt.secret`/`anitrack.jwt.expiration` 配置键、`@Value` 注解引用完全对应；`UserRepo` 三个方法（`getByUsername`/`save`/`existsByUsername`，Task 2 定义）与 `UserRepoImpl`（Task 4）、`UserApplicationTest`（Task 6）中的调用签名一致。

---

## 与用户的既定流程约束（重申）

- 数据库连接凭证与 JWT 密钥严禁写入任何已提交文件，仅通过运行时环境变量注入（Task 10 Step 11 的 `export` 命令仅用于本地手动联调，不应写入 `.env` 或提交到仓库）。
