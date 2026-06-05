# Deployment Guidelines

This repository deploys backend services through the root `Jenkinsfile` and the Docker Compose templates under `script/docker/standalone/`.

## Scenario: Jenkins Docker Compose Deployment With External Infrastructure

### 1. Scope / Trigger

- Trigger: changes to `Jenkinsfile`, `script/docker/standalone/docker-compose.yml`, `.env.example`, or deployment docs.
- Scope: application service containers only. MySQL, Redis, Nacos, XXL-Job, and optional MQ infrastructure are external dependencies provided through the deploy host `.env`.

### 2. Signatures

- Jenkins parameter `SERVICES`: comma-separated service module names, or `all`; this controls Maven module packaging, Docker image building, and optional Compose deployment for the same selected set.
- Jenkins parameter `DEPLOY_DIR`: absolute deploy directory, default `/opt/gone-cloud/services`.
- Jenkins parameter `IMAGE_REPO_PREFIX`: image repository prefix, default `gone-cloud`.
- Compose command contract:

```bash
cd "$DEPLOY_DIR"
IMAGE_REPO_PREFIX=<prefix> IMAGE_TAG=<build-number> docker compose --env-file .env -f docker-compose.yml up -d <services>
```

### 3. Contracts

- `DEPLOY_DIR/.env` is required and must be non-empty before deployment.
- Jenkins may copy `docker-compose.yml`, `README.md`, and `.env.example` into `DEPLOY_DIR`.
- Jenkins must not create, overwrite, or mutate the real `DEPLOY_DIR/.env`.
- `DEPLOY_NOW=false` must still build the selected service jars and Docker images; it only skips Compose deployment.
- Required external infrastructure keys include:
  - `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_DATABASE`, `MYSQL_USERNAME`, `MYSQL_PASSWORD`
  - `REDIS_HOST`, `REDIS_PORT`, `REDIS_DATABASE`, `REDIS_PASSWORD`
  - `NACOS_SERVER_ADDR`, `NACOS_NAMESPACE`, `NACOS_GROUP`, `NACOS_USERNAME`, `NACOS_PASSWORD`
  - `XXL_JOB_ENABLED`, `XXL_JOB_ADMIN_ADDRESSES`, `XXL_JOB_ACCESS_TOKEN`
- `XXL_JOB_ACCESSTOKEN` is legacy-compatible only; new env files should use `XXL_JOB_ACCESS_TOKEN`.
- Relative Compose mounts such as `./logs` and `./plugins` resolve under `DEPLOY_DIR`, because Jenkins runs Compose from that directory.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| Unknown `SERVICES` entry | Jenkins fails during service resolution |
| Missing `DEPLOY_DIR/.env` | Jenkins fails before Compose and points to `.env.example` |
| Empty `DEPLOY_DIR/.env` | Jenkins fails before Compose |
| Invalid Compose syntax or env interpolation | `docker compose config --quiet` fails before `up` |
| External infrastructure address is wrong | Service may start unhealthy or fail at runtime; do not patch Compose to create local infra |

### 5. Good / Base / Bad Cases

- Good: deploy host owns `.env`; Jenkins copies templates, validates Compose, and deploys selected services with the current build tag.
- Base: first deployment fails until an operator creates `DEPLOY_DIR/.env` from `.env.example`.
- Bad: Jenkins copies `.env.example` to `.env` and deploys against placeholder infrastructure.

### 6. Tests Required

- Run `docker compose --env-file script/docker/standalone/.env.example -f script/docker/standalone/docker-compose.yml config --quiet` after changing Compose or env keys.
- Run `git diff --check` after changing Jenkins or deployment docs.
- For Jenkins behavior changes, review the shell blocks for missing `.env`, empty `.env`, and selected-service paths.

### 7. Wrong vs Correct

#### Wrong

```bash
cp script/docker/standalone/.env.example "$DEPLOY_DIR/.env"
docker compose --env-file "$DEPLOY_DIR/.env" -f "$DEPLOY_DIR/docker-compose.yml" up -d
```

#### Correct

```bash
test -s "$DEPLOY_DIR/.env"
cd "$DEPLOY_DIR"
docker compose --env-file .env -f docker-compose.yml config --quiet
docker compose --env-file .env -f docker-compose.yml up -d gateway-server system-server infra-server
```
