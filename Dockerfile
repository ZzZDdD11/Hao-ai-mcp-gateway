# syntax=docker/dockerfile:1
# ====== 阶段1：Maven 编译打包（在容器内完成，不需要本机装 Maven/JDK）======
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# 配置阿里云 Maven 镜像，加速依赖下载（central → 阿里云公共仓库）
RUN mkdir -p /mvn-settings && cat > /mvn-settings/settings.xml <<'EOF'
<settings>
  <mirrors>
    <mirror>
      <id>aliyun</id>
      <mirrorOf>central</mirrorOf>
      <name>Aliyun Public</name>
      <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
  </mirrors>
</settings>
EOF

# 先复制所有 pom.xml（利用 Docker 缓存加速依赖下载）
COPY pom.xml .
COPY Hao-ai-mcp-gateway-api/pom.xml Hao-ai-mcp-gateway-api/
COPY Hao-ai-mcp-gateway-app/pom.xml Hao-ai-mcp-gateway-app/
COPY Hao-ai-mcp-gateway-case/pom.xml Hao-ai-mcp-gateway-case/
COPY Hao-ai-mcp-gateway-domain/pom.xml Hao-ai-mcp-gateway-domain/
COPY Hao-ai-mcp-gateway-infrastructure/pom.xml Hao-ai-mcp-gateway-infrastructure/
COPY Hao-ai-mcp-gateway-trigger/pom.xml Hao-ai-mcp-gateway-trigger/
COPY Hao-ai-mcp-gateway-types/pom.xml Hao-ai-mcp-gateway-types/

# 下载依赖（BuildKit cache mount 持久化 ~/.m2，阿里云镜像加速；仅 pom 变更时才重下）
RUN --mount=type=cache,target=/root/.m2 \
    mvn -s /mvn-settings/settings.xml dependency:go-offline -B || true

# 复制源码并打包
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -s /mvn-settings/settings.xml clean package -DskipTests

# ====== 阶段2：运行（只用 JRE，镜像更小）======
FROM eclipse-temurin:17-jre

ENV PARAMS=""
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

COPY --from=builder /build/Hao-ai-mcp-gateway-app/target/Hao-ai-mcp-gateway-app.jar /app.jar

ENTRYPOINT ["sh","-c","java -jar $JAVA_OPTS /app.jar $PARAMS"]
