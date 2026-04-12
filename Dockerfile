# ---------- Build stage ----------
FROM mirror.gcr.io/library/maven:3.9.9-eclipse-temurin-17 AS builder

WORKDIR /workspace

# 先拷贝 pom.xml，充分利用 Docker 层缓存加速依赖下载
COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline

# 再拷贝源码并打包
COPY src ./src
RUN mvn -q -DskipTests clean package

# ---------- Runtime stage ----------
FROM mirror.gcr.io/library/eclipse-temurin:17-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends tzdata wget \
    && ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system appgroup && useradd --system --gid appgroup appuser

WORKDIR /app

# 使用通配符兼容版本号变化
COPY --from=builder --chown=appuser:appgroup /workspace/target/NexusMart-*.jar /app/app.jar

USER appuser

EXPOSE 8081

ENV TZ=Asia/Shanghai
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD wget -q -O /dev/null http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]