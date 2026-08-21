# Stage 1: Build
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

# Copy gradle files from additional context (project root)
COPY --from=gradle-root gradle/ gradle/
COPY --from=gradle-root gradlew .
COPY --from=gradle-root gradlew.bat .
COPY --from=gradle-root build.gradle .
COPY --from=gradle-root settings.gradle .
COPY --from=gradle-root gradle.properties .

# Copy service source (from primary context)
COPY . api-gateway/

# Create empty dirs for other subprojects referenced in settings.gradle
RUN mkdir -p eureka-server stock-service venta-service despacho-service auth-service saga-orchestrator-service stress-test

RUN chmod +x gradlew && ./gradlew :api-gateway:bootJar --no-daemon

# Stage 2: Run
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/api-gateway/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
