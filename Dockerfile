# syntax=docker/dockerfile:1.7

# Build stage
FROM node:20-alpine AS builder

WORKDIR /app

RUN apk add --no-cache bash openjdk17-jre \
  && corepack enable

# Generate the local file dependency before the immutable install.
COPY package.json yarn.lock .yarnrc.yml openapitools.json ./
COPY api/read-service-api.yaml ./api/read-service-api.yaml
COPY scripts/generate-api-client.sh ./scripts/generate-api-client.sh
RUN --mount=type=secret,id=GITHUB_TOKEN,required=true \
  export GITHUB_TOKEN="$(cat /run/secrets/GITHUB_TOKEN)" \
  && bash ./scripts/generate-api-client.sh read-service \
  && yarn install --immutable

COPY src ./src
COPY tsconfig.json ./

# Build TypeScript
RUN yarn build

# Runtime stage
FROM node:20-alpine

WORKDIR /app

RUN apk add --no-cache dumb-init

COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app/package.json ./
COPY --from=builder /app/dist ./dist

EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=10s --start-period=20s --retries=3 \
  CMD node -e "require('http').get('http://localhost:3000/health', (r) => {if (r.statusCode !== 200) throw new Error(r.statusCode)})"

ENTRYPOINT ["/usr/bin/dumb-init", "--"]
CMD ["node", "dist/src/index.js"]
