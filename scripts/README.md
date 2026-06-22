# Deployment Scripts

This directory contains reusable scripts for deploying services to DigitalOcean App Platform.

## merge-app-spec.sh

Merges a service spec into an existing DigitalOcean App Platform app spec. This prevents overwriting other services when deploying a single service. The script automatically detects all services in the new spec and merges them.

### Usage

```bash
./merge-app-spec.sh <new-service-spec-file> <app-name> [output-file]
```

### Arguments

- `new-service-spec-file`: Path to the new service spec YAML file
- `app-name`: Name of the DigitalOcean App Platform app
- `output-file`: (Optional) Output file for merged spec (default: `<new-service-spec-file>.merged.yaml`)

### Examples

```bash
# Merge services from spec into existing app (automatically detects services)
./merge-app-spec.sh .do/app.rendered.yaml xq-fitness .do/app.merged.yaml
```

### How It Works

1. Checks if the app exists in DigitalOcean App Platform
2. If the app doesn't exist, uses the new spec as-is
3. If the app exists:
   - Fetches the current app spec
   - Automatically detects all services in the new spec
   - **Compares each service configuration** to detect actual changes
   - **Only updates services that have changed** (prevents unnecessary redeployments)
   - For changed services:
     - Removes the existing service with the same name (if present)
     - Adds/updates the new service configuration
   - Preserves all other services in the app (not in the new spec)
4. If no services changed, uses the existing spec to avoid redeployment
5. Outputs the merged spec to the specified file
6. Validates the merged spec

### Important Note on Redeployments

**DigitalOcean App Platform redeploys ALL services when an app spec is updated**, even if only one service changed. To minimize unnecessary redeployments:

- The script compares service configurations before updating
- If a service configuration hasn't changed, it's skipped
- If no services changed, the existing spec is used (no update = no redeployment)
- This prevents redeploying other services (e.g., write-service) when only read-service changes, but only if read-service config is identical

### Requirements

- `doctl`: DigitalOcean CLI tool
- `jq`: JSON processor
- `yq`: YAML processor (v4+)

### Error Handling

- If the app doesn't exist, creates a new spec file with the new services
- If fetching the current spec fails, warns and uses the new spec as-is
- If no services are found in the new spec, exits with an error

### Use in GitHub Actions

This script is designed to be used in GitHub Actions workflows. Example:

```yaml
- name: Merge app spec with existing services
  run: |
    chmod +x scripts/merge-app-spec.sh
    ./scripts/merge-app-spec.sh .do/app.rendered.yaml "$APP_NAME" .do/app.merged.yaml
```

### Copying to Other Services

To use this script in other services (e.g., write-service):

1. Copy the script to the other service's `scripts/` directory
2. Use the same command - no need to specify service names, the script detects them automatically
3. Ensure `yq` is installed in the workflow (see `.github/workflows/build.yml`)
