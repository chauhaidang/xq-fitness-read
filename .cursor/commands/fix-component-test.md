you base on api contract to fix and upadate component test, no cheat or workaround
you clarify business behaviour before update or fixing component test

**Apply `.cursor/rules/component-test.mdc`** when running or troubleshooting component tests locally:
- Build image with `build-<service_name>-service.sh` from the service directory
- Use xq-infra: `xq-infra generate -f ./test-env` then `xq-infra up` from the service directory
- Run tests: gradle (e.g. `./gradlew componentTest`) or npm (e.g. `npm run test:component:ci`)
- If env won't start: check container port conflicts (test-env config is similar across services)