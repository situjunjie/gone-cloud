# DevOps Artifact Registry Guidelines

DevOps 制品仓库是独立于代码源的外部系统集成，当前实现面向 Nexus Repository Manager 3。

## Scenario: Nexus Artifact Registry Search

### 1. Scope / Trigger

- Trigger: changing artifact registry CRUD, repository sync, Maven search, Docker image search, Nexus client behavior, or `dev_artifact_*` schema.
- Scope: `artifactregistry` controllers, VOs, services, mappers, Nexus client DTOs, SQL bootstrap scripts, permissions, and focused tests.

### 2. Signatures

- Registry APIs:
  - `POST /devops/artifact-registry/create`
  - `PUT /devops/artifact-registry/update`
  - `DELETE /devops/artifact-registry/delete?id={id}`
  - `GET /devops/artifact-registry/get?id={id}`
  - `GET /devops/artifact-registry/page`
  - `POST /devops/artifact-registry/check?id={id}`
- Repository APIs:
  - `POST /devops/artifact-registry/sync-repositories?registryId={registryId}`
  - `GET /devops/artifact-registry/repositories?registryId={registryId}&format=MAVEN2|DOCKER`
- Search APIs:
  - `GET /devops/artifact-registry/search` for Maven.
  - `GET /devops/artifact-registry/search-docker` for Docker images.
- DB tables use the short DevOps prefix:
  - `dev_artifact_registry`
  - `dev_artifact_repository`

### 3. Contracts

- Nexus instance URL is the Nexus Repository Manager base URL, for example `http://192.168.16.102:8081`, not a repository URL like `/repository/maven-public/`.
- Password fields must use `EncryptTypeHandler` and response VOs must not expose raw passwords.
- Repository sync keeps only supported formats for this phase:
  - Nexus `maven2` -> `MAVEN2`
  - Nexus `docker` -> `DOCKER`
  - `npm` is reserved and must not be returned as a supported searchable format until implemented.
- Maven search maps to Nexus `/service/rest/v1/search`:
  - `repository`
  - `q`
  - `maven.groupId`
  - `maven.artifactId`
  - `maven.baseVersion`
  - `continuationToken`
- Docker search maps to Nexus `/service/rest/v1/search`:
  - `repository`
  - `q`
  - `docker.imageName`
  - `docker.imageTag`
  - `continuationToken`
- Do not forward frontend `limit` to Nexus search. Some Nexus versions return empty results when `limit` is sent to `/service/rest/v1/search`; keep pagination aligned to `continuationToken`.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| Unsupported provider type | Throw `ARTIFACT_REGISTRY_TYPE_NOT_SUPPORTED` |
| Unsupported auth type | Throw `ARTIFACT_REGISTRY_AUTH_TYPE_NOT_SUPPORTED` |
| Create without password | Throw `ARTIFACT_REGISTRY_PASSWORD_REQUIRED` |
| Update without password | Preserve old encrypted password and password mask |
| Duplicate registry name in tenant | Throw `ARTIFACT_REGISTRY_NAME_DUPLICATE` |
| Delete registry with synced repositories | Throw `ARTIFACT_REGISTRY_DELETE_FAIL_REPOSITORY_EXISTS` |
| Repository id belongs to another registry | Throw `ARTIFACT_REPOSITORY_NOT_EXISTS` |
| Maven search with Docker repository id | Throw `ARTIFACT_REPOSITORY_FORMAT_NOT_SUPPORTED` |
| Docker search with Maven repository id | Throw `ARTIFACT_REPOSITORY_FORMAT_NOT_SUPPORTED` |
| Nexus connection/search fails | Throw `ARTIFACT_REGISTRY_CONNECTION_FAIL` or `ARTIFACT_SEARCH_FAIL` with a 512-char capped message |

### 5. Good / Base / Bad Cases

- Good: frontend syncs repositories first, lets the user choose a `MAVEN2` or `DOCKER` repository id, then calls the matching search endpoint.
- Base: caller passes `repositoryName` directly when local repository sync has not been run yet.
- Bad: caller uses Maven `/search` for Docker images or sends `limit` expecting Nexus to apply traditional page sizing.
- Bad: backend logs Basic Auth headers, raw passwords, or full Nexus error bodies containing credentials.

### 6. Tests Required

- Service test for create/update password handling.
- Service test for duplicate name and delete-blocked-by-repository errors.
- Service test that repository sync persists Maven and Docker but ignores unsupported formats.
- Service test that Maven and Docker search resolve `repositoryId` only when the format matches the endpoint.
- Nexus client test that Maven search does not include `limit`.
- Nexus client test that Docker search emits `docker.imageName` and `docker.imageTag` and filters mixed-format Nexus responses.
- Focused command:
  - `mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dtest=NexusArtifactRegistryClientTest,ArtifactRegistryServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test`

### 7. Wrong vs Correct

#### Wrong

```java
putIfPresent(params, "name", reqDTO.getImageName());
putIfPresent(params, "version", reqDTO.getTag());
putIfPresent(params, "limit", reqDTO.getLimit());
```

#### Correct

```java
putIfPresent(params, "docker.imageName", reqDTO.getImageName());
putIfPresent(params, "docker.imageTag", reqDTO.getTag());
putIfPresent(params, "continuationToken", reqDTO.getContinuationToken());
```
