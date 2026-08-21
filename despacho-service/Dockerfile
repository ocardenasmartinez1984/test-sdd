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
COPY . despacho-service/

# Create empty dirs for other subprojects referenced in settings.gradle
RUN mkdir -p eureka-server api-gateway stock-service venta-service auth-service saga-orchestrator-service stress-test

RUN chmod +x gradlew && ./gradlew :despacho-service:bootJar --no-daemon

# Stage 2: Run
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/despacho-service/build/libs/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
