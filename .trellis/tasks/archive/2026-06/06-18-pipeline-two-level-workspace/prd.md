# 流水线两级 workspace 设计

## Goal

重构 DevOps 流水线执行引擎的 workspace 模型，将一次运行的隔离工作目录与应用环境流水线级别的持久缓存目录分离。目标是在保证每次运行源码、产物、临时文件隔离的前提下，让 Maven、npm、pnpm、Gradle 等依赖缓存可以跨运行复用，减少重复下载和构建耗时。

## What I Already Know

* 用户希望 workspace 分两级：一级是流水线常驻 workspace/cache，二级是单次流水线运行 workspace。
* 用户明确约束：同一个应用的一条环境流水线同时最多只有一条运行中实例。
* 当前执行引擎在首次遇到 `JOB_RUNTIME` step 时，懒创建 workspace、checkout 源码、启动 Docker job runtime。
* 当前 `LocalPipelineWorkspaceService#createWorkspace` 只创建 `{workspaceRoot}/run-{runId}/job-{jobId}-{uuid}`。
* 当前 `DockerPipelineJobRuntimeManager` 只将 job workspace 挂载到容器 `/workspace`，没有挂载 Maven/npm 等缓存目录。
* 当前 `PipelineRunDO` 已有 `appId`、`applicationEnvId`、`definitionId`、`definitionVersionId`，足够派生应用环境流水线级别的缓存键。
* 当前 `PipelineRunJobDO` 和 `PipelineRunLogDO` 都有 `workspacePath`，可以继续记录本次运行 workspace；持久 cache 不应该暴露给普通日志结果。
* 当前 `PipelineDefinitionDO` 是定义主表，保存名称、app/env、版本指针；`PipelineDefinitionVersionDO` 保存 `diagramJson/specJson/validationResultJson` 等可执行版本内容。
* 用户修正：整条流水线的一次构建中，所有 stage、所有 job 应共用同一个 `runWorkspace`，而不是每个 job 独立 workspace。
* 用户补充：不同流水线应该可以设置不同的缓存目录，页面形态类似“变量和缓存 / 缓存目录”列表，支持添加缓存目录、启用/禁用、清理缓存。
* `.trellis/spec/backend/devops-pipeline-guidelines.md` 现有“每个 job 使用独立 workspace”的约束与本任务目标冲突；本任务完成时需要同步更新该 spec。
* 既有设计文档 `yudao-module-devops/PIPELINE_DOCKER_RUNTIME_DESIGN.md` 已提到 Maven/npm cache，但维度偏租户或资源池；本任务将收敛为应用环境流水线级缓存。

## Requirements

* 引入两级 workspace 概念：
  * `PipelineCacheWorkspace`：应用环境流水线级常驻目录，用于依赖缓存和可选共享元数据。
  * `PipelineRunWorkspace`：单次流水线运行级隔离工作目录，整次 run 内所有 stage/job 共享，用于源码、产物、报告、临时文件。
* 单次运行 workspace 必须按 `runId` 隔离，不同 run 不能复用源码目录、产物目录或临时目录。
* 同一个 run 内的所有 stage/job 默认挂载同一个 run workspace 到容器 `/workspace`，以支持前序 job 生成的文件被后续 job 读取。
* job 内如果需要独立临时目录，使用 run workspace 下的 `jobs/{jobId}/tmp`、`jobs/{jobId}/reports`、`jobs/{jobId}/artifacts` 等约定子目录，不再创建独立宿主 workspace。
* 持久 cache 的默认维度为 `tenantId + appId + applicationEnvId + definitionId`。
* 流水线需要支持定义级缓存目录配置，并随草稿/发布版本保存：
  * 保存草稿时接收缓存配置。
  * 发布时把草稿缓存配置复制到发布版本。
  * 回滚时把目标版本缓存配置复制到新的发布版本。
  * 执行时使用 `PipelineRunDO.definitionVersionId` 对应版本上的缓存配置快照。
* 缓存目录配置只允许声明“容器内缓存路径”和启用状态，不允许用户指定宿主机任意路径。宿主机 cache 子目录由平台根据配置项派生。
* 每条流水线可以有不同的缓存目录列表；未配置时使用平台默认缓存目录。
* 默认缓存目录建议包含：
  * `/root/.m2`
  * `/root/.gradle/caches`
  * `/root/.npm`
  * `/root/.yarn`
  * `/go/pkg/mod`
  * `/root/.cache`
* 缓存目录支持启用/禁用；禁用项不创建宿主机缓存目录、不挂载到容器。
* 缓存目录清理进入本次 MVP。清理动作只删除当前流水线常驻 cache workspace 下对应目录，不影响当前运行中的 run workspace；如果流水线正在运行，应拒绝清理。
* 当 `appId` 或 `applicationEnvId` 缺失时，不能落入全局共享缓存；应使用 run-scoped 临时 cache 或 `standalone/definition-{definitionId}` 这类受限维度，避免不同来源互相污染。
* Docker job runtime 创建容器时挂载：
  * run workspace -> `/workspace`
  * enabled cache directories -> configured container paths
* 命令执行环境变量需要补充缓存路径，例如：
  * `MAVEN_OPTS` 不强制覆盖用户值。
  * `MAVEN_CONFIG=/root/.m2` 或只挂载 `/root/.m2`。
  * `NPM_CONFIG_CACHE=/root/.npm`
  * `PNPM_STORE_PATH=/root/.pnpm-store`
  * `GRADLE_USER_HOME=/root/.gradle`
* 不缓存 `node_modules` 作为第一期能力。依赖下载缓存优先，避免 Node 版本、系统平台、lockfile 变化导致污染。
* 保持“同一应用环境流水线最多一个运行中实例”的业务互斥，但实现中仍应依赖已有 run 状态校验或数据库约束/事务更新来保证入口幂等。
* 清理策略分层：
  * run workspace：按运行结果和保留天数清理，失败运行可以保留更久用于排查。
  * cache workspace：按容量或最后访问时间清理，不随单次运行结束删除。
* 日志、VO、`contextJson`、`resultJson` 不输出宿主机 cache 绝对路径；如需排查，只记录 cache key 或相对路径。
* 第一阶段只支持本地 Docker daemon 的 `local-docker/default` runtime；后续远程 SSH/K8s runner 需要在各 runner 本地维护自己的 cache root。

## Acceptance Criteria

* [ ] 同一应用环境流水线第二次运行 Maven 构建时，容器内 `/root/.m2` 复用第一次运行下载的依赖。
* [ ] 同一应用环境流水线第二次运行 npm 构建时，容器内 npm cache 复用第一次运行下载的包缓存。
* [ ] 两次运行的源码目录、产物目录、临时目录不同，互不污染。
* [ ] 同一次运行内不同 stage/job 看到相同的 `/workspace` 内容，前序 job 写入的文件可被后续 job 读取。
* [ ] 不同应用、不同应用环境、不同流水线定义不会共享同一个持久 cache 目录。
* [ ] 不同流水线定义可以保存不同的缓存目录配置，执行时只挂载当前发布版本启用的缓存目录。
* [ ] 保存草稿、发布、回滚都会保留对应版本的缓存目录配置。
* [ ] 禁用的缓存目录不会挂载到容器。
* [ ] 清理缓存只清理当前流水线对应 cache workspace，不删除 run workspace，不影响其他流水线。
* [ ] 流水线处于 `QUEUED/RUNNING/WAITING_INPUT` 时，清理缓存请求被拒绝。
* [ ] 清理指定缓存目录时，只删除该目录派生出的 host cache path；清理全部时，删除该流水线 definition cache root 下已知 cache 目录。
* [ ] 清理缓存接口有权限控制、错误码和审计/操作日志。
* [ ] 没有应用上下文的流水线不会误用应用环境级 cache。
* [ ] `dev_pipeline_run_job.workspace_path` 记录本次 run workspace，而不是 cache workspace；同一 run 内多个 job 的该字段可以相同。
* [ ] Docker 容器创建参数包含 run workspace 与各工具 cache 的 bind mount。
* [ ] Command step 环境变量能让 npm/pnpm/Gradle 使用挂载的 cache 目录。
* [ ] 取消、失败、成功都会销毁 job runtime，但不会删除持久 cache。
* [ ] 路径生成对用户输入不可控，所有目录名基于内部 id，避免路径穿越。

## Definition of Done

* Tests added/updated for workspace path calculation and Docker bind mount construction.
* Focused DevOps module tests pass.
* Pipeline runtime/design docs updated if the contract changes.
* Cache/run workspace cleanup policy documented, even if scheduled cleanup后置实现。
* Rollout/rollback considered: config 可关闭持久 cache 或切回 run-scoped cache。

## Technical Approach

### Directory Model

Recommended host layout:

```text
${workspaceRoot}/
  tenants/
    tenant-{tenantId}/
      apps/
        app-{appId}/
          envs/
            app-env-{applicationEnvId}/
              pipelines/
                definition-{definitionId}/
                  cache/
                    by-path/
                      {cachePathHash}/
                    metadata/
                  runs/
                    run-{runId}/
                      source/
                      artifacts/
                      reports/
                      tmp/
                      jobs/
                        job-{jobId}/
                          tmp/
                          reports/
                          artifacts/
```

The current implementation clones directly into the job workspace root. The target model should clone once into the run workspace root or into `source/`.

* keep cloning into the run workspace root for minimal change, while still creating `artifacts/reports/tmp`; or
* move to `source/` and update Docker command working dir to `/workspace/source`.

Recommended MVP: keep command working dir as `/workspace` to avoid breaking existing YAML scripts, and defer `/workspace/source` migration unless we explicitly decide to introduce it.

Under this MVP, source checkout should happen once per run workspace. Later jobs must reuse the existing checkout instead of recloning into the same directory.

### Java Model

Add a value object under `framework.pipeline.runtime`, for example:

```java
public class PipelineWorkspace {
    private Path runWorkspace;
    private Path cacheWorkspace;
    private List<PipelineCacheMount> cacheMounts;
    private String cacheKey;
}
```

Add a cache mount value object:

```java
public class PipelineCacheMount {
    private String id;
    private String containerPath;
    private Path hostPath;
    private Boolean enabled;
    private String description;
}
```

Adjust `PipelineWorkspaceService` from returning only `Path` to returning this workspace descriptor:

```java
PipelineWorkspace createWorkspace(PipelineRunDO run, PipelineSpec.ExecutableJob job);
```

The descriptor becomes the single source of truth for:

* host run workspace path
* host cache mount paths derived from version cache config
* sanitized cache key
* future cleanup metadata

Even if the method still receives `job` for logging or future job-subdirectory creation, the returned `runWorkspace` must be stable for the same `runId`.

### Runtime Model

Extend `PipelineJobRuntime`:

```java
private Path workspace;
private String cacheKey;
private List<PipelineCacheMount> cacheMounts;
```

`workspace` is the run-level workspace and continues to fill `PipelineRunJobDO.workspacePath` and `PipelineRunLogDO.workspacePath`. Multiple job runtime containers in the same pipeline run may point to the same workspace path.

### Docker Mounts

`DockerPipelineJobRuntimeManager#createRuntime` should accept the workspace descriptor instead of only a `Path`, or receive cache mounts through `PipelineJobRuntime`.

Container mounts:

```text
runWorkspace  -> /workspace
cacheMount.hostPath -> cacheMount.containerPath
```

The runtime manager should create directories before container creation. It should not delete cache paths in `destroyRuntime`.

The platform should derive each `hostPath` from the persistent cache workspace and a stable hash of `containerPath`, for example:

```text
{cacheWorkspace}/by-path/{sha256(containerPath)}/
```

This avoids unsafe host path input and avoids path characters such as `/` becoming host directory structure.

### Pipeline Cache Configuration

Add a versioned cache configuration JSON field to `PipelineDefinitionVersionDO`, for example `cacheConfigJson`.

Recommended DTO shape:

```json
{
  "directories": [
    {
      "id": "maven",
      "path": "/root/.m2",
      "description": "Maven local repository",
      "enabled": true
    }
  ]
}
```

API and persistence contracts:

* `PipelineSaveDraftReqVO` adds `cacheConfig`.
* `PipelineDefinitionVersionRespVO` returns `cacheConfig`.
* `PipelineDefinitionServiceImpl#fillDraft` stores cache config on the draft version.
* `fillPublishedContent` copies cache config from draft to published version.
* rollback copies cache config from the target published version to the new rollback version.
* existing versions with `cacheConfigJson = null` should resolve to default cache config in read/execute paths.

Validation rules:

* `path` must be an absolute Linux container path beginning with `/`.
* Reject blank path, relative path, path containing `..`, and paths under `/workspace`, `/proc`, `/sys`, `/dev`, `/run`, `/tmp`.
* Deduplicate by normalized path.
* Cap directory count, recommended max 20.
* Description is optional and length-limited.
* `enabled=false` entries may be saved for UI state, but are ignored during runtime mount calculation.

Cache cleanup:

* Add cache cleanup API in this MVP.
* Request can clear all enabled cache directories or a selected `path/id`.
* Service resolves `PipelineDefinitionDO` and current draft/published version as needed, validates no active run exists for the same `applicationEnvId + definitionId`, then deletes only the derived host cache path(s).
* If an active run exists, return a business error instead of deleting files under a running container.

### Versioned Cache Configuration Plan

The cache configuration is part of the executable pipeline version, not mutable definition metadata. It must follow the same lifecycle as `specJson` and `diagramJson`.

#### Storage

Add `cache_config_json` to `dev_pipeline_definition_version`.

```sql
ALTER TABLE dev_pipeline_definition_version
  ADD COLUMN cache_config_json text DEFAULT NULL COMMENT '缓存目录配置 JSON';
```

Recommended Java fields:

* `PipelineDefinitionVersionDO.cacheConfigJson`
* `PipelineSaveDraftReqVO.cacheConfig`
* `PipelineDefinitionVersionRespVO.cacheConfig`
* Optional internal DTOs:
  * `PipelineCacheConfig`
  * `PipelineCacheDirectoryConfig`

Recommended JSON:

```json
{
  "schemaVersion": "1.0",
  "directories": [
    {
      "id": "maven",
      "path": "/root/.m2",
      "description": "Maven local repository",
      "enabled": true
    },
    {
      "id": "npm",
      "path": "/root/.npm",
      "description": "npm package cache",
      "enabled": true
    }
  ]
}
```

`schemaVersion` is optional for MVP but recommended. It gives us room to later add cache modes, max size, retention days, or language presets without guessing old JSON semantics.

#### Lifecycle

* Create first draft:
  * If request has no `cacheConfig`, store default cache config.
  * If request has `cacheConfig`, validate and store normalized config.
* Save existing draft:
  * Replace draft `cacheConfigJson` with normalized request config.
* Publish:
  * Copy `cacheConfigJson` from draft to new published version.
* Rollback:
  * Copy `cacheConfigJson` from target historical version to the new rollback version.
* Execute:
  * Load `PipelineDefinitionVersionDO` using `PipelineRunDO.definitionVersionId`.
  * Resolve cache config from that exact version.
  * `null` or invalid legacy config falls back to platform defaults with a warning log.
* Version list/detail:
  * Return `cacheConfig` with each draft/published version so the UI can render historical configuration.

#### Default Config

Default cache directories:

```json
{
  "schemaVersion": "1.0",
  "directories": [
    {"id": "maven", "path": "/root/.m2", "description": "Maven local repository", "enabled": true},
    {"id": "gradle", "path": "/root/.gradle/caches", "description": "Gradle dependency cache", "enabled": true},
    {"id": "npm", "path": "/root/.npm", "description": "npm package cache", "enabled": true},
    {"id": "yarn", "path": "/root/.yarn", "description": "Yarn cache", "enabled": true},
    {"id": "go-mod", "path": "/go/pkg/mod", "description": "Go module cache", "enabled": true},
    {"id": "user-cache", "path": "/root/.cache", "description": "Generic user cache", "enabled": true}
  ]
}
```

The default should be centralized in one resolver, not duplicated in controller, service, and runtime code.

#### Path Normalization And Validation

Normalize before storing:

* trim spaces;
* collapse repeated `/`;
* remove trailing `/` except root;
* deduplicate by normalized path;
* generate a stable id when `id` is blank, e.g. `cache-<shortHash(path)>`.

Reject:

* blank path;
* relative path;
* root path `/`;
* path containing `..`;
* path under `/workspace`, because run workspace must not be confused with persistent cache;
* path under `/proc`, `/sys`, `/dev`, `/run`;
* path under `/tmp`, because temporary files should belong to run workspace;
* duplicate normalized path;
* nested cache mount conflicts such as `/root/.gradle` and `/root/.gradle/caches` both enabled in the same config.

Recommended limits:

* max 20 directories;
* path length max 255;
* description max 128;
* id max 64 and only `[a-zA-Z0-9._-]`.

#### Host Path Derivation

The user-provided path is always a container path. Host path is platform-derived:

```text
{workspaceRoot}/tenants/tenant-{tenantId}/apps/app-{appId}/envs/app-env-{applicationEnvId}/pipelines/definition-{definitionId}/cache/by-path/{sha256(containerPath)}/
```

Store optional metadata next to cache directories:

```text
cache/metadata/cache-directories.json
```

The metadata file can record `containerPath`, `description`, `lastUsedAt`, and `lastClearedAt` for operations/debugging without exposing host absolute paths through normal run logs.

#### Runtime Mounting

At runtime:

1. Resolve version cache config.
2. Filter enabled directories.
3. Validate again defensively.
4. Convert each container path to host path.
5. Create host directories.
6. Bind mount host path to container path.

Do not mount disabled entries. Do not delete cache directories when the runtime is destroyed.

#### Clear Cache Semantics

Clear cache should be a definition-level operation, not a version-level operation, because the physical cache namespace is `definitionId` scoped and intentionally reused across versions.

Recommended API:

```text
POST /devops/pipeline/cache/clear
```

Permission:

```text
devops:pipeline:update
```

Request:

```json
{
  "definitionId": 1,
  "paths": ["/root/.m2"]
}
```

Rules:

* `paths` empty or absent means clear all known cache paths for the definition.
* If a run is active for `applicationEnvId + definitionId`, reject the operation.
* Resolve clear targets from current draft/published config plus metadata file, so users can still clear cache directories that existed in an older version but are no longer enabled.
* Delete only platform-derived cache directories under the definition cache root.
* Do not follow symlinks.
* Log audit event with `definitionId`, normalized paths, operator, and result.

Recommended request VO:

```java
public class PipelineCacheClearReqVO {
    @NotNull
    private Long definitionId;
    private List<String> paths;
}
```

Recommended service method:

```java
void clearCache(PipelineCacheClearReqVO reqVO, Long userId);
```

Active run statuses:

```java
QUEUED, RUNNING, WAITING_INPUT
```

Clear implementation details:

* Validate definition exists.
* Query active runs by `applicationEnvId` and active statuses; if any run has the same `definitionId`, reject.
* Normalize request paths with the same cache config validator.
* Build allowed clear target set from:
  * draft version `cacheConfigJson`;
  * published version `cacheConfigJson`;
  * `cache/metadata/cache-directories.json`;
  * default config when both version configs are null.
* If request `paths` is not empty, every requested path must exist in the allowed target set.
* Resolve each target to `{definitionCacheRoot}/by-path/{sha256(containerPath)}`.
* Verify each resolved path starts with the definition cache root before deletion.
* Delete recursively without following symlinks.
* Missing cache directories count as success.
* MVP returns `Boolean.TRUE` when clear succeeds. Any failed target throws a business exception; operation logs carry path-level details.

Recommended new errors:

```java
PIPELINE_CACHE_CONFIG_INVALID = "流水线缓存配置无效：{}"
PIPELINE_CACHE_CLEAR_RUNNING = "流水线运行中，不能清理缓存"
PIPELINE_CACHE_CLEAR_PATH_INVALID = "缓存目录不属于当前流水线：{}"
PIPELINE_CACHE_CLEAR_FAIL = "清理流水线缓存失败：{}"
```

Implementation can reuse existing `PIPELINE_RUN_ACTIVE_EXISTS` for the running case if we do not want a dedicated error code, but a dedicated cache-clear message is clearer for the UI.

#### Why Not Store On PipelineDefinitionDO

`PipelineDefinitionDO` represents identity and pointers: app, environment, draft version, published version. Cache config affects execution behavior and must be reproducible by version.

If stored only on the definition:

* rollback would restore YAML but not runtime cache behavior;
* historical versions would show misleading cache config;
* a run created before a config edit could accidentally execute with newer config;
* debugging old runs would be harder because execution environment is not snapshotted.

Therefore `cache_config_json` belongs to `dev_pipeline_definition_version`.

### Missed-Issue Sweep

Potential issues to handle or explicitly defer:

* Active run cleanup race: cache clear must reject active runs for the same application environment pipeline.
* Overlapping mounts: nested paths can shadow each other in Docker and should be rejected.
* Image defaults hidden by mount: mounting `/root/.m2` hides any image-baked files under that path. If users need pre-baked settings, they should mount a narrower cache path or write settings during the run.
* Container user mismatch: default paths assume root containers. Custom images running as non-root may need cache paths like `/home/build/.m2`; the configurable path list covers this.
* Source checkout idempotency: shared run workspace means checkout must happen once per run, not once per job.
* Code merge interaction: if CodeMerge produces `mergedBranch/mergedCommitSha`, source preparation must use that resolved state before downstream runtime jobs.
* Run workspace sharing and future parallelism: sequential jobs are safe; parallel jobs need explicit file ownership conventions before enabling true parallel writes.
* Old published versions: `cache_config_json = null` must remain executable via defaults.
* Cache poisoning boundary: do not share cache across tenants/apps/envs/definitions by default; private registry packages may live in cache.
* Disk pressure: persistent caches need later size/retention cleanup; MVP should at least provide manual clear.
* API read shape: frontend needs both draft and published version cache configs, not only the definition-level latest config.
* Validation consistency: save-draft and validate endpoints should use the same cache config validator if the validate API accepts cache config later.
* Host path safety: clear and mount code must verify derived paths stay under the configured workspace root.
* Partial deletion failure: clear API should fail visibly if a target cannot be deleted; do not silently leave stale cache while returning success.
* Permission boundary: clearing cache changes future build behavior and should use update permission, not query permission.

### Command Environment

`CommandStepHandler#buildEnv` should add defaults only when the user did not provide the same env key:

```text
NPM_CONFIG_CACHE=/root/.npm
PNPM_STORE_PATH=/root/.pnpm-store
GRADLE_USER_HOME=/root/.gradle
```

For pnpm, command examples can use:

```sh
pnpm config set store-dir "$PNPM_STORE_PATH"
pnpm install
```

or the handler can set `npm_config_store_dir`, but the first implementation should avoid overfitting until actual pnpm usage is tested.

### Execution Flow

Current flow:

```text
executeJob
  -> first JOB_RUNTIME step
  -> createWorkspace(run, job): Path
  -> prepare source into workspace
  -> createRuntime(run, job, workspace)
```

Target flow:

```text
executeJob
  -> first JOB_RUNTIME step
  -> load version cache config
  -> createWorkspace(run, job, cacheConfig): PipelineWorkspace
  -> prepare source into workspace.runWorkspace if not already prepared for this run
  -> createRuntime(run, job, workspace)
  -> runtime.workspace = workspace.runWorkspace
  -> runtime.cacheKey/cacheMounts = workspace cache metadata
```

Because multiple jobs share the run workspace, source preparation must be idempotent per run. A simple MVP can record a `sourcePrepared:{runId}` flag in `sharedState`; a more robust version can create a marker file under the run workspace after checkout succeeds.

### Cleanup Policy

MVP:

* Do not delete cache workspace automatically at run completion.
* Keep run workspace after completion to preserve current debugging behavior.
* Add config placeholders:

```yaml
yudao:
  devops:
    pipeline:
      workspace-root: ${java.io.tmpdir}/gone-devops/pipeline-workspaces
      cache-enabled: true
      run-retention-days: 7
      failed-run-retention-days: 14
      cache-retention-days: 30
```

Later:

* scheduled cleanup removes old `runs/run-*` directories by run status and mtime;
* cache cleanup removes cache directories by last access marker or size budget;
* cleanup never follows symlinks.

### Concurrency

Because one application environment pipeline can only have one active run, the persistent cache for `appId + applicationEnvId + definitionId` can be shared without per-tool distributed locks in MVP.

Still required:

* trigger path must atomically reject a second running run for the same `applicationEnvId + definitionId`;
* job-level parallelism inside one pipeline should remain out of scope for the shared-run-workspace MVP unless file write contracts are defined. Shared run workspace is straightforward for sequential jobs; true parallel jobs need explicit conventions to avoid two jobs writing the same paths.

### Security

* Never derive path segments from branch names, repo names, YAML ids without sanitization. Prefer numeric ids for cache path segments.
* Do not expose host cache absolute paths in frontend APIs.
* Do not mount arbitrary YAML-defined host paths.
* Keep Docker socket/custom volumes/privileged mode out of YAML controls.
* Cache can contain package metadata and possibly private registry artifacts; keep tenant/app/env boundaries strict.

## Decision (ADR-lite)

**Context**: The current single-level workspace isolates runs but causes Maven/npm dependency downloads to repeat on every run. The previous job-isolated workspace idea conflicts with the desired pipeline behavior: one pipeline run should have a continuous filesystem context across all stages/jobs.

**Decision**: Use a two-level model: application environment pipeline scoped persistent cache workspace plus run-scoped shared workspace. The persistent cache stores only tool caches; execution always happens in a fresh run workspace, and all stage/job runtimes in that run mount the same workspace.

**Versioning decision**: Cache directory configuration is stored on `dev_pipeline_definition_version.cache_config_json` and follows draft/publish/rollback. The physical persistent cache namespace remains `tenantId + appId + applicationEnvId + definitionId`, so dependency cache can be reused across pipeline versions while each run still uses the cache directory list from its bound `definitionVersionId`.

**Clear-cache decision**: Manual cache clearing is included in the MVP through `POST /devops/pipeline/cache/clear`. The API returns `Boolean`; active runs reject clearing; failures throw business errors instead of returning partial success.

**Consequences**:

* Build speed improves for Maven/npm/pnpm/Gradle workloads after warm-up.
* Disk usage grows because cache persists beyond run completion; cleanup policy becomes necessary.
* Cache contamination risk is bounded by tenant/app/environment/pipeline dimensions.
* Docker runtime creation needs a slightly richer workspace descriptor and more bind mounts.
* Parallel job execution becomes a deliberate later decision because shared writable workspace requires file write conventions.

## Out of Scope

* Caching `node_modules` directories.
* YAML-configurable custom host volume mounts.
* Docker socket mount or privileged build mode.
* Cross-runner shared network cache.
* Backend multi-replica scheduler coordination.
* Full scheduled cleanup implementation if the MVP only adds config placeholders and path structure.
* Changing command working directory from `/workspace` to `/workspace/source` unless explicitly selected.

## Open Questions

* None currently. Requirements are ready for final confirmation before implementation.

## Implementation Plan

### PR1: Workspace model and path calculation

* Add `PipelineWorkspace` descriptor.
* Add `PipelineCacheMount` descriptor and cache config model.
* Change `PipelineWorkspaceService` contract and `LocalPipelineWorkspaceService` implementation.
* Generate run workspace and cache workspace paths from internal ids.
* Derive host cache paths from configured container paths by stable hash.
* Add focused unit tests for path layout and missing app/env fallback behavior.

### PR2: Versioned cache configuration persistence

* Add `cache_config_json` to `dev_pipeline_definition_version`.
* Add cache config fields to draft save request and version response VO.
* Store cache config on draft versions, copy on publish, copy on rollback.
* Add validation for cache paths and defaults for old/null config.
* Add tests for save/publish/rollback preserving cache config.

### PR3: Runtime mounts and env wiring

* Update `PipelineExecutionEngine` to use `workspace.getRunWorkspace()`.
* Update `PipelineJobRuntimeManager` and `DockerPipelineJobRuntimeManager` to mount configured enabled cache directories.
* Extend `PipelineJobRuntime` with cache metadata.
* Add default cache-related env vars in `CommandStepHandler` without overriding user-defined env.
* Add tests or smoke-level assertions for Docker bind mount construction where practical.

### PR4: Clear cache MVP

* Add `PipelineCacheClearReqVO`.
* Add `PipelineDefinitionService#clearCache`.
* Add `POST /devops/pipeline/cache/clear` with `devops:pipeline:update`.
* Add active-run guard for `QUEUED/RUNNING/WAITING_INPUT`.
* Add safe recursive delete helper that refuses paths outside definition cache root and does not follow symlinks.
* Add error codes for invalid cache config/path, running pipeline, and delete failure.
* Add tests for:
  * clearing all cache directories;
  * clearing selected cache directory;
  * active run rejects clear;
  * invalid path rejects clear;
  * missing cache directory is success;
  * deletion outside cache root is impossible.

### PR5: Docs and config

* Add cache-related config properties.
* Update `.trellis/spec/backend/devops-pipeline-guidelines.md`, `PIPELINE_DOCKER_RUNTIME_DESIGN.md`, and/or `PIPELINE_YAML_SPEC.md` examples.
* Document cleanup policy and future scheduled cleanup.
* Add rollback switch: disable persistent cache and use run-scoped cache if needed.

## Technical Notes

* Relevant files inspected:
  * `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/framework/pipeline/runtime/PipelineWorkspaceService.java`
  * `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/framework/pipeline/runtime/LocalPipelineWorkspaceService.java`
  * `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/framework/pipeline/runtime/DockerPipelineJobRuntimeManager.java`
  * `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/framework/pipeline/runtime/PipelineJobRuntime.java`
  * `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/service/pipeline/execution/PipelineExecutionEngine.java`
  * `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/service/pipeline/execution/handler/CommandStepHandler.java`
  * `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/dal/dataobject/pipeline/PipelineRunDO.java`
  * `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/dal/dataobject/pipeline/PipelineDefinitionDO.java`
  * `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/dal/dataobject/pipeline/PipelineDefinitionVersionDO.java`
  * `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/controller/admin/pipeline/vo/PipelineSaveDraftReqVO.java`
  * `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/controller/admin/pipeline/vo/PipelineDefinitionVersionRespVO.java`
  * `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/dal/dataobject/pipeline/job/PipelineRunJobDO.java`
  * `yudao-module-devops/PIPELINE_DOCKER_RUNTIME_DESIGN.md`
* Relevant specs:
  * `.trellis/spec/backend/devops-pipeline-guidelines.md`
  * `.trellis/spec/guides/cross-layer-thinking-guide.md`
