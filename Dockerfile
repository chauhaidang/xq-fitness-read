# Build stage
FROM gradle:8.5-jdk17 AS builder

# Build arguments for accessing private GitHub packages
ARG GITHUB_ACTOR
ARG GITHUB_TOKEN
ENV GITHUB_ACTOR=${GITHUB_ACTOR}
ENV GITHUB_TOKEN=${GITHUB_TOKEN}
WORKDIR /app

# Copy build files
COPY build.gradle .
COPY settings.gradle .
COPY gradle ./gradle

# Copy source code
COPY src ./src

# Build the application with GitHub credentials
RUN gradle clean build -x test

# Runtime stage
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copy JAR from builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/v1/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
