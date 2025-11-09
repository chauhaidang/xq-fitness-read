## Architecture

Standard Spring Boot layered architecture:
- **Controller** → **Service** → **Repository** → **Entity**
- DTOs for API responses with `fromEntity()` factory methods

### Key Patterns

**Repository Queries:**
- Use JOIN FETCH in `@Query` to eagerly load nested entities and avoid N+1 queries
- Example: `WorkoutRoutineRepository.findByIdWithDetails()` fetches routine with all nested workout days, sets, and muscle groups in one query

**Entity Relationships:**
```
WorkoutRoutine (1) → (N) WorkoutDay (1) → (N) WorkoutDaySet (N) → (1) MuscleGroup
```
- Default fetch type: LAZY
- Use JOIN FETCH queries for detail endpoints

**DTO Conversion:**
All DTOs have static `fromEntity()` methods that handle entity-to-DTO conversion, including nested objects using Java Streams.

**Service Layer:**
All methods in `ReadService` are marked `@Transactional(readOnly = true)`.

**Configuration:**
- Base path: `/api/v1` (set in `application.properties`)
- JPA `ddl-auto=validate` - schema is managed externally in `../database/schemas/`
- Database credentials in `src/main/resources/application.properties`

**When adding new endpoints:**
1. Update `ReadController`
2. Update `read-service-api.yaml` OpenAPI spec
