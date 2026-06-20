# 制品仓库 Nexus Maven 搜索 - Technical Design

## Architecture

新增能力落在 `yudao-module-devops`，作为独立领域 `artifactregistry` 实现，不复用现有代码源 `repositoryprovider` 模型。

主要边界：

- Controller：管理后台 REST API，路径前缀 `/devops/artifact-registry`。
- Service：业务校验、实例 CRUD、仓库同步、搜索编排。
- DAL：MyBatis-Plus DO / Mapper。
- Client：封装 Nexus REST API 调用，避免业务 Service 直接拼 HTTP 细节。
- SQL：`sql/mysql/devops.sql` 增加表；`sql/mysql/devops-menu.sql` 增加菜单权限；字典按需要补充 `sql/mysql/devops-dict.sql`。

## Data Model

### `dev_artifact_registry`

表示一个制品仓库实例，目前仅 Nexus3。

关键字段：

- `id`
- `name`
- `provider_type`: `NEXUS3`
- `server_url`
- `auth_type`: `USERNAME_PASSWORD`
- `username`
- `password`: 加密存储，DO 使用 `EncryptTypeHandler`
- `password_mask`
- `status`
- `last_check_time`
- `last_check_status`
- `last_check_message`
- `remark`
- `creator/create_time/updater/update_time/deleted/tenant_id`

索引：

- `uk_tenant_name(tenant_id, name)`
- `idx_tenant_provider_type(tenant_id, provider_type)`
- `idx_tenant_status(tenant_id, status)`

### `dev_artifact_repository`

表示 Nexus 实例中的一个仓库配置。

关键字段：

- `id`
- `registry_id`
- `repository_name`
- `format`: `MAVEN2` / `DOCKER`，由 Nexus `maven2` / `docker` 标准化而来
- `repository_type`: `HOSTED / PROXY / GROUP`
- `url`
- `online`
- `status`
- `last_sync_time`
- `remark`
- `creator/create_time/updater/update_time/deleted/tenant_id`

索引：

- `uk_tenant_registry_repository(tenant_id, registry_id, repository_name)`
- `idx_tenant_registry_id(tenant_id, registry_id)`
- `idx_tenant_format(tenant_id, format)`
- `idx_tenant_status(tenant_id, status)`

## API Contracts

### Registry CRUD

- `POST /devops/artifact-registry/create`
- `PUT /devops/artifact-registry/update`
- `DELETE /devops/artifact-registry/delete?id=`
- `GET /devops/artifact-registry/get?id=`
- `GET /devops/artifact-registry/page`
- `POST /devops/artifact-registry/check?id=`

权限：

- `devops:artifact-registry:create`
- `devops:artifact-registry:update`
- `devops:artifact-registry:delete`
- `devops:artifact-registry:query`

### Repository APIs

- `POST /devops/artifact-registry/sync-repositories?registryId=`
  - 调用 Nexus `/service/rest/v1/repositories`
  - 保留 `format=maven2` 和 `format=docker`
  - upsert 到 `dev_artifact_repository`
- `GET /devops/artifact-registry/repositories?registryId=&format=MAVEN2|DOCKER`
  - 返回本地已同步 Maven 或 Docker 仓库列表

### Maven Search

- `GET /devops/artifact-registry/search`

请求参数：

- `registryId`
- `repositoryId` 或 `repositoryName`
- `keyword`
- `groupId`
- `artifactId`
- `version`
- `continuationToken`

### Docker Search

- `GET /devops/artifact-registry/search-docker`

请求参数：

- `registryId`
- `repositoryId` 或 `repositoryName`
- `keyword`
- `imageName`
- `tag`
- `continuationToken`

响应：

- `items`
  - `repository`
  - `imageName`
  - `tag`
  - `path`
  - `downloadUrl`
  - `lastModified`
- `continuationToken`
- `limit`

响应：

- `items`
  - `repository`
  - `groupId`
  - `artifactId`
  - `version`
  - `baseVersion`
  - `classifier`
  - `extension`
  - `path`
  - `downloadUrl`
  - `lastModified`
- `continuationToken`

## Nexus Integration

新增 client 层：

- `ArtifactRegistryClient`
- `NexusArtifactRegistryClient`
- `NexusArtifactRegistryClientFactory`

接口建议：

```java
void checkConnection(ArtifactRegistryDO registry);
List<ArtifactRepositoryDTO> listRepositories(ArtifactRegistryDO registry);
ArtifactSearchResultDTO searchMaven(ArtifactRegistryDO registry, ArtifactSearchReqDTO query);
ArtifactDockerSearchResultDTO searchDocker(ArtifactRegistryDO registry, ArtifactDockerSearchReqDTO query);
```

Nexus API 映射：

- 连接检测：优先请求 `/service/rest/v1/status` 或 `/service/rest/v1/repositories`。
- 仓库列表：`GET /service/rest/v1/repositories`。
- Maven 搜索：`GET /service/rest/v1/search`，参数映射：
  - `repository`
  - `q`
  - `maven.groupId`
  - `maven.artifactId`
  - `maven.baseVersion` 或 `maven.version`
  - `continuationToken`
- Docker 搜索：`GET /service/rest/v1/search`，参数映射：
  - `repository`
  - `q`
  - `docker.imageName`
  - `docker.imageTag`
  - `continuationToken`

HTTP 错误处理：

- 不记录用户名密码。
- Nexus 异常消息截断到 512 字符。
- 连接检测失败更新 `last_check_status=FAIL` 后抛业务错误。
- 搜索失败抛 `ARTIFACT_SEARCH_FAIL`。

## Extension Design

后续 npm 支持只需要扩展：

- `ArtifactRepositoryFormatEnum.NPM`
- Nexus repository `format=npm` 映射。
- 新增 npm 搜索 VO/DTO 或在统一搜索响应里增加 npm 字段。
- Controller 可新增 `/search-npm`，避免 Maven 坐标字段污染 npm 语义。

## Compatibility

- 不修改现有 `devops_repository_provider`、应用、流水线表结构。
- 新 SQL 均使用 `CREATE TABLE` 段落追加；菜单 SQL 使用 `WHERE NOT EXISTS` 风格，保持可重复导入。
- 密码更新沿用现有代码源模式：更新请求密码为空时保留旧值和旧掩码。

## Risks

- Nexus 搜索字段在不同版本间可能存在细节差异，client 层要对缺失字段容错。
- 当前项目没有前端源码，因此只能保证后端 API 和菜单权限数据，页面对接需另行实现。
- 如 Nexus 搜索响应较大，一期不要持久化或缓存，避免引入一致性和容量风险。
