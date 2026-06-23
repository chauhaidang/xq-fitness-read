# Component Tests

Component tests run against the live API via the gateway (local xq-infra or CI).

## Prerequisites

1. Build the service image: `./build-read-service.sh`
2. Generate API client and install dependencies: `./scripts/generate-api-client.sh read-service && yarn install --immutable`
3. Install the external infra helper if needed: `npm install -g @chauhaidang/xq-test-infra@1.0.3`
4. Start test environment: `xq-infra generate -f ./test-env && xq-infra up`

Tests hit the gateway at **localhost:8080**. The database helper connects directly to the local xq-infra DB (localhost:5432, user `xq_user`, database `xq_fitness`) — no env vars needed.

## Run

```bash
yarn test:component        # Against the configured/default gateway
yarn test:component:local  # Against gateway (localhost:8080), DB at localhost:5432
yarn test:component:ci     # CI mode (JUnit output)
```

## Structure

- `workflows/` - Test files (e.g. `create-resource.test.ts`)
- `helpers/` - ApiClient, CleanupHelper, test-data
- `setup.ts` - Waits for API to be ready
- `teardown.ts` - Closes DB pool, generates report
