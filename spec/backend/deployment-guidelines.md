# Deployment Guidelines

This repository deploys backend services through the root `Jenkinsfile` and the Docker Compose templates under `script/docker/standalone/`.

## Scenario: Jenkins Docker Compose Deployment With External Infrastructure

### 1. Scope / Trigger

- Trigger: changes to `Jenkinsfile`, `script/docker/standalone/docker-compose.yml`, `.env.example`, or deployment docs.
- Scope: application service containers only. MySQL, Redis, Nacos, XXL-Job, and optional MQ infrastructure are external dependencies provided through the deploy host `.env`.

### 2. Signatures

- Jenkins parameter `SELECT_ALL_SERVICES`: boolean; when checked, all enabled services are selected.
- Jenkins service parameters: booleans named `SERVICE_*`; when `SELECT_ALL_SERVICES=false`, these checkboxes control Maven module packaging, Docker image building, and optional Compose deployment for the same selected set.
- `script/docker/standalone/docker-compose.yml` must only define services that are enabled in the root `Jenkinsfile` service map.
- `yudao-server` is the aggregated monolith startup mode and must not be included in standalone microservice Docker image builds or Compose deployment.
- Jenkins parameter `DEPLOY_DIR`: absolute deploy directory on the SSH target, default `/data/situ/gone`.
- Jenkins parameter `SSH_SERVER_NAME`: Publish Over SSH server config name; must match Jenkins global SSH Server `Name`.
- Jenkins parameter `IMAGE_REPO_PREFIX`: image repository prefix, default `gone-cloud`.
- Jenkins parameter `MAVEN_TOOL_NAME`: Jenkins global Maven tool name; when present, resolve it with the Pipeline `tool` step and prepend its `bin` directory to `PATH`.
- Jenkins parameter `MAVEN_CMD`: explicit Maven command used only when `MAVEN_TOOL_NAME` is blank or cannot be resolved.
- Jenkins parameter `MAVEN_DOCKER_IMAGE`: Docker image used only when Jenkins global Maven, `MAVEN_CMD`, and node `mvn` are unavailable.
- Compose command contract:

```bash
cd "$DEPLOY_DIR"
IMAGE_REPO_PREFIX=<prefix> IMAGE_TAG=<build-number> docker compose --env-file .env -f docker-compose.yml up -d <services>
```

### 3. Contracts

- `DEPLOY_DIR` is on the Publish Over SSH target host, not inside the Jenkins controller/container.
- `DEPLOY_DIR/.env` on the SSH target is required and must be non-empty before deployment.
- Jenkins may upload `docker-compose.yml`, `README.md`, and `.env.example` into `DEPLOY_DIR` through Publish Over SSH.
- Jenkins must not create, overwrite, or mutate the real remote `DEPLOY_DIR/.env`.
- `DEPLOY_NOW=false` must still build the selected service jars and Docker images; it only skips Compose deployment.
- The SSH target must have Docker and Docker Compose installed.
- Built images must be visible to the SSH target Docker daemon. If Jenkins builds through the host Docker socket and SSH deploys to the same host, the images are already visible.
- Maven resolution order is:
  1. Jenkins global Maven tool named by `MAVEN_TOOL_NAME`
  2. `MAVEN_CMD`
  3. `mvn` on the Jenkins node `PATH`
  4. Dockerized Maven from `MAVEN_DOCKER_IMAGE`
- If the Jenkins global Maven tool name cannot be resolved, fallback is allowed. If Maven itself starts and the build fails, the pipeline must fail instead of retrying through another Maven path.
- Required external infrastructure keys include:
  - `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_DATABASE`, `MYSQL_USERNAME`, `MYSQL_PASSWORD`
  - `REDIS_HOST`, `REDIS_PORT`, `REDIS_DATABASE`, `REDIS_PASSWORD`
  - `NACOS_SERVER_ADDR`, `NACOS_NAMESPACE`, `NACOS_GROUP`, `NACOS_USERNAME`, `NACOS_PASSWORD`
  - `XXL_JOB_ENABLED`, `XXL_JOB_ADMIN_ADDRESSES`, `XXL_JOB_ACCESS_TOKEN`
- `XXL_JOB_ACCESSTOKEN` is legacy-compatible only; new env files should use `XXL_JOB_ACCESS_TOKEN`.
- Relative Compose mounts such as `./logs` and `./plugins` resolve under `DEPLOY_DIR`, because Jenkins runs Compose from that directory.
- Compose `JAVA_OPTS` defaults must not embed quotes in the interpolated value. Use `JAVA_OPTS: "${JAVA_OPTS_GATEWAY_SERVER:--Xms512m ...}"`, not `JAVA_OPTS: ${JAVA_OPTS_GATEWAY_SERVER:-"-Xms512m ..."}`.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| No service checkbox selected and `SELECT_ALL_SERVICES=false` | Jenkins fails during service resolution |
| `SELECT_ALL_SERVICES=true` | Jenkins ignores individual service checkboxes and selects all enabled services |
| Compose defines services outside the Jenkins service map | Remove the extra services or enable/build them in Jenkins before deployment |
| Compose `JAVA_OPTS` default contains embedded quotes | Containers fail with `Could not find or load main class "-Xms512m`; remove embedded quotes from the default value |
| `MAVEN_TOOL_NAME` does not match a Jenkins global Maven tool | Jenkins logs the missing tool and falls back to `MAVEN_CMD`, node `mvn`, then Dockerized Maven |
| Maven command starts but build fails | Jenkins fails the build without retrying another Maven path |
| No Maven path and no Docker command available | Jenkins fails with an explicit Maven/Docker installation message |
| Missing remote `DEPLOY_DIR/.env` | SSH deploy command fails before Compose and points to `.env.example` |
| Empty remote `DEPLOY_DIR/.env` | SSH deploy command fails before Compose |
| Invalid Compose syntax or env interpolation | `docker compose config --quiet` fails before `up` |
| `SSH_SERVER_NAME` does not match a configured Publish Over SSH server | Jenkins fails in the SSH Publisher step |
| SSH target Docker daemon cannot see the built image tag | `docker compose up` pulls/fails according to Docker image availability; fix by deploying to same daemon or pushing to a registry |
| External infrastructure address is wrong | Service may start unhealthy or fail at runtime; do not patch Compose to create local infra |

### 5. Good / Base / Bad Cases

- Good: operator selects services through Jenkins checkboxes; Jenkins uses the configured global Maven tool, builds selected images, uploads Compose files through Publish Over SSH, then runs Compose on the SSH target with the same selected set and build tag.
- Base: first deployment fails until an operator creates `DEPLOY_DIR/.env` from `.env.example`.
- Bad: Jenkins uses a free-form `SERVICES` text parameter that can drift from supported service keys, or copies `.env.example` to `.env` and deploys against placeholder infrastructure.

### 6. Tests Required

- Run `docker compose --env-file script/docker/standalone/.env.example -f script/docker/standalone/docker-compose.yml config --quiet` after changing Compose or env keys.
- Run `git diff --check` after changing Jenkins or deployment docs.
- For Jenkins behavior changes, review service checkbox selection, Maven tool fallback, SSH server name, remote upload file list, missing `.env`, empty `.env`, and selected-service paths.

### 7. Wrong vs Correct

#### Wrong

```bash
SERVICES=gateway-server,system-server,infra-server
mvn -pl "$MODULES" -am clean package -DskipTests
cp script/docker/standalone/.env.example "$DEPLOY_DIR/.env"
```

#### Correct

```bash
SELECT_ALL_SERVICES=false
SERVICE_GATEWAY_SERVER=true
SERVICE_SYSTEM_SERVER=true
SERVICE_INFRA_SERVER=true
MAVEN_TOOL_NAME=mvn3.9.9
SSH_SERVER_NAME=192.168.16.102
test -s "$DEPLOY_DIR/.env"
cd "$DEPLOY_DIR"
docker compose --env-file .env -f docker-compose.yml config --quiet
docker compose --env-file .env -f docker-compose.yml up -d gateway-server system-server infra-server
```
