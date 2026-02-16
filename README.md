# XQ Fitness Read Service

Node.js/Express read-only service for querying workout routines, days, exercises, and weekly reports.

### [Test summary report](https://chauhaidang.github.io/xq-fitness-read/)

## Setup

```bash
npm run generate:client   # Before npm install (client is file: dependency)
npm install
./build-read-service.sh
```

## Development

```bash
npm run dev               # Start with nodemon
npm run test:unit         # Unit tests
npm run test:component    # Component tests (requires xq-infra up)
```

## Scripts

| Script | Purpose |
|--------|---------|
| `build-read-service.sh` | Build Docker image |
| `run-tests-do.sh` | Run component tests vs DigitalOcean (set APP_URL) |
| `scripts/generate-api-client.sh` | Generate TypeScript client from OpenAPI |
| `scripts/merge-app-spec.sh` | Merge service spec for DO App Platform |
