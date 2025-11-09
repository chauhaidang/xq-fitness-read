# XQ Fitness Read Service

Java Spring Boot service for reading workout data.

## Prerequisites

- Java 17 or higher
- Gradle
- PostgreSQL 12+
- Docker (optional, for containerized deployment)

## Quick Start

### Local Development

1. Start the PostgreSQL database:
```bash
docker pull ghcr.io/chauhaidang/xq-fitness-db:latest
docker run -d \
  --name xq-fitness-db \
  -p 5432:5432 \
  -e POSTGRES_DB=xq_fitness \
  -e POSTGRES_USER=xq_user \
  -e POSTGRES_PASSWORD=xq_password \
  ghcr.io/chauhaidang/xq-fitness-db:latest
```

2. Update database credentials in `src/main/resources/application.yml` if needed

3. Build the project:
```bash
./gradlew build
```

4. Run the service:
```bash
./gradlew bootRun
```

The service will start on port 8080.

### Docker Deployment

#### Using Docker Compose (Recommended)
From the project root, run:
```bash
docker-compose up -d
```

This starts all services including the database, read-service, and write-service.

#### Using Pre-built GHCR Images
Pull and run the latest published image:
```bash
docker run -d \
  --name xq-read-service \
  -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/xq_fitness \
  -e SPRING_DATASOURCE_USERNAME=xq_user \
  -e SPRING_DATASOURCE_PASSWORD=xq_password \
  ghcr.io/chauhaidang/xq-fitness-read-service:latest
```

#### Building Docker Image Locally
```bash
docker build -t xq-fitness-read-service:latest .
docker run -d \
  --name xq-read-service \
  -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/xq_fitness \
  -e SPRING_DATASOURCE_USERNAME=xq_user \
  -e SPRING_DATASOURCE_PASSWORD=xq_password \
  xq-fitness-read-service:latest
```

## API Endpoints

Base URL: `http://localhost:8080/api/v1`

- `GET /muscle-groups` - Get all muscle groups
- `GET /routines` - Get all routines (optional query param: `isActive`)
- `GET /routines/{id}` - Get routine by ID with full details
- `GET /routines/{id}/days` - Get all workout days for a routine

## API Documentation

See the OpenAPI specification at `./read-service-api.yaml`

## Build Tool

This project uses **Gradle** as the build tool.

## Common Commands

**Build:**
```bash
./gradlew build
```

**Clean build:**
```bash
./gradlew clean build
```

**Run service:**
```bash
./gradlew bootRun
```

**Run tests:**
```bash
./gradlew test                          # All tests
./gradlew test --tests ClassName        # Single test class
./gradlew test --tests ClassName.method # Single test method
```

**Check dependencies:**
```bash
./gradlew dependencies
```

## Docker Image Publishing

This service is automatically published to GitHub Container Registry (GHCR) on every push to the `main` branch.

### Image Tags
- `latest` - Most recent build from main branch
- `main-<commit-sha>` - Specific commit version (e.g., `main-abc1234f`)

### Pulling Published Images
```bash
docker pull ghcr.io/chauhaidang/xq-fitness-read-service:latest
docker pull ghcr.io/chauhaidang/xq-fitness-read-service:main-abc1234f
```

### GitHub Actions Workflow
See `.github/workflows/publish.yml` for the automatic publishing configuration.

## References
- For Architecture, refer to [architecture](./doc/ARCHITECTURE.md)
- For Business specification, refer to [business](./doc/BUSINESS.md)
