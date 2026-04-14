# ---------- Build stage ----------
FROM mirror.gcr.io/library/maven:3.9.9-eclipse-temurin-17 AS builder

WORKDIR /workspace

COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline

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

COPY --from=builder --chown=appuser:appgroup /workspace/target/nexusmart-gateway-*.jar /app/gateway.jar

USER appuser

EXPOSE 8080

ENV TZ=Asia/Shanghai
ENV JAVA_OPTS="-Xms128m -Xmx256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD wget -e use_proxy=no -q -O /dev/null http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar gateway.jar"]
