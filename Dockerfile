# syntax=docker/dockerfile:1

# ---------- build ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# 先拷贝所有 pom 预热依赖缓存
COPY pom.xml .
COPY anitrack-common/pom.xml anitrack-common/
COPY anitrack-domain/pom.xml anitrack-domain/
COPY anitrack-infrastructure/pom.xml anitrack-infrastructure/
COPY anitrack-application/pom.xml anitrack-application/
COPY anitrack-starter/pom.xml anitrack-starter/
RUN mvn -B -e dependency:go-offline -DskipTests || true

# 拷贝源码并打包
COPY anitrack-common/src anitrack-common/src
COPY anitrack-domain/src anitrack-domain/src
COPY anitrack-infrastructure/src anitrack-infrastructure/src
COPY anitrack-application/src anitrack-application/src
COPY anitrack-starter/src anitrack-starter/src
RUN mvn -B -e clean package -DskipTests

# 提取 Spring Boot 分层 jar，便于运行时镜像分层缓存
RUN mkdir -p target/extracted && \
    java -Djarmode=layertools -jar anitrack-starter/target/anitrack-starter-1.0.0-SNAPSHOT.jar extract --destination target/extracted

# ---------- runtime ----------
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
