# ====== 阶段1：Maven 编译打包（在容器内完成，不需要本机装 Maven/JDK）======
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# 先复制所有 pom.xml（利用 Docker 缓存加速依赖下载）
COPY pom.xml .
COPY Hao-ai-mcp-gateway-api/pom.xml Hao-ai-mcp-gateway-api/
COPY Hao-ai-mcp-gateway-app/pom.xml Hao-ai-mcp-gateway-app/
COPY Hao-ai-mcp-gateway-case/pom.xml Hao-ai-mcp-gateway-case/
COPY Hao-ai-mcp-gateway-domain/pom.xml Hao-ai-mcp-gateway-domain/
COPY Hao-ai-mcp-gateway-infrastructure/pom.xml Hao-ai-mcp-gateway-infrastructure/
COPY Hao-ai-mcp-gateway-trigger/pom.xml Hao-ai-mcp-gateway-trigger/
COPY Hao-ai-mcp-gateway-types/pom.xml Hao-ai-mcp-gateway-types/

# 下载依赖（首次慢，后续 Docker cache 命中快）
RUN mvn dependency:go-offline -B || true

# 复制源码并打包
COPY . .
RUN mvn clean package -DskipTests

# ====== 阶段2：运行（只用 JRE，镜像更小）======
FROM eclipse-temurin:17-jre

ENV PARAMS=""
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

COPY --from=builder /build/Hao-ai-mcp-gateway-app/target/Hao-ai-mcp-gateway-app.jar /app.jar

ENTRYPOINT ["sh","-c","java -jar $JAVA_OPTS /app.jar $PARAMS"]
