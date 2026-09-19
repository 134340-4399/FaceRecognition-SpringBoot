# 第一阶段：构建阶段，使用Maven打包SpringBoot项目
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# 先拷贝pom.xml，利用Docker缓存加速依赖下载
COPY pom.xml .
RUN mvn dependency:go-offline -B
# 拷贝源码并打包
COPY src ./src
RUN mvn clean package -DskipTests -B

# 第二阶段：运行阶段，只保留JRE
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# 从构建阶段复制jar包
COPY --from=build /app/target/*.jar app.jar
# 暴露后端端口
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]