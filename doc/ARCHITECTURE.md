## Architecture

Node.js/Express layered architecture (aligned with write-service):
- **Routes** → **Controller** → **Service** → **Database (pg)**
- DTOs (TypeScript interfaces) match OpenAPI response shapes

### Key Patterns

**Data Access:**
- Parameterized SQL via `pg`; no ORM. `src/config/database.ts` exports `query()` and configures int8 parser.
- Services perform one or more queries and map rows to DTOs (date fields as ISO 8601 strings).

**Service Layer:**
- `readService.ts`: muscle groups, routines, routine by id, workout days by routine, exercises by workout day.
- `reportService.ts`: weekly report (week start = Monday UTC); aggregates from `snapshot_exercises` or legacy `snapshot_workout_day_sets`.

**Configuration:**
- Base path: `/api/v1`; health at `/health`.
- Env: `DB_HOST`, `DB_PORT`, `DB_USER`, `DB_PASSWORD`, `DB_NAME`, `DB_SSL`.
- Port: 3000.

**When adding new endpoints:**
1. Add handler in controller, service, and `src/routes/index.ts`.
2. Update `api/read-service-api.yaml` OpenAPI spec.
