# 后端镜像：Maven 多阶段构建，运行阶段只留 JRE + 一个 jar
#
# 构建上下文是仓库根目录：
#   docker build -f deploy/docker/backend.Dockerfile -t clf-backend .
#
# 注意目录结构：pom.xml 里用 ${project.basedir}/../db 引入迁移脚本，
# 所以 pom 必须放在 <ctx>/backend/ 下、db 放在 <ctx>/db，与仓库布局一致。

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src/backend

# 国内网络直连 Maven Central 常超时，默认走阿里云镜像；海外可传 --build-arg USE_MIRROR=no。
ARG USE_MIRROR=yes
COPY deploy/docker/maven-settings.xml /root/.m2/settings.xml

COPY backend/pom.xml ./pom.xml
# 先单独拉依赖，代码改动时可以命中这一层缓存。
RUN if [ "$USE_MIRROR" = "no" ]; then rm -f /root/.m2/settings.xml; fi \
 && mvn -B -q dependency:go-offline

COPY backend/src ./src
COPY db /src/db
RUN mvn -B -q -DskipTests package \
 && find target -maxdepth 1 -name '*.jar' ! -name '*.original' -exec cp {} /app.jar \;

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

ENV TZ=Asia/Shanghai \
    SERVER_PORT=8081 \
    SPRING_PROFILES_ACTIVE=local \
    STORAGE_LOCAL_ROOT=/app/data/files

RUN addgroup -S app && adduser -S app -G app \
 && mkdir -p /app/data/files && chown -R app:app /app
USER app

COPY --from=build /app.jar /app/app.jar

EXPOSE 8081
VOLUME ["/app/data/files"]

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]