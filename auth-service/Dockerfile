# syntax=docker/dockerfile:1.7
# Stage 1: Build
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

# Copy gradle wrapper + root build files from additional context (project root)
COPY --from=gradle-root gradle/ gradle/
COPY --from=gradle-root gradlew .
COPY --from=gradle-root gradlew.bat .
COPY --from=gradle-root build.gradle .
COPY --from=gradle-root settings.gradle .
COPY --from=gradle-root gradle.properties .

RUN chmod +x gradlew
RUN mkdir -p eureka-server api-gateway stock-service venta-service despacho-service stress-test

# Copy service source (from primary context)
COPY . auth-service/

# Build using a shared, persistent Gradle cache (BuildKit cache mount).
RUN --mount=type=cache,target=/root/.gradle,id=gradle-cache,sharing=locked \
    ./gradlew :auth-service:bootJar --no-daemon --build-cache

# Stage 2: Run
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/auth-service/build/libs/*.jar app.jar
EXPOSE 8084
ENV JAVA_OPTS="-Xms256m -Xmx512m"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar"]
