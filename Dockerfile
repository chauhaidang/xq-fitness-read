# Build stage - use JDK base image with Gradle wrapper for version consistency
FROM eclipse-temurin:21-jdk AS builder

# Build arguments for accessing private GitHub packages
ARG GITHUB_ACTOR
ARG GITHUB_TOKEN
ENV GITHUB_ACTOR=${GITHUB_ACTOR}
ENV GITHUB_TOKEN=${GITHUB_TOKEN}
WORKDIR /app

# Copy Gradle wrapper and build files first (for caching)
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./

# Make gradlew executable and download dependencies as a separate layer
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon --console=plain

# Copy source code (this layer changes most frequently)
COPY src ./src

# Build the application (skip tests, use no-daemon for container efficiency)
RUN ./gradlew build -x test --no-daemon --console=plain --parallel

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy JAR from builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/v1/actuator/health || exit 1

# Run the application (JAVA_OPTS can be set at runtime if needed)
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS:-} -jar app.jar"]
