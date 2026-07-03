# anitrack 依赖初始化规范

## 单元测试依赖

anitrack 使用 `spring-boot-starter-test` 作为单一测试依赖坐标，已聚合 JUnit5、Mockito、AssertJ、MockMvc，无需逐个引入：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

版本号统一由 Spring Boot 3.x 的 `spring-boot-dependencies` BOM 管理，不在子模块中单独指定版本号；如需覆盖版本，在父 `pom.xml` 的 `<dependencyManagement>` 中统一声明。

## Maven Surefire 插件配置

测试源目录使用标准的 `src/test/java`，`maven-surefire-plugin` 使用 Spring Boot 父 POM 的默认配置即可，无需在子模块中额外声明 `testSourceDirectory` 或 `includes`：

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

若需要自定义测试类匹配规则（默认已覆盖 `**/*Test.java`），可显式声明：

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <includes>
            <include>**/*Test.java</include>
        </includes>
    </configuration>
</plugin>
```

## 依赖版本管理原则

所有依赖版本统一在根 `pom.xml` 的 `<dependencyManagement>` 中声明，子模块 `pom.xml` 引入依赖时不指定 `<version>`，避免多模块间版本不一致。
