# DevOps Repository Source Guidelines

DevOps 应用的代码仓库来源必须通过代码源实体建模。不要只用 GitLab/GitHub/Gitee 这类提供方类型表达仓库归属，因为同一租户可能配置多个同类型代码源。

## Scenario: Application Repository Source Linkage

### 1. Scope / Trigger

- Trigger: changing DevOps application repository fields, code source CRUD, repository project listing, or application create/update APIs.
- Scope: `yudao-module-devops` application controllers, VOs, services, mappers, code source services, and `sql/mysql/devops.sql`.

### 2. Signatures

- Code source project list API:
  - `GET /devops/repository-provider/projects?id={repositoryProviderId}`
  - Response: `List<RepositoryProviderProjectRespVO>`
- Application save API payload:
  - `repositoryProviderId: Long` required
  - `repoIdentifier: String` required, usually GitLab `pathWithNamespace`
  - `repoUrl: String` required
  - `defaultBranchName: String` required
  - `repoProviderType: String` is server-derived from `RepositoryProviderDO.providerType`
- Application response:
  - includes `repositoryProviderId`
  - includes derived `repoProviderType`
- DB signature:
  - `dev_application.repository_provider_id bigint NOT NULL`
  - unique key: `(tenant_id, repository_provider_id, repo_identifier)`

### 3. Contracts

- Create/update application must validate that `repositoryProviderId` exists before saving.
- Create/update application must not trust client-submitted `repoProviderType`; set it from the selected code source.
- Repository uniqueness is scoped to one code source. The same `repoIdentifier` may exist under different `repositoryProviderId` values.
- Deleting a code source must fail when any application references it.
- Application list filtering may support both `repositoryProviderId` and `repoProviderType`; `repositoryProviderId` is the precise source filter.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| `repositoryProviderId` missing in application save request | Bean validation fails with `代码源编号不能为空` |
| `repositoryProviderId` does not exist | Throw `REPOSITORY_PROVIDER_NOT_EXISTS` |
| Same `appKey` already exists in tenant | Throw `APPLICATION_APP_KEY_DUPLICATE` |
| Same `repositoryProviderId + repoIdentifier` already exists in tenant | Throw `APPLICATION_REPO_IDENTIFIER_DUPLICATE` |
| Same `repoIdentifier` exists under a different code source | Allow create/update |
| Delete code source referenced by applications | Throw `REPOSITORY_PROVIDER_DELETE_FAIL_APPLICATION_EXISTS` |

### 5. Good / Base / Bad Cases

- Good: frontend first selects a code source, calls `/devops/repository-provider/projects?id=...`, then submits `repositoryProviderId` plus the selected project metadata to application create/update.
- Base: `repoProviderType` remains stored for existing list display and broad filtering, but backend derives it from the code source.
- Bad: frontend submits only `repoProviderType=GITLAB` and a repo path, because that cannot distinguish multiple GitLab instances.

### 6. Tests Required

- Service test that create application stores `repositoryProviderId`.
- Service test that create/update application overwrites client `repoProviderType` from `RepositoryProviderDO.providerType`.
- Service test that duplicate repository validation uses `repositoryProviderId + repoIdentifier`.
- Service test that deleting a referenced code source throws `REPOSITORY_PROVIDER_DELETE_FAIL_APPLICATION_EXISTS`.
- Compile/test command:
  - `mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dsurefire.failIfNoSpecifiedTests=false test`

### 7. Wrong vs Correct

#### Wrong

```java
ApplicationDO repoIdentifierApplication = applicationMapper.selectByRepoIdentifier(repoIdentifier);
```

#### Correct

```java
ApplicationDO repoIdentifierApplication = applicationMapper
        .selectByRepositoryProviderIdAndRepoIdentifier(repositoryProviderId, repoIdentifier);
```

#### Wrong

```java
ApplicationDO application = ApplicationConvert.INSTANCE.convert(createReqVO);
applicationMapper.insert(application);
```

#### Correct

```java
RepositoryProviderDO repositoryProvider = repositoryProviderService
        .validateRepositoryProviderExists(createReqVO.getRepositoryProviderId());
ApplicationDO application = ApplicationConvert.INSTANCE.convert(createReqVO);
application.setRepoProviderType(repositoryProvider.getProviderType());
applicationMapper.insert(application);
```

## Scenario: GitLab Push Hook Latest Commit Sync

### 1. Scope / Trigger

- Trigger: adding or changing repository webhook callbacks that update DevOps change branch metadata.
- Scope: `RepositoryProviderController`, repository webhook request VOs, `ChangeService`, application/change mappers, and focused change service tests.

### 2. Signatures

- API:
  - `POST /devops/repository-provider/gitlab/push-hook?id={repositoryProviderId}`
  - The endpoint is `@PermitAll` and `@TenantIgnore`; safety is provided by scoping the URL to a concrete code source id. Add token/signature verification before exposing the endpoint to untrusted networks.
- Request:
  - GitLab Push Hook raw JSON body.
  - Required payload fields for matching: `object_kind` or `event_name`, `ref`, `checkout_sha` or `after`, and `project.path_with_namespace`.
- DB write:
  - Updates `dev_change.latest_commit_sha`, `dev_change.latest_commit_message`, and `dev_change.latest_commit_at` for the active change that matches the application repository and pushed branch.

### 3. Contracts

- Resolve the code source by `repositoryProviderId`; do not infer the source from `providerType` because one tenant can configure multiple GitLab providers.
- Validate the resolved code source is `RepositoryProviderTypeEnum.GITLAB`.
- Resolve the application by `repositoryProviderId + project.path_with_namespace`.
- Parse `ref` only when it starts with `refs/heads/`; ignore tags and other refs.
- Match only `ChangeStatusEnum.ACTIVE` changes by `appId + branchName`.
- Treat branch deletion push events (`after` all zeroes and no checkout sha) as ignored events.
- Webhook processing should be idempotent. Unmatched repository, branch, or change returns `false` without throwing.
- Do not log access tokens or full raw payloads from repository webhook callbacks.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| `repositoryProviderId` does not exist | Throw `REPOSITORY_PROVIDER_NOT_EXISTS` |
| code source is not GitLab | Throw `REPOSITORY_PROVIDER_TYPE_NOT_SUPPORTED` |
| event is not a push event | Return `false`, do not update |
| ref is not `refs/heads/*` | Return `false`, do not update |
| push deletes a branch | Return `false`, do not update |
| application not found for provider/project | Return `false`, do not update |
| active change not found for app/branch | Return `false`, do not update |
| matching active change found | Update latest commit fields and return `true` |

### 5. Good / Base / Bad Cases

- Good: configure each GitLab project webhook with the exact platform code source id in the callback URL.
- Base: webhook payload updates the mutable remote-HEAD fields on `dev_change`; deployment/run snapshots remain immutable.
- Bad: matching by `repoProviderType + repoIdentifier`, because two GitLab providers can contain the same namespace path.
- Bad: storing only `changeId` for deployed state and relying on it to detect later pushes; `changeId` does not change when the branch advances.

### 6. Tests Required

- Service test that a matching GitLab Push Hook updates `latestCommitSha`, `latestCommitMessage`, and `latestCommitAt`.
- Service test that branch deletion events are ignored before provider lookup.
- Service test that unmatched active change returns `false` and does not update.
- Service test that non-GitLab providers throw `REPOSITORY_PROVIDER_TYPE_NOT_SUPPORTED` when reached.
- Compile/test command:
  - `mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dtest=ChangeServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test`

### 7. Wrong vs Correct

#### Wrong

```java
ApplicationDO application = applicationMapper.selectByRepoIdentifier(projectPath);
```

#### Correct

```java
ApplicationDO application = applicationMapper
        .selectByRepositoryProviderIdAndRepoIdentifier(repositoryProviderId, projectPath);
```
