# 基础镜像：使用轻量级的 Java 17 运行环境
FROM eclipse-temurin:17-jre-alpine

# 设置时区为上海，防止存入数据库的时间少 8 个小时
RUN apk add --no-cache tzdata \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone

# 创建非 root 用户，避免以 root 身份运行应用
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# 设定容器内的工作目录
WORKDIR /app

# 将刚刚打包好的 jar 包复制到容器内，并重命名为 app.jar
# 注意：这里的名字要和你在 target 目录下生成的名字一致，你可以按需修改
COPY --chown=appuser:appgroup target/NexusMart-1.0.0-SNAPSHOT.jar /app/app.jar

# 以非 root 用户运行
USER appuser

# 暴露 8081 端口（容器内部的端口）
EXPOSE 8081

# 容器启动时执行的命令（附带 JVM 内存参数，可通过 JAVA_OPTS 环境变量覆盖）
ENV JAVA_OPTS="-Xms256m -Xmx512m"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]