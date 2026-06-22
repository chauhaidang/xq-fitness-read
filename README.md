# XQ Fitness Read Service

Node.js/Express read-only service for querying workout routines, days, exercises, and weekly reports.

### [Test summary report](https://chauhaidang.github.io/xq-fitness-read/)

## Setup

```bash
export GITHUB_TOKEN=your_github_packages_token
export DOCKER_BUILDKIT=1
corepack enable
./scripts/generate-api-client.sh read-service
yarn install --immutable
./build-read-service.sh
```

Use Node.js 20 for local work and CI. The direct generator script is required on a clean checkout because the generated client is a `file:` dependency and does not exist before bootstrap.

## Development

```bash
yarn dev                  # Start with nodemon
yarn test:unit            # Unit tests
yarn test:component       # Component tests (requires xq-infra up)
```

`npm` is still only needed for external infrastructure tooling such as `xq-infra` in component-test setup.

## Scripts

| Script                           | Purpose                                 |
| -------------------------------- | --------------------------------------- |
| `build-read-service.sh`          | Build Docker image                      |
| `scripts/generate-api-client.sh` | Generate TypeScript client from OpenAPI |
| `scripts/merge-app-spec.sh`      | Merge service spec for DO App Platform  |
