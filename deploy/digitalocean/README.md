# DigitalOcean Deployment for Read Service

This directory contains deployment scripts and templates for deploying the read-service to DigitalOcean App Platform.

## Files

- `app-spec.read-service.template.yaml` - App Platform spec template for read-service
- `deploy-service.sh` - Script to deploy this service to App Platform
- `provision-db.sh` - Script to provision the shared database (run once)

## Quick Start

### 1. Provision Database (One-time)

```bash
export DO_TOKEN=your_digitalocean_token
./provision-db.sh
```

Save the database connection details that are output.

### 2. Deploy Read Service

```bash
export DO_TOKEN=your_digitalocean_token
export DB_HOST=...
export DB_PORT=...
export DB_USER=...
export DB_PASSWORD=...
export IMAGE_NAME=chauhaidang/xq-fitness-read-service
export IMAGE_TAG=latest

./deploy-service.sh read-service
```

## GitHub Actions

The service includes a GitHub Actions workflow (`.github/workflows/deploy-docr.yml`) that:
- Builds and pushes Docker images to DigitalOcean Container Registry
- Automatically deploys to App Platform on push to `main`

See the main deployment documentation in the root `deploy/digitalocean/` directory for complete details.

