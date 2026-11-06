# DevOps Pipeline Guidelines

DevOps 流水线定义由平台持有，Jenkins 在当前阶段只作为构建/测试/Jenkinsfile 执行器的下游目标。不要把流水线定义所有权下放到 Jenkins Job 配置里。

## Scenario: Pipeline YAML Configuration

### 1. Scope / Trigger

- Trigger: changing pipeline DSL parsing, validation, node registry, execution ordering, or release-page pipeline read model.
- Scope: `PipelineSpec`, `PipelineSpecValidationServiceImpl`, `PipelineNodeRegistryServiceImpl`, `PipelineExecutionEngine`, pipeline step handlers, release current-run/read-model builders, and focused pipeline tests.
- Frontend/backend integration must follow the contract document at `yudao-module-devops/PIPELINE_YAML_SPEC.md`. Keep that document synchronized when the YAML shape, validation rules, save/publish APIs, or first-version execution limits change.

### 2. Signatures

- Configuration shape is YAML/JSON using:
  - `sources.<sourceId>.type/name/endpoint/branch`
  - `stages.<stageId>.name/jobs`
  - `stages.<stageId>.jobs.<jobId>.name/runsOn/needs/steps`
  - `runsOn.group`
  - `runsOn.container`
  - `needs` as an optional job dependency list. A single scalar string is accepted and normalized to a list.
  - `steps.<stepId>.name`
  - `steps.<stepId>.step`
  - `steps.<stepId>.with`
- `Command` steps execute `with.run`.
- `PipelineSpec#toExecutableSteps()` flattens `stages.jobs.steps` into `PipelineSpec.ExecutableStep` items that carry `stageId/stageName/jobId/jobName/runsOn/stepId/name/step/with`.
- Execution scheduling should use a job DAG derived from `stages.jobs.<jobId>.needs`, not declaration-order step flattening.
- Execution handlers receive `PipelineSpec.ExecutableStep` through `PipelineNodeContext#getStep()`. Do not reintroduce a `PipelineSpec.Node` execution adapter.

### 3. Contracts

- Do not reintroduce the old user-facing `nodes/edges` graph DSL. The product is not online yet, so no backward-compatible config path is required.
- Frontend can follow the backend's new read model. New/changed run read APIs should return stage/job/step structures instead of compatibility `nodes/edges`.
- Job scheduling is DAG-based. Jobs without `needs` are ready immediately and may run in parallel, subject to executor resource limits. Jobs with `needs` run only after all dependency jobs succeed.
- Stage order is display/grouping order only; it is not an implicit execution barrier. Cross-stage sequencing must be expressed with `needs`.
- `needs` supports cross-stage dependencies by `jobId`. Therefore `jobId` must be unique across the whole pipeline, not just inside one stage.
- The dependency graph must be acyclic. Self-dependencies, missing dependency jobs, and cycles are validation errors.
- Persist job-level DAG state in `dev_pipeline_run_job`. Valid job statuses are `PENDING`, `RUNNING`, `BLOCKED`, `SUCCESS`, `FAILED`, `SKIPPED`, and `CANCELED`.
- First implementation does not support backend service multi-replica scheduling. If worker lease fields such as `worker_id` and `lease_until` are added, treat them as reserved fields until DB/Redis coordination is implemented.
- If a dependency job fails or is canceled, dependent jobs should be marked `SKIPPED` and not scheduled.
- `BLOCKED` is the job-level state for a suspended step. The concrete step log may still use `WAITING_INPUT` to show which step is waiting for approval, conflict resolution, or another external event.
- Run aggregate status is derived from job states: any `RUNNING` job keeps the run `RUNNING`; any `BLOCKED` job with no running jobs maps the run to `WAITING_INPUT`; all-success jobs map to `SUCCESS`; any `FAILED/SKIPPED` terminal graph maps to `FAILED`; user cancellation maps to `CANCELED`.
- Job retry is job-level, not single-step retry. A retry increments `attempt`, creates a fresh runtime and job-scoped temp/report/artifact subdirectories under the run workspace, starts from the first step, and does not rerun already successful upstream jobs.
- Resuming a `BLOCKED` job re-enters the suspended step through an idempotent handler; it must not hold a Docker runtime or executor permit while blocked.
- Canceling a run cancels `PENDING/BLOCKED/RUNNING` jobs, interrupts running commands, destroys runtime, and calls platform-step cancellation hooks where applicable.
- First implementation defaults: `failStrategy` is fail-fast only, `retryTimes` defaults to 0, job timeout defaults to 1800 seconds, and `runsOn.group` supports only `local-docker/default`.
- First implementation must support multiple `PipelineRun` instances for different application environments building/deploying concurrently, with all ready jobs sharing executor-group concurrency limits. The same application environment pipeline may have only one active run in `QUEUED/RUNNING/WAITING_INPUT`.
- First implementation assumes a single backend service scheduler. Backend service multi-replica scheduling is a later enhancement and must use DB/Redis coordination for job claiming, executor permits, and lease expiry.
- An application environment has only one effective pipeline definition at a time. Multiple `PipelineRun` instances may still exist historically or concurrently for different application environments.
- YAML `sources` is the optional source workspace declaration. First implementation supports at most one source, only `type: gitlab`; when `sources` is absent and the run has an application, checkout uses the application-linked GitLab repository and resolves the target branch/commit from merged branch/commit, execution context system state, `PipelineRun.branchName` / `commitSha`, then the application default branch. Source checkout happens once into the run workspace before the first source-requiring step needs it. Only create an empty temporary workspace when both `sources` and application context are absent. For `submit-branch` runs, do not run a hidden pre-merge step.
- Because the shared run workspace may be pre-created with platform-managed directories such as `artifacts/`, `reports/`, `tmp/`, and `jobs/{jobId}/...`, source checkout must not assume the run workspace root is empty. Prepare the Git repository in place without deleting those platform directories, and do not rely on `git clone ... .` against a non-empty run workspace.
- The run workspace is created when `PipelineExecutionContext` is initialized, before any step handler runs. `PLATFORM` steps can read the same workspace path without creating Docker runtime resources.
- Job runtime is lazy-created as execution context internal state. `PLATFORM` steps must not create or hold Docker containers; the first `JOB_RUNTIME` step in a job creates the runtime.
- Application environment pipeline builds use one stable workspace scoped by `tenantId + appId + applicationEnvId + definitionId`, mounted into every job runtime as `/workspace`. Do not create per-run directories such as `runs/run-{runId}/workspace` for normal application-environment builds; only standalone runs without application/environment context may fall back to run-scoped directories. Job-specific scratch output belongs under workspace subdirectories such as `jobs/{jobId}/tmp`, `jobs/{jobId}/reports`, and `jobs/{jobId}/artifacts`.
- The container mount point must match the platform-side workspace identity. The stable app-env-definition workspace is what makes cross-run Git object reuse possible; never derive it from `runId`, because a per-run directory gives every run an empty workspace and re-downloads the whole history.
- `resetRunWorkspace` clears the run workspace worktree but must preserve `.git`. Keeping the object database is what lets the next run of the same app-env-definition fetch incrementally instead of re-downloading the whole history; clearing everything else removes stale untracked build output such as `target/` and `node_modules/`. Do not implement this with `git clean -ffdx`, because that would also delete the platform directories `artifacts/`, `reports/`, `tmp/`, and `jobs/`.
- When reusing the stable application-environment workspace, source preparation must refresh the existing Git repository by updating `origin`, fetching the target branch or commit, and checking it out. Do not skip source preparation merely because `.git` already exists. If `CodeMerge` already produced the deploy branch in the run workspace, mark source preparation satisfied and do not let later source preparation overwrite the merged workspace.
- Dependency caches are separate from the run workspace. Cache directories are versioned on `dev_pipeline_definition_version.cache_config_json`, scoped by `tenantId + appId + applicationEnvId + definitionId`, and mounted by derived host paths such as `{cacheRoot}/by-path/{sha256(containerPath)}`. User input may declare only container paths and enabled state, never host paths.
- Default cache directories include `/root/.m2`, `/root/.gradle/caches`, `/root/.npm`, `/root/.pnpm-store`, `/root/.yarn`, `/go/pkg/mod`, and `/root/.cache`. Docker `Command` steps should provide default cache env values such as `MAVEN_CONFIG=/root/.m2`, `NPM_CONFIG_CACHE=/root/.npm`, `PNPM_STORE_PATH=/root/.pnpm-store`, and `GRADLE_USER_HOME=/root/.gradle` without overriding user-provided env.
- Cache cleanup is part of the MVP. `POST /devops/pipeline/cache/clear` requires `devops:pipeline:update`, rejects active runs in `QUEUED/RUNNING/WAITING_INPUT`, validates requested paths against versioned/recorded cache directories, and deletes only platform-derived cache paths for the current pipeline definition.
- Variable expansion must go through a shared resolver. First phase supports simple `${VAR}` substitution only, and credential values must be masked in logs, `contextJson`, and `resultJson`.
- Step output variables should be published through the run output registry, persisted in `resultJson.outputs`, remain scoped to the same pipeline run, and must not contain secrets. Do not merge generic step outputs into shared/system state with unscoped `putAll`; keep outputs namespaced by job id, step id, and key so parallel jobs cannot overwrite each other.
- `ArtifactUpload` and `UnitTestReport` should write standard metadata to `resultJson.artifacts` and `resultJson.reports` using workspace-relative paths, never host absolute paths.
- First implementation does not expose YAML-configurable Docker socket mount, custom host volumes, `privileged`, or custom network mode.
- Full build logs and artifact/report URLs may be deferred; DB stores sanitized summaries and workspace-relative metadata first.
- Step ids must be unique across the whole pipeline because run logs and frontend step detail lookups use `stepId`.
- Parser accepts both JSON and YAML text in `specJson`; YAML is the primary authoring format.
- Code, comments, logs, class names, and tests must use neutral product wording such as “流水线 YAML” or “Pipeline YAML”; do not name external competitor products in implementation artifacts.
- Unknown step types fail validation unless registered in `PipelineNodeRegistryServiceImpl`.
- First implementation needs real `Command`, `CodeMerge`, `ChangePublishFinalize`, and `APPROVAL` step handlers. `Command` executes `with.run` in the Docker job runtime by default, or through the explicitly referenced environment host when `with.sshHostId` is present. `CodeMerge` is a platform step that merges branch arrays or submit-time change branches before downstream build jobs. `ChangePublishFinalize` is a platform step that runs after deployment, merges the published deploy branch into the application baseline branch, marks the participating changes released, and deletes both the remote change branches and the deploy branch. `APPROVAL` is a platform step backed by the BPM process instance API.
- `CodeMerge` Git workspace preparation must fetch the latest remote baseline branch before merging change branches. When the target deploy branch already exists and differs from the baseline branch, checkout the remote deploy branch and merge the latest baseline commit into it first; when the deploy branch is missing, create it from the latest baseline; when target equals baseline, skip the self-merge. Empty-change CodeMerge runs must use the prepared HEAD as the deploy commit so baseline-only changes still flow downstream.
- Before merging each CodeMerge item, fetch that item branch into the current merge workspace even when `ChangeDO.latestCommitSha` already stores a snapshot SHA. The snapshot SHA is still the merge target, but the fetch is required so the local repository has the commit object; otherwise Git may fail with `not something we can merge` instead of producing conflict entries for the online conflict flow.
- CodeMerge fetches use `--filter=blob:none` (partial clone) so a cold workspace transfers commit/tree objects only and pulls blobs on demand. This is the supported way to cut CodeMerge transfer volume.
- **CodeMerge must never use shallow clone (`--depth`).** `git merge --no-ff --no-commit <sha>` needs a common ancestor plus the full side branch; a shallow boundary truncates history and Git fails with `refusing to merge unrelated histories`. This was verified for the baseline fetch, the item-branch fetch, and both together, so no CodeMerge fetch may be shallow. The source-checkout path (`GitlabPipelineSourceWorkspacePreparer`) is different and may keep `--depth 1`, because it only materializes a single branch tip and never merges.
- Kubernetes deployment steps are platform steps. `K8sDeploy` defaults to `RAW_MANIFEST`, creating or replacing Services plus exactly one `Deployment` from `with.manifestYaml`. `ORCHESTRATION` reads the application's K8S_DEPLOYMENT draft and supports six resource types with exactly one enabled Deployment; see the application orchestration scenario below. `K8sImageUpgrade` only updates an existing Deployment and fails when the workload or target container does not exist.
- Kubernetes deployment steps are synchronous health gates. Applying YAML is not step success: Deployment, ReplicaSet, Pods and containers must be healthy. RAW_MANIFEST also checks Service Endpoints; ORCHESTRATION applies other resources without additional health gates.
- Deployment-order-backed platform steps must return a `StepResult` to the execution engine and must not directly mark the whole `PipelineRun` success or failed from the deployment service. Run aggregate status belongs to `PipelineExecutionEngine`.
- `PrivateRegistryDockerBuild` is a platform step. It must prepare or reuse the run-level source workspace through the shared workspace preparer, build and push with the platform `DockerClientFactory`/docker-java client, and must not create a job runtime or require `runsOn`.
- `PrivateRegistryDockerBuild` supports only `certificate.type=usernamePassword` in the current implementation. Validation must reject `serviceConnection` clearly, and handlers must never write `certificate.password` to logs, `contextJson`, `resultJson`, or step outputs.
- Built-in steps other than `Command` should not silently succeed in the first implementation. If encountered before their handlers exist, validation or execution must return a clear unsupported-step error.
- `dev_pipeline_run_log` is YAML-first storage: persist `stage_id/stage_name/job_id/job_name/step_id/step_type/step_name`, runtime fields such as `runtime_type/executor_group/executor_image/runtime_id/runtime_name/workspace_path`, and `duration_millis`.
- Command step live output is stored primarily in workspace step log files and streamed with SSE. `dev_pipeline_run_log_line` is a legacy read fallback for older runs only. Treat `id`/`afterId` as an opaque backend cursor, not as a guaranteed table row id, because file-backed runs use a step-file byte-offset cursor.
- Do not keep Java/API compatibility aliases named `nodeId/nodeType/nodeName` in new or changed pipeline run APIs. Use `stepId/stepType/stepName` and stage/job fields directly. Do not add `node_id/node_type/node_name` back to the database schema.
- New execution or Docker-runtime code should fill runtime fields on run logs. Platform-control steps such as code merge, approval, and deployment use `runtime_type=PLATFORM`; containerized build steps use `runtime_type=DOCKER`.
- The execution extension point is `PipelineStepHandler`, not the old graph-oriented node handler. New code should use `PipelineStepHandler`, `PipelineExecutionContext`, `StepResult`, and `PipelineStepHandlerRegistry`; migrate existing `*NodeHandler` classes to `*StepHandler` before implementing Docker job runtime.
- `StepResult` should carry type, summary, sanitized error code/message, and non-sensitive outputs so the engine can update step log, job state, and run aggregate consistently.
- New pipeline interfaces must include Javadocs on the interface and each method, including parameter and return semantics. New pipeline model/context/result/DO fields must include field comments. Implementation classes do not need extra comments unless the logic is non-obvious.

## Scenario: Docker Image Export Public Download URL

### 1. Scope / Trigger

- Trigger: changing `DockerImageExportObjectStorage` step outputs or its `IMAGE_ARCHIVE_DOWNLOAD` action.
- Scope: export step handler, application release current-run read model, focused handler/application tests, and `PIPELINE_YAML_SPEC.md`.

### 2. Signatures

- Step output fields: `storagePath` (`s3://bucket/key`) and `downloadUrl` (公开 HTTP(S) URL).
- Download action: `actions[].target.url` must equal `downloadUrl`; `fileName` remains the configured archive file name.

### 3. Contracts

- After successful upload, preserve `storagePath` and add `downloadUrl` to `resultJson` and `StepResult.outputs`.
- Build `downloadUrl` from `storage.endpoint`, `storage.path`, and `forcePathStyle`; encode object-key segments while preserving `/` separators.
- `forcePathStyle=true` uses `{endpoint}/{bucket}/{key}`. Otherwise, an endpoint without a custom path uses `{bucket}.{endpoint-host}/{key}`; endpoints with a custom path use path-style.
- The object is assumed public. AccessKey/SecretKey and registry passwords remain execution-only and must never enter result JSON, action payload, or logs.

### 4. Validation & Error Matrix

- Invalid endpoint, endpoint credentials/query/fragment, or missing bucket/key -> step parameter failure and no download action.
- Upload failure -> no `downloadUrl` output/action is exposed as a successful download.

### 5. Good/Base/Bad Cases

- Good: `s3://bucket/images/a b.tar` produces an HTTP(S) URL with encoded key and the action targets that URL.
- Base: existing consumers continue reading unchanged `storagePath`.
- Bad: use `s3://...` as `target.url`, or include storage credentials in outputs.

### 6. Tests Required

- Handler success tests assert `downloadUrl` in outputs/resultJson and equality with action `target.url`.
- Application current-run tests assert the allowlisted `downloadUrl` survives read-model filtering.

### 7. Wrong vs Correct

Wrong: `target.url = storagePath`.

Correct: compute one public `downloadUrl`, write it to outputs and use it for `target.url`, while retaining `storagePath` for object identity.

## Scenario: Kubernetes Deployment Step Health Gate

### 1. Scope / Trigger

- Trigger: changing `K8sDeploy`, `K8sImageUpgrade`, deployment-order rollout waiting, Kubernetes live status, or manifest Service handling.
- Scope: `DeploymentOrderServiceImpl`, `KubernetesDeploymentStatusCollector`, `KubernetesDeploymentManifestSupport`, deployment result snapshots, and focused handler/service tests.

### 2. Signatures

- `K8sDeploy` manifest supports exactly one `Deployment`, optionally with one or more core/v1 `Service` resources.
- `K8sImageUpgrade` waits on the same Deployment/ReplicaSet/Pod/container health contract with an empty Service list.
- Deployment result/live status uses additive optional snapshot fields without a database migration:
  - `events: List<Map<String, Object>>`: historical Kubernetes events, including recovered Warnings.
  - `health: Map<String, Object>`: current health-gate result with `ready/terminal/reason/message`.
  - `diagnosis: Map<String, Object>`: current primary recoverable diagnosis while the gate is not ready.
  - `failure: Map<String, Object>`: final execution failure or explicit terminal Kubernetes failure only.
  - `services: List<Map<String, Object>>`: optional Service Endpoint coverage summaries.

### 3. Contracts

- Apply every supported YAML document successfully before health success is possible.
- Correlate the target ReplicaSet and Pods by `gone.devops/deployment-order-id`, Deployment UID, and owner references; old ReplicaSet Pods must never satisfy the gate.
- Require Deployment rollout ready, target ReplicaSet desired/ready replicas equal desired, exactly desired non-terminating target Pods, Pod `Running` plus `Ready=True`, all normal containers running/ready, successful init containers, and the target container spec image equal to the deployment-order image.
- Deployment-only manifests skip Service checks and may succeed when the workload health gate passes.
- When Services exist, every Service must have ready Endpoints covering every target Pod UID; Pod IP is only a fallback when an Endpoint address has no Pod targetRef.
- Continue waiting on recoverable states such as image pull, scheduling, mount, crash loop, or probe failures until `rolloutTimeoutSeconds`; preserve their diagnosis in live/final snapshots.
- Do not promote a historical Warning event directly to `failure`. Kubernetes events outlive the condition that produced them, so a successful snapshot with `health.ready=true` may still contain historical `Unhealthy` events but must not contain `diagnosis` or `failure`.
- While `health.ready=false` and `health.terminal=false`, expose the most relevant recoverable problem as `diagnosis` and keep `failure` absent. On timeout, keep `ROLLOUT_TIMEOUT` as the primary `failure.reason` and nest the Kubernetes diagnosis under `failure.diagnosis`.
- Success persistence must explicitly clear stale `diagnosis/failure`. Deployment-order reads must also normalize legacy persisted data when `deployStatus=SUCCESS` and `result.health.ready=true`, so old contradictory snapshots do not leak a failure response.
- Cancellation must be checked during waiting and again before returning success.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| Deployment-only manifest | Skip Service Endpoint gate |
| Service is `ExternalName` | Reject as deployment node parameter invalid |
| Service selector is empty | Reject as deployment node parameter invalid |
| Service selector does not match Pod Template labels | Reject before apply |
| Service exists with desired replicas 0 | Reject because Endpoint coverage cannot succeed |
| Target ReplicaSet/Pod is missing or unhealthy | Keep waiting until ready, canceled, terminal Deployment failure, or timeout |
| Readiness probe fails during application startup | Keep waiting; expose `diagnosis.reason=Unhealthy`, not `failure` |
| Pod becomes healthy after historical `Unhealthy` events | Keep the events; return `health.ready=true` without `diagnosis/failure` |
| Recoverable diagnosis persists until rollout timeout | Fail with `failure.reason=ROLLOUT_TIMEOUT` and nested `failure.diagnosis` |
| Deployment reports `ProgressDeadlineExceeded` | Fail immediately with the Kubernetes diagnosis |
| Service Endpoints miss any target Pod | Keep waiting and expose missing Pod UIDs |

### 5. Good / Base / Bad Cases

- Good: a Deployment-only manifest returns step success after all current-order Pods and containers are healthy.
- Good: a Service plus Deployment returns success only after ready Endpoint addresses cover all target Pod UIDs.
- Good: a slow-starting application may accumulate Readiness `Unhealthy` events, then finish successfully with those events retained and no top-level failure.
- Base: temporary `ImagePullBackOff` remains running and retains diagnosis until it recovers or times out.
- Base: legacy successful result JSON may contain a stale failure; the detail read model removes it only when persisted health is explicitly ready.
- Bad: treating `availableReplicas == desired` as sufficient, because available replicas may belong to an old ReplicaSet.
- Bad: requiring a Service for every Deployment, because worker/internal workloads may intentionally be Deployment-only.
- Bad: treating any `Warning/Unhealthy` event as current deployment failure, because Kubernetes events are historical records.

### 6. Tests Required

- Manifest tests for Deployment-only, valid Service, `ExternalName`, empty selector, selector mismatch, and zero replicas with Service.
- Health tests proving old ReplicaSets cannot satisfy the gate, Pod/container/init-container health is enforced, Deployment-only can pass, and Service Endpoints missing/full coverage behave correctly.
- Health tests proving a recovered historical `Unhealthy` event remains in `events` without producing `diagnosis/failure`, while an unready Pod exposes it as `diagnosis` only.
- Service tests proving the Step remains synchronous, final `health.ready=true` is persisted without stale failure, cancellation cannot return success, timeout keeps `ROLLOUT_TIMEOUT + failure.diagnosis`, and legacy successful details are normalized on read.
- Run the focused deployment, collector, manifest, `K8sDeploy`, and `K8sImageUpgrade` test suite plus DevOps module compile.

### 7. Wrong vs Correct

#### Wrong

```java
if (deployment.getStatus().getAvailableReplicas() == desired) {
    return StepResult.continueRun();
}
```

#### Correct

```java
KubernetesDeploymentHealthResult health = collector.evaluateHealth(client, order, deployment, services);
if (health.isReady() && !isCanceled(order.getId())) {
    return deployment;
}
```

#### Wrong

```java
// Historical Warning does not prove the current deployment failed.
events.stream().filter(event -> "Unhealthy".equals(event.get("reason")))
        .findFirst().ifPresent(event -> snapshot.put("failure", event));
```

#### Correct

```java
evaluateHealthGate(..., snapshot);
if (!Boolean.TRUE.equals(((Map<?, ?>) snapshot.get("health")).get("ready"))) {
    snapshot.put("diagnosis", diagnoseIssue(pods, events, deployment));
}
```

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| Blank spec text | Validation error `SPEC_REQUIRED` |
| Invalid YAML/JSON | Validation error `SPEC_INVALID` |
| Missing stages | Validation error `STAGE_REQUIRED` |
| Stage without jobs | Validation error `JOB_REQUIRED` |
| Job without steps | Validation error `STEP_REQUIRED` |
| Invalid source/stage/job/step id | Validation error on the corresponding id field |
| Duplicate job id across stages | Validation error `JOB_ID_DUPLICATE` |
| Duplicate step id | Validation error `STEP_ID_DUPLICATE` |
| `needs` references missing job id | Validation error `JOB_NEEDS_NOT_FOUND` |
| `needs` references the same job | Validation error `JOB_NEEDS_SELF` |
| `needs` creates a cycle | Validation error `JOB_NEEDS_CYCLE` |
| Dependency job fails or is canceled | Dependent pending jobs become `SKIPPED` |
| A job step returns suspend | Job becomes `BLOCKED`; step log records `WAITING_INPUT` |
| A blocked job is resumed | Re-enter suspended step handler idempotently; do not recreate runtime until a `JOB_RUNTIME` step needs it |
| Failed job is retried | Increment `attempt`, create fresh runtime/workspace, leave successful upstream jobs untouched |
| Run is canceled | Pending/blocked/running jobs become `CANCELED`; runtime is destroyed and platform cancellation hooks run |
| Job contains only `PLATFORM` steps | No Docker runtime is created |
| Two ready jobs run in parallel | Each job gets its own Docker runtime, and both mount the same run workspace; job-specific temp/report/artifact paths must be namespaced under `jobs/{jobId}/...` |
| YAML value contains `${VAR}` | Shared resolver expands it from run/job/step variables |
| Step output contains credential material | Reject or mask before writing `resultJson.outputs`, logs, or context |
| `Command` without `with.run` | Validation error on `with.run` |
| `APPROVAL` without `with.processDefinitionKey` | Validation error on `with.processDefinitionKey` |
| `K8sDeploy` without `with.deployMode/manifestYaml/containerName/image` | Validation error on the missing field |
| `K8sDeploy` has `with.deployMode` other than `RAW_MANIFEST` | Validation error on `with.deployMode` |
| `K8sImageUpgrade` without `with.workloadKind/workloadName/containerName/image` | Validation error on the missing field |
| `K8sImageUpgrade` has `with.workloadKind` other than `Deployment` | Validation error on `with.workloadKind` |
| `K8sImageUpgrade` targets a missing Deployment or container | Step returns `FAIL`; do not create a Deployment |
| Non-registered step before handler exists | Validation or execution error indicating the step is not supported yet |
| `CodeMerge` without `with.baseBranch` or `with.targetBranch` | Validation error on the missing field |
| `CodeMerge` has `with.branches` | Merge those branches in array order; this takes precedence over `branchesFromSubmit` |
| `CodeMerge` omits `with.branches` and has `branchesFromSubmit=true` | Merge the submit-time change branches captured on the pipeline run |
| `CodeMerge` succeeds before a downstream `JOB_RUNTIME` job | Downstream source checkout uses `mergedBranch/mergedCommitSha` from step outputs |
| `CodeMerge` conflicts | Step log becomes `WAITING_INPUT`; owning job becomes `BLOCKED`; conflict APIs continue to resolve and resume the suspended job |
| `ChangePublishFinalize` runs with `PipelineRun.changeSnapshotJson` | Finalize the snapshot change ids in order; if the snapshot is empty, fall back to `PipelineRun.changeId` |
| `ChangePublishFinalize` sees active changes | Merge `PipelineRun.branchName` into `ChangeDO.sourceBaseBranchName` or `ApplicationDO.defaultBranchName`, then mark all participating changes released and delete their remote branches plus the deploy branch |
| `ChangePublishFinalize` sees only released changes | Skip merge/status mutation and retry remote cleanup for participating change branches and the deploy branch idempotently |
| `ChangePublishFinalize` hits merge conflict or Git push failure | Step returns `FAIL`; do not silently continue |
| `PrivateRegistryDockerBuild` has `certificate.type=serviceConnection` | Validation error on `with.certificate.type`; current version supports only `usernamePassword` |
| `PrivateRegistryDockerBuild` succeeds | Step outputs include non-sensitive `artifact` and `image`; `certificate.password` is absent from logs and result JSON |

## Scenario: Pipeline Run Live Command Logs

### 1. Scope / Trigger

- Trigger: adding or changing Docker `Command` step output capture, file-backed log storage, object-storage archive behavior, or frontend live-log APIs.
- Scope: `CommandStepHandler`, `DockerPipelineCommandExecutor`, `LogSink`, `PipelineRunLogLineService`, `PipelineLogStorageService`, `PipelineLogBroadcaster`, `PipelineRunController`, legacy `PipelineRunLogLineDO/Mapper`, `dev_pipeline_run_log`, `dev_pipeline_run_log_line`, and focused tests.

### 2. Signatures

- Query API:
  - `GET /devops/pipeline-run/{runId}/log-lines?stepId={stepId}&afterId={id}&limit={limit}`
  - `stepId` is required for new file-backed log viewing.
  - Returns `CommonResult<List<PipelineRunLogLineRespVO>>`.
- SSE API:
  - `GET /devops/pipeline-run/{runId}/log-lines/stream?stepId={stepId}&afterId={id}`
  - `stepId` is required.
  - Produces `text/event-stream`.
  - `log-line` events use the same opaque cursor as the response `id`.
  - terminal `complete` events return `PipelineRunLogStreamCompleteRespVO` with `pipelineRunId`, `runLogId`, `stageId`, `jobId`, `stepId`, `status`, `terminal`, `logFileUrl`, and `logArchiveStatus`.
- Raw log API:
  - `GET /devops/pipeline-run/{runId}/logs/{runLogId}/raw`
  - Streams the local step raw log when present.
- File storage:
  - `{workspace}/.devops/pipeline-runs/run-{runId}/logs/{safeStageId}/{safeJobId}/{safeStepId}.log`
  - `dev_pipeline_run_log.log_file_path` stores the workspace-relative path.
  - One step owns one raw log file. Do not introduce run-level aggregate log files for the frontend contract.
  - The step raw log file is the primary source for `/log-lines`, SSE replay, raw download, and bounded object-storage archive.
- DB metadata:
  - `dev_pipeline_run_log.log_file_path`
  - `dev_pipeline_run_log.log_file_url`
  - `dev_pipeline_run_log.log_size_bytes`
  - `dev_pipeline_run_log.log_line_count`
  - `dev_pipeline_run_log.log_archive_status`
  - `dev_pipeline_run_log.log_archived_at`
  - `dev_pipeline_run_log_line` remains for legacy fallback and should not be the new append path.
- Runtime callback:
  - `LogSink.accept(String stream, String line)` where `stream` is normalized to `stdout` or `stderr`.

### 3. Contracts

- `afterId` is the only reconnect cursor for step-level streams. Do not expose or rely on `afterLineNo` for reconnect.
- `id`/`afterId` is opaque. File-backed runs use a cursor derived from `runLogId` and the byte offset immediately after the line in the step log file; legacy DB fallback may still use the old row id.
- `lineNo` is display metadata scoped to one `run_log_id`; it is not globally unique across a run.
- `stepId` is required. If omitted, `/log-lines` and `/log-lines/stream` throw `PIPELINE_RUN_LOG_STEP_REQUIRED`.
- Read APIs must keep `@PreAuthorize("@ss.hasPermission('devops:pipeline:query')")`.
- The existing structured API `GET /devops/pipeline-run/{runId}/logs` remains the source for stage/job/step status and metadata.
- Execution-capable pipeline step handlers may keep a small `resultJson.logLines` summary for compatibility, but full frontend log viewing should use `/log-lines` and SSE backed by file storage.
- Line persistence must be bounded by configured file byte limits. When the cap is reached, set parent `dev_pipeline_run_log.log_truncated=true`.
- Step completion finalizes raw log metadata. Step-scoped SSE completion may archive the bounded raw log through `FileApi` so it can return `logFileUrl`; the run terminal path must also call archive as a compensation step before workspace cleanup. Archive failure must record failure metadata and must not fail an otherwise successful pipeline step or run.
- Docker/container command output should flow through docker-java stdout/stderr frames into backend `LogSink`; backend code writes the workspace step log file. Do not make containers write directly to platform log files or mount an extra log root just for pipeline logs.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| `pipelineRunId` does not exist | Throw `PIPELINE_RUN_NOT_EXISTS` |
| `stepId` is omitted | Throw `PIPELINE_RUN_LOG_STEP_REQUIRED` |
| `afterId` is omitted | Start from the beginning |
| `limit` is omitted or invalid | Use service default; cap requests to the service maximum |
| Command output exceeds file cap | Stop appending new lines and mark the parent run log truncated |
| File-backed run is missing but legacy DB rows exist | Read from `dev_pipeline_run_log_line` fallback |
| Step raw log archive upload fails | Mark archive status failed and keep the pipeline step/run result unchanged |
| Step-scoped SSE sees the step terminal and no more buffered lines | Archive the step log if needed, send a `complete` event containing `logFileUrl` / `logArchiveStatus`, and close the emitter |

### 5. Good / Base / Bad Cases

- Good: Docker stdout/stderr frames flow through `LogSink.accept(stream, line)`, backend appends raw step text to the workspace step log file, publishes `log-line` SSE events, and frontend reconnects with `afterId`.
- Base: a completed run still exposes its persisted file-backed command lines through the same query API.
- Base: an older run without a workspace step log file still exposes old `dev_pipeline_run_log_line` rows through the same query API when `stepId` is provided.
- Bad: using `lineNo` as a reconnect cursor, because it is display metadata scoped to one step log and can reset across steps or retries.
- Bad: storing unbounded command output in `resultJson` or repeatedly updating one large JSON blob while a command is running.
- Bad: writing new high-volume build output to `dev_pipeline_run_log_line` as the primary path.
- Bad: supporting frontend run-level log aggregation by concatenating multiple step files in the stream API; require callers to choose one `stepId`.
- Bad: using `with.logMode` or container-owned log-file writes for new `Command` behavior.

### 6. Tests Required

- Service tests for workspace file append, truncation, cursor query, missing run, required `stepId`, legacy DB fallback, archive success/failure, and SSE completion payload.
- Handler tests proving execution-capable step handlers publish live output while still writing compatibility result summary.
- Executor tests updated when `LogSink` signature changes.
- Focused command:
  `mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dtest='PipelineLogStorageServiceImplTest,PipelineRunLogLineServiceImplTest,CommandStepHandlerTest,LocalBuildExecutorTest,*Pipeline*Test' -Dsurefire.failIfNoSpecifiedTests=false test`

### 7. Wrong vs Correct

#### Wrong

```java
// New frontend log viewing must not request all steps in one stream.
pipelineLogStorageService.readLines(runId, null, afterId, limit);
```

#### Correct

```java
// Use the opaque backend cursor for one selected step.
pipelineLogStorageService.readLines(runId, stepId, afterId, limit);
```

## Scenario: Pipeline Step Logs and Actions

### 1. Scope / Trigger

- Trigger: adding or changing platform-step process logs, `StepResult` result metadata, `dev_pipeline_run_log.result_json`, or frontend node action rendering.
- Scope: `PipelineStepHandler`, `StepResult`, `PipelineStepLogHelper`, `PipelineStepActionHelper`, platform step handlers/services such as `CodeMergeService` and `PipelineApprovalServiceImpl`, `DockerImageExportObjectStorageStepHandler`, `PipelineRunLogRespVO.result`, and focused handler/helper tests.

### 2. Signatures

- Structured run log API remains:
  - `GET /devops/pipeline-run/{runId}/logs`
  - Returns `PipelineRunLogRespVO.result` parsed from `dev_pipeline_run_log.result_json`.
- Step actions live under:
  - `result.actions: PipelineStepAction[]`
- Action fields:
  - `key`: stable action code, such as `CODE_MERGE_RESOLVE_CONFLICT`, `APPROVAL_OPEN_PROCESS`, `IMAGE_ARCHIVE_DOWNLOAD`
  - `label`: display text
  - `type`: `ROUTE`, `DOWNLOAD`, `API`, or `EXTERNAL_LINK`
  - `style`: `PRIMARY`, `DEFAULT`, `DANGER`, or `LINK`
  - `permission`: UI permission hint; backend APIs must still authorize independently
  - `enabled`: boolean availability
  - `disabledReason`: optional disabled reason
  - `target`: route/download/API target object
  - `payload`: non-sensitive display metadata
  - `order`: optional sort key
- `StepResult` may carry `actions` in addition to `outputs`; the execution engine merges both into `result_json`.

### 3. Contracts

- `dev_pipeline_run_log.result_json.actions` is the backend-owned node action contract. Frontend must render from this generic list and must not infer built-in buttons from `stepType`.
- Release `current-run` is a polling read model. It must not pass through full `result_json`; strip `logLines`, `outputs`, raw execution context, workspace/runtime/env details, and other bulky or sensitive fields. Convert backend-owned `result.actions` into node/step top-level `actions`, and aggregate step actions onto the job card when the job is the frontend render unit.
- Use `PipelineStepActionHelper` to upsert/remove actions; do not hand-concatenate JSON in handlers.
- Action upsert replaces actions with the same `key` and preserves unrelated existing `result_json` fields such as `outputs`, `storagePath`, or `logLines`.
- Terminal states must remove actions that are no longer valid, unless the action is intentionally a read-only history action.
- Platform steps should write low-frequency human-readable step log lines through `PipelineStepLogHelper.appendPlatformLine(...)`; command/build output remains the high-volume command log path.
- Action payloads and targets must not include passwords, tokens, access keys, access secrets, or credential material.
- `DOWNLOAD` actions may contain a storage URL/path only when it is safe for the frontend to consume. If a secure proxy or presigned URL is needed, add a controlled backend endpoint before exposing it as a browser-download URL.
- When CodeMerge hits a conflict and `pushOnSuccess` is not explicitly false, push the pre-conflict deploy branch HEAD to remote before writing `WAITING_INPUT` so local IDE users can fetch it. `CODE_MERGE_RESOLVE_CONFLICT` action payload should include `deployBranch`, `deployBranchPushed`, `currentBranchName`, and `currentCommitSha`; the local reproduction flow is checkout `deployBranch`, then merge `currentCommitSha`. Do not make the frontend infer branch names from display summaries.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| Old run has no `result.actions` | Frontend treats actions as an empty list |
| Unknown action `type` | Frontend hides or ignores the action |
| Unknown action `key` with known type | Frontend may render generically using `type/label/target` |
| Action is no longer valid after terminal state | Backend removes it from `result.actions` |
| Action would require a secret in payload or target | Do not create the action; use backend-owned lookup/proxy instead |
| Platform step has only summary text | Add bounded platform log lines for key state transitions |

### 5. Good / Base / Bad Cases

- Good: `CodeMerge` writes `CODE_MERGE_RESOLVE_CONFLICT` only while the step is `WAITING_INPUT` with conflicts, and removes it after continue succeeds or fails.
- Good: `APPROVAL` writes `APPROVAL_OPEN_PROCESS` after creating the BPM process instance, targets `BpmProcessInstanceDetail` with `query.id=<processInstanceId>`, then removes it on approve/reject/cancel.
- Good: image export writes `IMAGE_ARCHIVE_DOWNLOAD` with image name, storage path, and file name, without storage credentials.
- Base: old completed logs without actions still render normally.
- Bad: frontend hard-codes `if stepType == APPROVAL` to show "去审批".
- Bad: action payload contains `password`, `token`, `accessKeySecret`, or registry credentials.

### 6. Tests Required

- Helper tests for upsert, same-key replacement, removal, and preserving existing result fields.
- Handler/service tests proving each built-in action appears only in the expected status.
- Tests or assertions that action JSON for image export does not contain registry passwords or object-storage secrets.
- Platform step log tests should verify `appendPlatformLine(...)` is called for key transitions where practical.

### 7. Wrong vs Correct

#### Wrong

```java
Map<String, Object> result = JsonUtils.parseMap(log.getResultJson());
result.put("actions", List.of(Map.of("key", "APPROVAL_OPEN_PROCESS")));
log.setResultJson(JsonUtils.toJsonString(result));
```

This easily overwrites existing fields and scatters action schema knowledge.

#### Correct

```java
pipelineStepActionHelper.upsertActions(log, List.of(action));
pipelineStepActionHelper.removeActions(log, PipelineStepActionKeyConstants.APPROVAL_OPEN_PROCESS);
```

## Scenario: Git Command Output and CodeMerge Conflict Detail

### 1. Scope / Trigger

- Trigger: changing `GitCommandExecutor`, Git workspace conflict reads, or `GET /devops/pipeline-run/{runId}/code-merge/conflict-detail`.
- Scope: complete process/conflict output, timeout/interruption cleanup, persisted conflict/workspace validation, and API error mapping.

### 2. Signatures

- Git command execution returns the complete captured output:
  `GitCommandResult execute(Path workDir, List<String> command, boolean checkExitCode)`.
- Conflict scene validation is:
  `boolean isConflictWorkspaceValid(String workspaceKey, GitConflictDescriptor conflict)`.
- Conflict detail response assembly is:
  `CodeMergeConflictDetailRespVO convert(CodeMergeConflictContext conflict, GitConflictContent content, String resultContent, List<String> supportedActions)`.
- Conflict detail API remains:
  `GET /devops/pipeline-run/{runId}/code-merge/conflict-detail?filePath=<path>`.

### 3. Contracts

- Use Hutool 5 `RuntimeUtil.exec(String...)` plus `RuntimeUtil.getResult(process, UTF_8)` so merged stdout/stderr is consumed from process start instead of after `waitFor`. Generic Git command output is intentionally returned in full with no application-level byte cap; callers must account for the accepted heap-risk trade-off.
- Preserve a 300-second Future/read timeout. Timeout/interruption must terminate the process and descendants, converge the reader within a bound, and restore the caller's interrupt flag.
- When `workDir` is present, normalize the command to `git -C <workDir> ...`. Do not use `RuntimeUtil.exec(envp, dir, ...)` for this path because that overload uses `Runtime.exec` without merged stderr and can block when only stdout is consumed.
- Conflict detail is valid only while the CodeMerge step log is `WAITING_INPUT`.
- A persisted conflict is readable only when the workspace and `.git` exist and its base/ours/theirs blob ids exactly match stages 1/2/3 from `git ls-files -u -- <filePath>`. This prevents a later run or a new conflict at the same path from being mistaken for the original scene.
- Read Git blobs with `git cat-file blob <sha>` and working files as complete UTF-8 content. Generic command output and online conflict content intentionally have no application-level size cap; `contentTooLarge` remains response-compatible and is `false`. The accepted trade-off is potentially large service heap, HTTP response, and browser-editor pressure.
- Workspace-expired errors and later Git execution errors use module business error codes. Diagnostics must be bounded and mask credential-bearing URLs and workspace absolute paths.
- `PipelineExecutionServiceImpl` computes the effective `resultContent` and `supportedActions`; `PipelineConvert` maps conflict metadata plus Git content into `CodeMergeConflictDetailRespVO` in one call. Do not restore per-field response setters in the service.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| Child writes more than the OS pipe capacity | RuntimeUtil consumes while running; command completes without pipe deadlock and output is returned in full |
| CodeMerge log is not `WAITING_INPUT` | Return `PIPELINE_RUN_LOG_STATE_INVALID` before workspace access |
| Workspace/`.git` is missing, unmerged entries are gone, or stage blob ids changed | Return `PIPELINE_CODE_MERGE_WORKSPACE_NOT_EXISTS` |
| A blob or working file exceeds the former 512 KiB limit | Return complete content and `contentTooLarge=false` |
| Valid scene later hits another Git failure | Return `PIPELINE_CODE_MERGE_GIT_EXEC_FAIL` with bounded, masked diagnostics |
| Caller thread is interrupted | Terminate process tree, converge reader, restore interrupt status, and throw `GitCommandException` |

### 5. Good / Base / Bad Cases

- Good: a 1 MiB-producing child is consumed while running and returned in full.
- Good: persisted stage blob ids are compared with the current unmerged index before any conflict content is returned.
- Good: conflict metadata, complete Git content, effective result, and supported actions are assembled by one `PipelineConvert` call.
- Base: a small UTF-8 conflict returns complete base/ours/theirs/working content.
- Bad: call `waitFor` before reading stdout/stderr; a full pipe can deadlock parent and child.
- Bad: accept any non-empty `git ls-files -u` result for the same path without comparing stage blob ids.
- Bad: use the work-directory `RuntimeUtil.exec(envp, dir, ...)` overload and read only stdout without also draining stderr.

### 6. Tests Required

- Real-process test producing at least 1 MiB, asserting completion within a short test timeout and complete returned output.
- UTF-8 completeness test; non-zero exit must preserve complete captured diagnostics.
- Interruption test asserting the caller interrupt flag and child-process termination.
- Blob and working-file tests for content above the former 512 KiB limit, asserting complete content and `contentTooLarge=false`.
- Workspace tests for missing `.git`, missing unmerged entries, missing blobs, and a same-path conflict whose current stage blob ids differ from the persisted descriptor.
- Service tests for valid detail, non-`WAITING_INPUT`, workspace expiry, and credential/workspace masking on Git failure.
- Service tests must assert every field produced by the multi-source `PipelineConvert`, including saved-resolution override and resolved-state actions.

### 7. Wrong vs Correct

#### Wrong

```java
boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
String output = new String(process.getInputStream().readAllBytes(), UTF_8);
```

The child can block while filling the pipe and never reach exit.

#### Correct

```java
Process process = RuntimeUtil.exec(normalizeGitCommand(workDir, command));
Future<String> output = readAsync(() -> RuntimeUtil.getResult(process, UTF_8));
String completeOutput = output.get(timeout, TimeUnit.SECONDS);
terminateProcessTreeIfNeeded(process, output);
```

The implementation may name the helpers differently, but RuntimeUtil-based early consumption, full generic output, timeout, and process-tree cleanup are mandatory.

## Scenario: Pipeline Approval BPM Deploy Detail Variables

### 1. Scope / Trigger

- Trigger: changing the `APPROVAL` step handler, approval service, BPM process-instance create variables, or release approval display data.
- Scope: `PipelineApprovalServiceImpl`, approval helper services under `service/pipeline/approval`, BPM `BpmProcessInstanceCreateReqDTO.variables`, and focused approval tests.

### 2. Signatures

- Pipeline YAML remains unchanged:
  - `steps.<stepId>.step: APPROVAL`
  - `steps.<stepId>.with.processDefinitionKey`
- BPM create request:
  - `BpmProcessInstanceCreateReqDTO.processDefinitionKey`
  - `BpmProcessInstanceCreateReqDTO.businessKey`
  - `BpmProcessInstanceCreateReqDTO.variables.deploy_html: String`

### 3. Contracts

- `variables.deploy_html` is the rich-text payload for the BPM form field whose id is `deploy_html`.
- `deploy_html` should be generated only when first creating the BPM process instance. Existing waiting/success/terminal approval logs must keep the current idempotent behavior and must not recreate the BPM instance just to refresh detail HTML.
- The HTML should include application, environment, pipeline run, deploy branch, and change list details.
- Change rows should follow `PipelineRun.changeSnapshotJson` order and fall back to `PipelineRun.changeId` for older single-change runs.
- Change rows should include tester and code reviewer display values. Prefer admin user nickname; fall back to user id; use `-` when blank.
- The first version does not include DevOps page links in the HTML because frontend routes, gateway base URL, and permission behavior are not part of the approval variable contract.
- BPM API, BPM server, BPM form definition, and pipeline YAML config must not be changed for this display-only contract.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| Approval step first creates a BPM instance | Include non-blank `variables.deploy_html` |
| Existing approval log is `WAITING_INPUT` | Return suspend without creating a new BPM instance or refreshing `deploy_html` |
| Existing approval log is success | Return success without creating a new BPM instance |
| `changeSnapshotJson` contains multiple changes | Render all snapshot changes in snapshot order |
| `changeSnapshotJson` is empty and `changeId` exists | Render the single `changeId` row |
| No related changes exist | Render a clear no-related-change message and still create the BPM instance |
| Application/environment/change record is missing | Render available ids or `-`; do not block approval creation |
| Admin user lookup fails | Log a warning, fall back to user ids, and continue approval creation |
| Database text contains HTML/script-like content | HTML-escape dynamic values before writing `deploy_html` |

### 5. Good / Base / Bad Cases

- Good: approval variables keep the existing machine-readable ids and add `deploy_html` for BPM rich-text display.
- Good: HTML assembly is isolated from approval lifecycle code so idempotency and BPM callback handling stay focused.
- Base: upload-image or empty-change approval still starts and shows no related changes.
- Bad: changing pipeline YAML to require a `deployHtmlField` option for this default behavior.
- Bad: adding DevOps page links without a product decision and a stable frontend/base-url contract.
- Bad: passing unescaped change titles, branch names, commit messages, or environment names into rich-text HTML.

### 6. Tests Required

- Approval service test that captures `BpmProcessInstanceCreateReqDTO` and asserts `variables.deploy_html` is present.
- Builder/helper test that verifies multi-change snapshot order.
- Builder/helper test that verifies tester and code reviewer nickname display plus id fallback.
- Builder/helper test that verifies empty-change approval HTML.
- Builder/helper test that verifies HTML escaping for application, environment, change title, branch, and commit-message data.
- Existing approval lifecycle tests for waiting/success/reject/cancel should continue passing.

### 7. Wrong vs Correct

#### Wrong

```java
variables.put("deploy_html", "<p>" + change.getTitle() + "</p>");
```

#### Correct

```java
variables.put("deploy_html", deployDetailBuilder.buildDeployHtml(run));
```

## Scenario: Pipeline Approval BPM Post-Notification Callback

### 1. Scope / Trigger

- Trigger: changing the DevOps approval callback, BPM process post-notification, or the BPM process-instance RPC status contract.
- Scope: `PipelineRunController`, `PipelineApprovalService`, `BpmProcessInstanceApi`, `BpmProcessInstanceApiImpl`, BPM historic-process lookup, and focused BPM/DevOps approval tests.

### 2. Signatures

- BPM process post-notification endpoint: `POST /admin-api/devops/pipeline-run/bpm/approval-status`.
  - Form parameter: `processInstanceId: String`, non-blank.
  - Tenant header: BPM's standard `tenant-id` header.
- BPM internal status query: `GET /rpc-api/bpm/process-instance/status?id={processInstanceId}`.
  - Response DTO: `id`, `processDefinitionKey`, `businessKey`, `status`.

### 3. Contracts

- The HTTP callback accepts only `processInstanceId`; it must not trust a caller-supplied BPM status or business key.
- DevOps queries BPM under the propagated tenant, then processes the result only when `status == APPROVE` and `businessKey` begins with `devops:pipeline-approval:`.
- An approved result is converted to the existing `BpmProcessInstanceStatusEvent` handling path so its idempotent node/job/run updates and engine re-entry remain centralized.
- Missing instances, non-approved status, and non-DevOps business keys return `CommonResult.success(false)` without updating a pipeline or resuming the engine.
- BPM historic-instance lookup includes `processInstanceTenantId(FlowableUtils.getTenantId())` and process variables because `PROCESS_STATUS` is stored in variables.
- BPM sends approval-only process post-notifications with no response-variable mapping after transaction commit. Notifications with response mappings remain synchronous because their mapped values must be written before the process ends.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| `processInstanceId` blank | Request validation failure; do not call BPM |
| BPM instance not found | Return `success(false)`; do not touch pipeline state |
| BPM status is not `APPROVE` | Return `success(false)`; do not resume engine |
| BPM business key is not DevOps approval | Return `success(false)`; do not touch pipeline state |
| Valid approved DevOps instance | Mark approval step success and re-enter engine through existing approval service |
| Callback is delivered again | Existing terminal-log guard prevents a second engine advance |

### 5. Good / Base / Bad Cases

- Good: a configured BPM post-notification sends only `processInstanceId`; DevOps fetches the authoritative state before advancing.
- Good: no-response-mapping notification is dispatched after commit, so the remote status lookup observes the final process state.
- Base: monolithic deployment may receive both local event and HTTP notification; the terminal approval log makes the second path a no-op.
- Bad: expose `status` and `businessKey` as trusted callback form parameters.
- Bad: keep `@TenantIgnore` on the DevOps callback, which discards the tenant needed by the BPM RPC lookup.

### 6. Tests Required

- BPM API test: assert status DTO maps historic instance id, definition key, business key, and `PROCESS_STATUS`; missing instance returns null data.
- DevOps approval-service test: assert an approved queried instance updates the existing approval lifecycle; non-approved, missing, and non-DevOps instances are no-ops.
- Run BPM and DevOps focused module tests and compile the DevOps module with its reactor dependencies.

### 7. Wrong vs Correct

#### Wrong

```java
handleProcessInstanceStatus(request.getStatus(), request.getBusinessKey());
```

#### Correct

```java
BpmProcessInstanceStatusRespDTO instance = bpmProcessInstanceApi
        .getProcessInstanceStatus(processInstanceId).getCheckedData();
if (instance != null && APPROVE.equals(instance.getStatus())) {
    handleProcessInstanceStatus(toTrustedStatusEvent(instance));
}
```

## Scenario: Pipeline Definition Version Rollback

### 1. Scope / Trigger

- Trigger: changing pipeline definition versioning, publishing, rollback, or version-list contracts.
- Scope: `PipelineDefinitionVersionDO`, `PipelineDefinitionServiceImpl`, `PipelineDefinitionVersionMapper`, pipeline admin VOs/controllers, `dev_pipeline_definition_version`, `PIPELINE_YAML_SPEC.md`, and focused pipeline-definition service tests.

### 2. Signatures

- API:
  - `POST /devops/pipeline/rollback`
  - Request: `definitionId`, `targetVersionId`, optional `versionName`, required `rollbackReason`.
  - Response: new published version id.
- DB fields on `dev_pipeline_definition_version`:
  - `rollback_from_version_id`
  - `rollback_from_version_no`
  - `based_on_current_version_id`
  - `based_on_current_version_no`
  - `rollback_reason`

### 3. Contracts

- Rollback must create a new `PUBLISHED` version; it must not mutate the target historical version or rewrite the current version content.
- The new rollback version copies executable content from the target version: `diagramJson`, `specJson`, `nodeSchemaVersion`, and `validationResultJson`.
- The new version number continues the published-version sequence through the same `nextPublishedVersionNo` rule used by normal publish.
- The new version immediately becomes current by updating `PipelineDefinitionDO.publishedVersionId`.
- `rollbackFrom*` records the historical version being copied. `basedOnCurrent*` records the current published version at rollback time.
- Pipeline runs continue to bind the concrete `definition_version_id`; do not update existing run rows during rollback.
- Jenkins compatibility fields must not be reintroduced into definition-version DOs or admin VOs.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| `definitionId` does not exist | Throw `PIPELINE_DEFINITION_NOT_EXISTS` |
| `targetVersionId` does not exist | Throw `PIPELINE_VERSION_NOT_EXISTS` |
| Target version belongs to another definition | Throw `PIPELINE_VERSION_NOT_IN_DEFINITION` |
| Target version is not `PUBLISHED` | Throw `PIPELINE_ROLLBACK_TARGET_INVALID` |
| Target version is already the current published version | Throw `PIPELINE_ROLLBACK_TARGET_INVALID` |

### 5. Good / Base / Bad Cases

- Good: current `v10` rolls back to historical `v6` by inserting `v11`, setting `rollback_from=v6`, `based_on_current=v10`, and activating `v11`.
- Base: no current published version exists but a published target version is supplied; insert the next published version and leave `basedOnCurrent*` null.
- Bad: updating `v10` content to match `v6`, because historical run records would no longer represent the definition that actually ran.

### 6. Tests Required

- Service test for successful rollback: version number increments, copied content matches target, rollback metadata is set, and current published version changes to the new id.
- Service tests for wrong-definition target and current-version target.
- Compile/test command:
  `mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dtest='PipelineDefinitionServiceImplTest,PipelineSpecValidationServiceImplTest' -Dsurefire.failIfNoSpecifiedTests=false test`

### 7. Wrong vs Correct

#### Wrong

```java
currentVersion.setSpecJson(targetVersion.getSpecJson());
pipelineDefinitionVersionMapper.updateById(currentVersion);
```

#### Correct

```java
PipelineDefinitionVersionDO rollbackVersion = new PipelineDefinitionVersionDO();
rollbackVersion.setVersionNo(nextPublishedVersionNo(definition.getId()));
rollbackVersion.setSpecJson(targetVersion.getSpecJson());
rollbackVersion.setRollbackFromVersionId(targetVersion.getId());
pipelineDefinitionVersionMapper.insert(rollbackVersion);
definition.setPublishedVersionId(rollbackVersion.getId());
```

### 5. Tests Required

- Validation tests for JSON and YAML parsing, successful flattening, duplicate job ids, duplicate step ids, missing/self/cyclic `needs`, and required `with` params.
- Execution-engine tests must verify job DAG scheduling order and parallel-ready job discovery. Existing `sortExecutableSteps` tests may remain only as internal YAML flattening coverage.
- Runtime tests must verify PLATFORM-only jobs do not create Docker runtime and JOB_RUNTIME steps lazy-create it.
- Workspace tests must verify different runs use isolated run workspaces, all jobs in one run share the same run workspace, and dependency cache directories are mounted from derived definition-scoped paths.
- Workspace reuse tests must verify different runs of the same app-env-definition map to the same workspace, and that `resetRunWorkspace` preserves `.git` while clearing other top-level entries.
- Git workspace tests must assert the `--filter=blob:none` fetch command shape, and must not assert any `--depth` value for CodeMerge fetches.
- Variable resolver tests must verify `${VAR}` expansion and credential masking.
- Retry/resume/cancel tests must verify job attempt increments, blocked resume is idempotent, cancellation destroys runtime, and run aggregate status follows job states.
- K8s deployment step tests must verify deployment order creation, `StepResult` mapping, no service-level `PipelineRun` terminal mutation, cancellation hook dispatch, and `K8sImageUpgrade` missing-workload/container failure without create-or-replace behavior.
- Artifact/report tests must verify standardized `resultJson.artifacts` and `resultJson.reports` metadata.
- Release-page tests for changed APIs should assert stage/job/step read models instead of `nodes/edges`; fixture specs must be built with `stages.jobs.steps`.
- Compile/test command:
  `mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dtest='PipelineSpecValidationServiceImplTest,PipelineExecutionEngineTest,PipelineExecutionServiceImplTest,ApplicationServiceImplTest' -Dsurefire.failIfNoSpecifiedTests=false test`

## Scenario: Pipeline Release Submit Trigger

### 1. Scope / Trigger

- Trigger: adding or changing pipeline run execution, release-submit trigger behavior, source workspace checkout, or run log persistence.
- Scope: `ApplicationService.submitApplicationReleaseBranch`, pipeline run controllers, execution services, source workspace integration, run/log mappers, `dev_pipeline_run` / `dev_pipeline_run_log` SQL, enums, error codes, and focused tests.

### 2. Signatures

- Trigger API:
  - `POST /devops/application/release/submit-branch`
  - Every release submission creates one `dev_pipeline_run` and starts the published pipeline YAML, including an empty `changeIds[]` baseline release.
- Release-page polling API:
  - `GET /devops/application/release/current-run?applicationEnvId={id}`
  - Returns the current or latest run for the application environment, plus lightweight node execution state for card rendering.
  - Also returns `mountedBranches`, using the same item shape and ordering as `GET /devops/application/release/env-detail`'s `mountedBranches`.
- Run APIs:
  - `GET /devops/pipeline-run/{runId}/logs`
  - `POST /devops/pipeline-run/{runId}/cancel`
- DB:
  - `dev_pipeline_run` remains the run master record.
  - `dev_pipeline_run_log` stores generic `NODE` / `STEP` / `EVENT` logs with `context_json` and `result_json`.

### 3. Contracts

- `submit-branch` does not perform a built-in pre-merge step. The published pipeline YAML is the execution source of truth.
- Source checkout for YAML `sources` happens once before creating the first job runtime container and uses the run-level workspace shared by all jobs in that pipeline run.
- Deploy branch selection is decided during `submit-branch` and stored in `dev_pipeline_run.branch_name`.
- Deploy branch naming for new branches uses `release/{envKey}/{yyyyMMddHHmmss}`. The application dimension is supplied by the repository/application environment; do not include `appKey` in new release branch names.
- When `submit-branch` only adds or refreshes changes and removes no currently mounted change, reuse the latest `release/` branch recorded by the same application environment when it exists, regardless of whether the later Jenkins/build stage succeeded.
- When `submit-branch` removes any currently mounted change from the target set, create a new timestamp release branch and rebuild from the application default branch.
- When `submit-branch` creates a new release branch (because changes were removed, or because the recorded base commit is outdated against the application default branch), it must also delete the application environment's previously recorded `release/` deploy branches from the remote. The old branch is no longer referenced by any later step, so leaving it behind only accumulates orphan branches.
- Stale deploy branch cleanup must resolve its targets from local `dev_pipeline_run` rows filtered by `application_env_id` plus the `release/` prefix. Never enumerate or delete remote branches by name prefix: a remote scan cannot prove ownership and would reach branches outside this application environment.
- Stale deploy branch cleanup is best-effort: deduplicate repeated branch names, exclude the branch just created, and log a warning per failure instead of failing `submit-branch`. Failures self-heal because the next rebuild re-queries the full historical set.
- Reusing an existing `release/` branch must not delete any branch and must not query historical runs for cleanup.
- When `submit-branch` has no target changes, create a run against the application default branch source workspace; do not create or push a merged branch as a precondition.
- Empty-change release runs may have null compatibility anchor fields `dev_pipeline_run.change_id` and `dev_pipeline_run.change_env_id`; non-empty runs still set them from the first submitted change.
- Source workspace preparation clones the application-linked GitLab repository at the application default branch when `submit-branch` triggers a run. The clone is reused by later jobs in the same run. Do not expose raw tokens, tokenized clone URLs, or workspace absolute paths in API responses or error messages.
- `submit-branch` must stop the currently active run (`QUEUED` / `RUNNING` / `WAITING_INPUT`) of the same application environment before creating the new run, then deploy with the new run. Cancellation is committed in its own transaction, so a later failure of the submit transaction cannot resurrect the stopped run. When the stop fails, or the run is still active after the stop attempt, throw `PIPELINE_RUN_STOP_FAILED` and do not create a new run.
- Stop-before-submit applies only to `submit-branch`. The upload-image trigger keeps rejecting active runs with `PIPELINE_RUN_ACTIVE_EXISTS`.
- The release page should poll `current-run` for card-level job/step status. It must use `logs` only when opening detail dialogs.
- `current-run` must not return raw `context_json`; return only sanitized summaries and result output so workspace keys and runtime metadata are not exposed during polling.
- `current-run` is a polling read model and should use declarative Spring Cache keyed by `applicationEnvId`, with a short TTL as a stale-data safety net.
- Current-run cache invalidation must cover release submission, explicit change-env mount/unmount, pipeline publication, public pipeline execution mutation APIs such as start and cancel, and change mutations that affect mounted branch cards.
- Prefer declarative `@CacheEvict` when the method input directly carries `applicationEnvId`; use programmatic cache eviction by `change_env.application_env_id` when a change-level mutation can affect multiple environments.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| Same app environment has an active run on submit | Stop it first; on stop failure throw `PIPELINE_RUN_STOP_FAILED` before creating another run |
| Stale deploy branch cleanup fails on rebuild | Log a warning with `applicationEnvId` + branch name and continue; `submit-branch` still succeeds and the new run is still created |
| Run id does not exist | Throw `PIPELINE_RUN_NOT_EXISTS` |
| Repository source is not supported for checkout | Mark run/job `FAILED` with sanitized detail |
| Git command fails unexpectedly | Mark run/job/log `FAILED` and store sanitized detail |

### 5. Good / Base / Bad Cases

- Good: `submit-branch` commits the release target set, creates one run, then after transaction commit dispatches published YAML execution to an async Spring bean.
- Good: source checkout happens once per run workspace before the first `JOB_RUNTIME` step creates a Docker container.
- Good: YAML with no `sources` and an application-linked run checks out the application repository; ad-hoc runs without application context still run `Command` steps in an empty workspace.
- Bad: running source checkout in the request thread or inside `TransactionSynchronization.afterCommit`.
- Bad: doing a hidden pre-merge before the YAML execution engine starts.
- Bad: deriving the deploy branch again inside pipeline execution when `dev_pipeline_run.branch_name` already stores the submit-time decision.
- Bad: deleting stale deploy branches by listing remote `release/*` refs; ownership must come from `dev_pipeline_run` rows scoped to one `application_env_id`.
- Bad: adding a per-branch `INFO` log inside the cleanup loop; aggregate the cleaned branches into one summary log after the loop.

### 6. Tests Required

- Compile DevOps server with reactor:
  `mvn -pl yudao-module-devops/yudao-module-devops-server -am -DskipTests compile`
- Run focused tests:
  `mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dtest='ApplicationServiceImplTest,PipelineExecutionServiceImplTest,*Pipeline*Test' -Dsurefire.failIfNoSpecifiedTests=false test`
- Cover:
  - `current-run` service method is annotated with `@Cacheable` using the application environment id as key;
  - release submission, pipeline publication, and public pipeline execution mutation methods are annotated with `@CacheEvict` for the same current-run cache;
  - release submit creates a run and dispatches `PipelineExecutionAsyncService.startPipelineAsync` only after commit;
  - current-run returns static nodes as `PENDING` when no run exists;
  - current-run returns `mountedBranches` matching env-detail's mounted branch data;
  - empty target set does not create a run;
  - an active run of the same application environment is stopped through `PipelineExecutionService#cancelRun` before the new run is created;
  - a stop failure, or a run still active after the stop attempt, throws `PIPELINE_RUN_STOP_FAILED` and creates no new run;
  - submit without removals reuses the latest successful deploy branch;
  - submit with removals creates a timestamp deploy branch;
  - rebuilding a deploy branch (removals or outdated base commit) deletes every historically recorded `release/` branch of that application environment and passes the repository provider id plus repo identifier;
  - reused branch names are deleted once, and the newly created branch is never a deletion target;
  - reusing a deploy branch performs no cleanup and does not query historical runs;
  - a cleanup failure is logged and does not fail `submit-branch` or prevent the new run from being created and dispatched;
  - workspace preparation clones the application-linked GitLab repository at the application default branch once per run for `submit-branch`;
  - YAML without `sources` uses the application-linked GitLab repository when `run.appId` exists, and creates an empty workspace only when no application context exists.

### 7. Wrong vs Correct

#### Wrong

```java
pipelineRunMapper.insert(pipelineRun);
pipelineExecutionEngine.execute(pipelineRun, userId);
```

#### Correct

```java
stopActivePipelineRuns(applicationEnv.getId(), userId);
pipelineRunMapper.insert(pipelineRun);
schedulePipelineStart(pipelineRun.getId(), changeIds, userId);
```

`stopActivePipelineRuns` must run after all read-only validations and before the first write of the submit transaction; `PipelineExecutionService#cancelRun` commits the cancellation in its own transaction.

`schedulePipelineStart` should register `TransactionSynchronization.afterCommit` and call an external `@Async` bean there; do not put `@Async` on a self-invoked private/local method.

## Scenario: Pipeline Upload Image Release Trigger

### 1. Scope / Trigger

- Trigger: adding or changing the "上传镜像部署" path, uploaded image archive import step, pipeline-run input context, or shared pipeline-builder image contract.
- Scope: `ApplicationController` upload-image release API, `ApplicationService` trigger creation, `PipelineRunDO`, `dev_pipeline_run`, `PipelineExecutionEngine`, source workspace preparation, `PipelineNodeRegistryServiceImpl`, `PipelineSpecValidationServiceImpl`, `DockerImageArchiveImportStepHandler`, `PIPELINE_YAML_SPEC.md`, `docker/pipeline-builder`, and focused application/pipeline tests.

### 2. Signatures

- API:
  - `POST /devops/application/release/upload-image`
  - Request: `applicationEnvId`, `fileUrl`
  - Response: `applicationEnvId`, `pipelineRunId`, `runStatus`
- DB:
  - `dev_pipeline_run.input_context_json varchar(4000) DEFAULT NULL COMMENT '触发输入上下文 JSON'`
  - Upload-image runs persist at least `{"fileUrl":"...","triggerMode":"UPLOAD_IMAGE"}`.
- Trigger type:
  - Upload-image release runs use `APPLICATION_UPLOAD_IMAGE`; do not reuse `APPLICATION_RELEASE_TAB`.
- YAML step:
  - `steps.<stepId>.step: DockerImageArchiveImport`
  - Required `with.fileUrl`, usually `${FILE_URL}`
  - Required `with.image`, usually with tag `${runId}`
  - Required `with.certificate.type=usernamePassword`, `username`, `password`
  - Optional `with.archiveFormat` in `docker-archive|oci-archive`, default `docker-archive`
  - Optional `with.compression` in `none|gzip|zstd`, default `none`
  - Optional `with.registryTlsVerify`, default `true`
- Runtime image/script:
  - `runsOn.container` must reference an image that contains `/usr/local/bin/import-image-archive-to-registry`, `curl`, `skopeo`, `gzip`, and `zstd`.
  - The project-provided image is `gone-cloud/pipeline-builder:java17-node24-maven3.9`.
  - The import script consumes env keys `FILE_URL`, `IMAGE_REF`, `ARCHIVE_FORMAT`, `COMPRESSION`, `REGISTRY_USERNAME`, `REGISTRY_PASSWORD`, `REGISTRY_TLS_VERIFY`, optional `MAX_DOWNLOAD_BYTES`, and optional `DOWNLOAD_TIMEOUT_SECONDS`.

### 3. Contracts

- Frontend upload is the primary entry: upload the archive elsewhere first, obtain a fresh `fileUrl`, then call the DevOps upload-image API. The DevOps API does not receive binary file streams.
- The trigger API accepts only `applicationEnvId` and `fileUrl`; it must not accept pipeline version id, target image, tag, or registry credentials.
- Backend resolves and starts the current published pipeline for the given application environment.
- The new capability must be an explicit YAML step. Do not inject a hidden import step from the trigger API.
- `OfflineImagePackage` is deprecated for this flow; do not persist, query, or revive it for upload-image deployment.
- Registry credentials belong to YAML step params and must not be copied into `input_context_json`, logs, `resultJson`, or step outputs.
- Runtime input context is persisted on `PipelineRunDO.inputContextJson` and injected into `sharedState` before step param resolution, so `fileUrl` is available as `${fileUrl}` and `${FILE_URL}`.
- `DockerImageArchiveImport` v1 supports `DockerImageExportObjectStorage` compatible archive files: `docker-archive` or `oci-archive`, optionally compressed with `gzip` or `zstd`.
- The import step does not need downstream outputs. Later deployment steps should pull the YAML-configured image from the registry, typically using `${runId}` as the tag.
- Upload-image runs that have no explicit YAML `sources` should skip implicit application Git checkout. Source checkout remains unchanged for `submit-branch` runs.
- The pipeline-builder image must be rebuilt and made available to the actual runner whenever the import script or required tools change; otherwise runtime fails with `sh: /usr/local/bin/import-image-archive-to-registry: No such file or directory`.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| Upload-image request missing `applicationEnvId` or `fileUrl` | Bean validation rejects the request |
| Application environment has no published pipeline | Throw the existing published-pipeline validation error before creating a run |
| Same application environment has an active run | Throw `PIPELINE_RUN_ACTIVE_EXISTS` before creating another run |
| `DockerImageArchiveImport.with.fileUrl` is blank | DSL validation error on `with.fileUrl` |
| `DockerImageArchiveImport.with.image` is blank | DSL validation error on `with.image` |
| `certificate.type` is missing or not `usernamePassword` | DSL validation error on `with.certificate.type` |
| `certificate.username/password` is blank | DSL validation error on the missing credential field |
| `archiveFormat` is not `docker-archive` or `oci-archive` | DSL validation error on `with.archiveFormat` |
| `compression` is not `none`, `gzip`, or `zstd` | DSL validation error on `with.compression` |
| Download, decompression, or `skopeo copy` fails | Step returns `FAIL` with sanitized summary and no credential leakage |
| Runner image lacks `/usr/local/bin/import-image-archive-to-registry` | Step fails in runtime; fix by rebuilding/publishing the runner image referenced by `runsOn.container` |

### 5. Good / Base / Bad Cases

- Good: release page uploads an archive, receives `fileUrl`, calls `POST /devops/application/release/upload-image`, and then polls the existing current-run/log APIs with the returned `pipelineRunId`.
- Good: YAML explicitly imports the archive to `registry.example.com/ns/app:${runId}`, and a following K8s step deploys the same image expression without reading step outputs.
- Base: arbitrary external `fileUrl` is allowed, but the script enforces bounded download size and timeout defaults.
- Bad: calling `submit-branch` for image-package deployment, because that path assumes source/change context.
- Bad: passing registry credentials or target image from the frontend trigger API.
- Bad: relying on local Docker Desktop's rebuilt image when the real runner pulls from another registry or runs on another CPU architecture.

### 6. Tests Required

- `ApplicationServiceImplTest` must cover upload-image run creation, published-version lookup, active-run rejection, persisted `inputContextJson`, and no change-env mount mutation.
- `PipelineExecutionEngineTest` must cover injecting `inputContextJson` into `sharedState` and resolving `${FILE_URL}`.
- `PipelineSpecValidationServiceImplTest` must cover valid `DockerImageArchiveImport` YAML and missing/unsupported `fileUrl`, `image`, credentials, `archiveFormat`, and `compression`.
- `DockerImageArchiveImportStepHandlerTest` must cover env construction, sanitized result/log behavior, command success/failure mapping, and cancellation delegation.
- Verify the runtime image when Docker is available:
  `docker build -t gone-cloud/pipeline-builder:java17-node24-maven3.9 yudao-module-devops/docker/pipeline-builder`
  and
  `docker run --rm gone-cloud/pipeline-builder:java17-node24-maven3.9 sh -lc 'command -v import-image-archive-to-registry && skopeo --version && zstd --version'`.

### 7. Wrong vs Correct

#### Wrong

```java
// Upload-image deployment should not pretend to be a source branch submit.
submitApplicationReleaseBranch(new ApplicationReleaseSubmitBranchReqVO()
        .setApplicationEnvId(applicationEnvId));
```

#### Correct

```java
Map<String, Object> inputContext = Map.of(
        "fileUrl", reqVO.getFileUrl(),
        "triggerMode", "UPLOAD_IMAGE");
pipelineRun.setTriggerType("APPLICATION_UPLOAD_IMAGE");
pipelineRun.setInputContextJson(JsonUtils.toJsonString(inputContext));
```

#### Wrong

```yaml
# Hidden backend behavior imports an archive before this YAML starts.
steps:
  deploy:
    step: K8sImageUpgrade
```

#### Correct

```yaml
steps:
  import_image:
    step: DockerImageArchiveImport
    with:
      fileUrl: ${FILE_URL}
      image: registry.example.com/ns/app:${runId}
      archiveFormat: docker-archive
      compression: gzip
      certificate:
        type: usernamePassword
        username: ${REGISTRY_USERNAME}
        password: ${REGISTRY_PASSWORD}
  deploy:
    step: K8sImageUpgrade
    with:
      image: registry.example.com/ns/app:${runId}
```

## Scenario: Pipeline Run Change Snapshot

### 1. Scope / Trigger

- Trigger: changing release submit, pipeline run persistence, or release current-run response data used to compare deployed commit snapshots with latest change branch commits.
- Scope: `ApplicationService.submitApplicationReleaseBranch`, `ApplicationReleaseCurrentRunRespVO`, `PipelineRunDO`, `dev_pipeline_run`, and focused application service tests.

### 2. Signatures

- DB:
  - `dev_pipeline_run.change_snapshot_json varchar(4000) DEFAULT NULL`
  - JSON item minimum shape: `{"changeId": 1024, "commitSha": "abc123"}`.
- API:
  - `GET /devops/application/release/current-run?applicationEnvId={id}`
  - Response includes `changeSnapshots: List<{changeId, commitSha}>`.
- Write source:
  - Snapshot is built from the submitted `changeIds` and each target `dev_change.latest_commit_sha` at run creation time.

### 3. Contracts

- `dev_change.latest_commit_sha` is mutable remote HEAD metadata, usually updated by repository webhooks.
- `dev_pipeline_run.change_snapshot_json` is immutable run-time metadata: it records what each submitted change pointed to when the run was created.
- Snapshot generation must preserve the order of submitted `changeIds`.
- Keep existing single-change compatibility anchor fields (`change_id`, `change_env_id`, `branch_name`, `commit_sha`) until downstream callers are migrated.
- Frontend compares `ApplicationReleaseBranchRespVO.latestCommitSha` with `current-run.changeSnapshots[*].commitSha` by `changeId` to detect branches that advanced after deployment.
- Do not store only `changeId` in the run snapshot. The same change id can advance to a new commit after deployment.
- `current-run` may return an empty `changeSnapshots` list for older runs created before the column existed.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| `changeIds` is empty on submit | Create a baseline release run, write an empty snapshot, and trigger Jenkins without merging change branches |
| requested change is missing/inactive/wrong app | Existing submit validation throws before snapshot creation |
| requested change has null latest commit | Snapshot item stores null `commitSha`; frontend can treat it as unknown |
| old run has blank snapshot JSON | `current-run.changeSnapshots` is empty |
| new run is created | Snapshot contains every requested change id and its then-current commit sha |

### 5. Good / Base / Bad Cases

- Good: `change_snapshot_json` stores compact run metadata and `current-run` exposes a typed response list.
- Base: compatibility anchor fields continue to store the first requested change for older downstream logic.
- Bad: re-reading `dev_change.latest_commit_sha` later and treating that as deployed state, because webhooks mutate it after the run.
- Bad: adding a separate persistent relation table before there is a query/audit need beyond compact current-run comparison.

### 6. Tests Required

- Service test that submit with multiple changes writes snapshot JSON in request order.
- Service test that `current-run` parses snapshot JSON into `changeSnapshots`.
- Existing tests must continue covering empty target set baseline release, active run conflict, and code-merge scheduling.
- Compile/test command:
  - `mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dtest=ApplicationServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test`

### 7. Wrong vs Correct

#### Wrong

```java
pipelineRun.setChangeSnapshotJson(JsonUtils.toJsonString(changeIds));
```

#### Correct

```java
pipelineRun.setChangeSnapshotJson(JsonUtils.toJsonString(changes.stream()
        .map(change -> new PipelineRunChangeSnapshotContext(change.getId(), change.getLatestCommitSha()))
        .toList()));
```

## Scenario: Pipeline Step Handler Status Read Model

### 1. Scope / Trigger

- Trigger: adding or changing pipeline step types, step handlers, approval steps, or `GET /devops/application/release/current-run` step/card response fields.
- Scope: `PipelineNodeRegistryServiceImpl`, `PipelineSpecValidationServiceImpl`, `PipelineStepHandler` implementations, `PipelineApprovalService`, `PipelineExecutionEngine`, current-run stage/job/step response VOs, and focused pipeline/application service tests.

### 2. Signatures

- Step type registry:
  - `CodeMerge`: platform step, required params `baseBranch` and `targetBranch`, optional params `branches`, `branchesFromSubmit`, and `pushOnSuccess`.
  - `APPROVAL`: gate step, required param `processDefinitionKey`.
  - `EXECUTE_SHELL`: build/runtime step, required param `script`.
- Handler contract:
  - Every executable step type should have a `PipelineStepHandler`.
  - `PipelineStepHandler#runtimeRequirement(ctx)` returns `PLATFORM` or `JOB_RUNTIME`; the default delegates to the existing no-arg method, while Command selects by `with.sshHostId`.
  - `PipelineStepHandler#handle` returns `CONTINUE`, `SUSPEND`, or `FAIL`.
  - Manual gates such as approval and code-merge conflicts must return `SUSPEND` instead of blocking a thread.
- Current-run response:
  - Return `stages[]`, each containing `stageId`, `stageName`, and `jobs[]`.
  - Each job item contains `jobId`, `jobName`, `status`, `summary`, `needs`, runtime metadata, and `steps[]`.
  - Each step item contains `stepId`, `stepType`, `stepName`, `status`, `message`, `detailType`, `detailRef`, and `actions`.
  - Step raw log status remains one of `PENDING`, `RUNNING`, `WAITING_INPUT`, `SUCCESS`, `FAILED`, `CANCELED`.
  - Job status remains one of `PENDING`, `RUNNING`, `BLOCKED`, `SUCCESS`, `FAILED`, `SKIPPED`, `CANCELED`.

### 3. Contracts

- Do not special-case approval execution in `PipelineExecutionEngine`; dispatch `APPROVAL` through a `PipelineStepHandler` like other step types.
- `PipelineApprovalService.startApproval(...)` is allowed to own BPM process creation and callback state updates, but handler-level chain control must be driven by an explicit execution status:
  - existing approved log -> `CONTINUE`;
  - newly started or still waiting approval -> `SUSPEND`;
  - rejected/canceled/failed approval -> `FAIL`.
- Approval step logs must use `stepType=APPROVAL`.
- `APPROVAL` DSL validation must reject missing or blank `processDefinitionKey`.
- Current-run should return stage/job/step structures. Frontend card rendering should use job `status`, step `status`, `message`, `detailType`, `detailRef`, and `actions`.
- Do not define step-type-specific status enums for dynamic concepts such as approval levels. Put the display sentence in `message`, the detail selector in `detailType`, and the next-step buttons in `actions`.
- Code-merge conflict cards should return `status=BLOCKED`, a conflict message, `detailType=CODE_MERGE`, `detailRef.conflictCount`, and an action with `code=RESOLVE_CODE_CONFLICT`.
- Waiting approval cards should return `status=BLOCKED`, an approval message such as `等待老板审批`, `detailType=APPROVAL`, `detailRef.processInstanceId`, and an action with `code=OPEN_APPROVAL_DETAIL` when a process instance exists.
- `PLATFORM` steps must not create Docker runtime. `JOB_RUNTIME` steps create the job runtime lazily through the execution engine before the handler is called.
- `CodeMerge` is `PLATFORM`: it must not create or hold a Docker runtime. It uses platform Git services, writes merge result to step outputs, and lets downstream `JOB_RUNTIME` jobs checkout the merged target branch/commit.
- `CodeMerge.with.branches` is a string array and is merged in declaration order. If the array is present and non-empty, it takes precedence over `branchesFromSubmit`.
- `CodeMerge.with.branchesFromSubmit` defaults to true for submit-triggered pipelines. If no explicit `branches` are configured, the step uses run change ids / submitted change branches.
- `CodeMerge.with.pushOnSuccess` defaults to true. Setting it false is for dry-run style tests; downstream checkout can only use a pushed merged branch when the remote repository can resolve it.
- A suspended step makes the owning job `BLOCKED`. The concrete step log keeps `WAITING_INPUT` so the UI can identify the suspended step.
- Step handlers must be idempotent: an already successful step returns `CONTINUE`; a still-waiting step returns `SUSPEND`; failed or canceled logs return `FAIL` unless this is an explicit retry attempt.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| `APPROVAL` step missing `processDefinitionKey` | DSL validation error on `with.processDefinitionKey` |
| `APPROVAL` handler starts BPM successfully | Step log becomes `WAITING_INPUT`; job becomes `BLOCKED`; handler returns `SUSPEND`; current-run step shows `status=WAITING_INPUT`, job shows `status=BLOCKED`, and step includes approval `message`, `detailType=APPROVAL`, and optional `OPEN_APPROVAL_DETAIL` action |
| BPM approval callback is approved | Approval log becomes `SUCCESS`; event path re-enters engine; handler treats existing success as `CONTINUE` |
| BPM approval callback is rejected | Approval log becomes `FAILED`; run is stopped as failed; current-run step shows `status=FAILED`, rejection `message`, and no approval action |
| Code merge has conflicts | Step log remains `WAITING_INPUT`; job becomes `BLOCKED`; current-run step includes conflict `message`, `detailType=CODE_MERGE`, `detailRef.conflictCount`, and `RESOLVE_CODE_CONFLICT` action |
| Pipeline run is canceled while approval is waiting | Engine cancellation invokes approval cancellation and marks the step/job/run canceled |

### 5. Good / Base / Bad Cases

- Good: adding a new step type means registering it, validating required params, adding a handler, declaring runtime requirement, and adding current-run message/detail/action mapping when generic log rendering is not expressive enough.
- Good: frontend cards render common color/progress from `status`, show backend-provided `message`, and render buttons from `actions`.
- Base: generic step types can still use run-log `summary` as `message`, `detailType=RUN_LOGS`, and an empty `actions` list.
- Bad: using `WAITING_INPUT` directly as the only UI state, because code conflicts and approval waiting need different text/actions but share the same raw log status.
- Bad: adding `APPROVAL_WAITING_BOSS`-style statuses when a dynamic approval message and action target can represent the same meaning without coupling.
- Bad: creating an approval service path outside `PipelineStepHandler`, because the engine cannot then reason consistently about suspend/continue/fail.

### 6. Tests Required

- Registry test asserts `APPROVAL` is an enabled configurable step type with `processDefinitionKey`.
- DSL validation test asserts `APPROVAL` with `processDefinitionKey` is valid and missing it fails on `with.processDefinitionKey`.
- Handler test asserts approval `SUCCESS` maps to `CONTINUE` and waiting maps to `SUSPEND`.
- Approval service test asserts created logs use `stepType=APPROVAL`, waiting logs return suspend, and existing success returns success.
- Current-run service test asserts code conflicts map to job `status=BLOCKED`, step `status=WAITING_INPUT`, `detailType=CODE_MERGE`, conflict detail refs, and `RESOLVE_CODE_CONFLICT`.
- Current-run service test asserts waiting approval maps to job `status=BLOCKED`, step `status=WAITING_INPUT`, approval detail refs, and `OPEN_APPROVAL_DETAIL`, while rejected approval maps to step `status=FAILED` with no approval action.
- Compile/test command:
  - `mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dtest='ApplicationServiceImplTest,PipelineExecutionServiceImplTest,*Pipeline*Test,ApprovalStepHandlerTest' -Dsurefire.failIfNoSpecifiedTests=false test`
  - `mvn -pl yudao-module-devops/yudao-module-devops-server -am -DskipTests compile`

### 7. Wrong vs Correct

#### Wrong

```java
if ("APPROVAL".equals(step.getStep())) {
    pipelineApprovalService.startApproval(run, step, userId);
    return;
}
```

#### Correct

```java
PipelineStepHandler handler = stepHandlerRegistry.resolve(step.getStep());
StepResult result = handler.handle(context);
```

#### Wrong

```java
node.setExecutionStatus(runLog.getStatus());
```

#### Correct

```java
node.setExecutionStatus(runLog.getStatus());
node.setStatus(resolveNodeStatus(runLog.getStatus()));
node.setMessage(resolveNodeMessage(node, runLog));
node.setActions(buildNodeActions(node, runLog));
```

## Scenario: Jenkins Tool Dropdown Options

### 1. Scope / Trigger

- Trigger: adding or changing visual pipeline parameters that should select Jenkins global tool names instead of accepting free-form text.
- Scope: Jenkins HTTP client, pipeline admin controller, pipeline node schema metadata, and focused tests.

### 2. Signatures

- API:
  - `GET /devops/pipeline/jenkins-tools`
  - `GET /devops/pipeline/jenkins-tools?type=JDK`
  - `GET /devops/pipeline/jenkins-tools?type=MAVEN`
- Response item shape:
  - `type`: `JDK` or `MAVEN`
  - `name`: Jenkins global tool name; this is the value stored in pipeline params and emitted into Jenkinsfile `tools`
  - `home`: optional Jenkins tool home path
- Jenkins descriptor reads combine route candidates and descriptor id candidates:
  - route candidates, in order:
    - `/descriptorByName`
    - `/manage/descriptorByName`
  - JDK descriptor id candidates, in order:
    - `hudson.model.JDK`
    - `hudson.model.JDK$DescriptorImpl`
  - Maven descriptor id candidates, in order:
    - `hudson.tasks.Maven$MavenInstallation`
    - `hudson.tasks.Maven$MavenInstallation$DescriptorImpl`
    - `hudson.tasks.Maven`
    - `hudson.tasks.Maven$DescriptorImpl`
- Jenkins Script Console fallback:
  - `POST /scriptText`
  - Form field `script=<Groovy script that prints JSON array [{name,home}]>`
  - Requires a Jenkins user with Script Console permission, normally `Overall/Administer`.

### 3. Contracts

- Pipeline params remain strings: `toolJdk` and `toolMaven` store the selected Jenkins tool `name`.
- Node schema marks remote-select fields with:
  - `x-component=select`
  - `x-optionSource.type=remote`
  - `x-optionSource.url=/devops/pipeline/jenkins-tools?type=<TYPE>`
  - `x-optionSource.labelField=name`
  - `x-optionSource.valueField=name`
- Jenkins tool lookup requires `devops.jenkins.enabled=true` and `devops.jenkins.base-url`; it does not require `devops.jenkins.job-name`.
- When Jenkins integration is disabled, return an empty list so the designer can degrade gracefully.
- A missing Jenkins descriptor, for example Maven plugin not installed, returns an empty list for that tool type.
- If descriptor JSON APIs all return 404, fallback to `/scriptText` before returning an empty list.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| `type` is blank | Return JDK and Maven tools in one list |
| `type=JDK` | Query only the JDK descriptor |
| `type=MAVEN` | Query only the Maven descriptor |
| unsupported `type` | Throw `PIPELINE_JENKINS_CONFIG_INVALID` |
| One Jenkins descriptor route/id candidate returns 404 | Try the next descriptor route/id candidate for the same tool type |
| All Jenkins descriptor candidates return 404 | Try `/scriptText` fallback |
| `/scriptText` returns 404 after descriptor candidates fail | Return an empty list for that tool type |
| `/scriptText` returns 403 after descriptor candidates fail | Throw `PIPELINE_JENKINS_TOOL_FETCH_FAIL`; the Jenkins API user likely lacks Script Console permission |
| Jenkins request fails for other reasons | Throw `PIPELINE_JENKINS_TOOL_FETCH_FAIL` with a sanitized message |

### 5. Good / Base / Bad Cases

- Good: frontend renders `toolJdk` and `toolMaven` as selects backed by `jenkins-tools`, then saves the selected `name` string in the existing DSL.
- Base: no Jenkins tools are configured; the endpoint returns `[]` and the stage can still omit tool names.
- Bad: frontend hard-codes tool names such as `jdk-17.0.12` or `mvn`, because Jenkins global tool names are environment-specific.
- Bad: generated Jenkinsfile writes tool home paths instead of Jenkins tool names.

### 6. Tests Required

- Jenkins client unit test parses descriptor `installations[name,home]` into `type/name/home`.
- Jenkins client unit test falls back from short descriptor id 404 to `$DescriptorImpl`.
- Jenkins client unit test falls back from root `descriptorByName` to `/manage/descriptorByName`.
- Jenkins client unit test falls back to `/scriptText` when descriptor route/id candidates all 404.
- Jenkins client unit test treats all descriptor candidates 404 as empty list.
- Jenkins client unit test maps non-404 request failures to `PIPELINE_JENKINS_TOOL_FETCH_FAIL`.
- Node registry test asserts remote-select metadata for `toolJdk` and `toolMaven`.

### 7. Wrong vs Correct

#### Wrong

```java
properties.put("toolMaven", stringParam("Jenkins Maven 工具名", ""));
```

#### Correct

```java
properties.put("toolMaven", remoteSelectParam("Jenkins Maven 工具名", "",
        "/devops/pipeline/jenkins-tools?type=MAVEN"));
```

## Scenario: Visual Pipeline Definition MVP

### 1. Scope / Trigger

- Trigger: adding or changing visual pipeline definition, pipeline DSL validation, Jenkinsfile preview generation, or application-environment pipeline linkage.
- Scope: `yudao-module-devops` pipeline controllers, VOs, services, DSL model, mappers, SQL bootstrap scripts, dict/menu scripts, and focused tests.

### 2. Signatures

- APIs:
  - `GET /devops/pipeline/node-types`
  - `GET /devops/pipeline/command-templates`
  - `GET /devops/pipeline/get-by-application-env?applicationEnvId={id}`
  - `POST /devops/pipeline/save-draft`
  - `POST /devops/pipeline/validate`
  - `POST /devops/pipeline/publish`
  - `GET /devops/pipeline/version/jenkinsfile?id={versionId}`
  - `GET /devops/pipeline/version/list?definitionId={definitionId}`
- DB:
  - `dev_pipeline_definition` stores one pipeline definition per `dev_application_env`.
  - `dev_pipeline_definition_version` stores mutable draft version `version_no=0` plus immutable published versions from `version_no=1`.
  - `dev_application_env.pipeline_definition_id` links the environment to the active platform-owned definition.
- Dict:
  - `dev_pipeline_definition_version_status`: `0` draft, `1` published, `2` archived.
- Menu:
  - Pipeline designer is a hidden `system_menu` entry under the DevOps application menu.
  - Route path is `/devops/pipeline/designer`, component is `devops/pipeline/designer`, component name is `DevopsPipelineDesigner`, and permission is `devops:pipeline:query`.
  - `devops:pipeline:create` and `devops:pipeline:delete` may be bootstrapped as reserved button permissions even when the current frontend does not render them.

### 3. Contracts

- Save draft accepts `applicationEnvId`, `name`, `diagramJson`, `specJson`, and optional `remark`.
- Save draft may persist an invalid DSL so the frontend can keep user edits; it must also persist `validationResultJson`.
- Publish only accepts a valid draft belonging to the same definition; publish creates a new published version and updates `dev_application_env.pipeline_definition_id`.
- Build/test/image commands must come from backend command templates via `params.commandTemplateKey`; frontend must not submit arbitrary raw shell commands.
- Disabled future nodes such as approval and deploy can be returned by `/node-types`, but validation must reject them until the backend implements execution semantics.
- Phase 1 Jenkinsfile is a generated preview/published artifact. Jenkins build-run creation, stage callbacks, logs, and platform approval resume are Phase 2 contracts.
- The backend menu table does not carry a frontend `activeMenu` / `active_menu` field. Hidden designer route activation must be configured in frontend route meta, not in `sql/mysql/devops-menu.sql`.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| `applicationEnvId` does not exist on save | Throw `APPLICATION_ENV_NOT_EXISTS` |
| Definition id does not exist | Throw `PIPELINE_DEFINITION_NOT_EXISTS` |
| Draft version id does not exist | Throw `PIPELINE_VERSION_NOT_EXISTS` or `PIPELINE_DRAFT_NOT_EXISTS` depending on ownership/status |
| `specJson` is blank or invalid JSON | Return validation error such as `SPEC_JSON_REQUIRED` or `SPEC_JSON_INVALID` |
| Node id is blank, duplicate, or invalid shape | Return validation errors; do not throw runtime exceptions |
| Node type is unknown or disabled | Return validation error; do not generate Jenkinsfile |
| Build/test/image node lacks a template | Return `COMMAND_TEMPLATE_REQUIRED` |
| Template does not exist or type mismatches node | Return template validation error |
| Graph has multiple starts, multiple terminals, or a cycle | Return topology validation error |
| Publish invalid draft | Throw `PIPELINE_SPEC_INVALID` |

### 5. Good / Base / Bad Cases

- Good: platform stores DSL and generated Jenkinsfile preview; Jenkins receives generated content later as an execution input.
- Base: visual editor can show disabled approval/deploy nodes as unavailable placeholders while backend rejects them on validation.
- Bad: frontend sends raw `mvn` or `docker` commands directly in node params, because this bypasses backend governance and makes future audit unsafe.

### 6. Tests Required

- Compile DevOps server with reactor: `mvn -pl yudao-module-devops/yudao-module-devops-server -am -DskipTests compile`.
- Run focused pipeline tests with reactor: `mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dtest='*Pipeline*Test,JenkinsfileGeneratorServiceImplTest' -Dsurefire.failIfNoSpecifiedTests=false test`.
- Cover:
  - draft creation creates definition and draft version;
  - publish creates a new published version and links application environment;
  - disabled nodes and missing command templates fail validation;
  - malformed/null DSL content returns validation errors instead of throwing;
  - Jenkinsfile generation contains the expected ordered stages and template commands.

### 7. Wrong vs Correct

#### Wrong

```java
String command = String.valueOf(node.getParams().get("command"));
builder.append("sh '").append(command).append("'");
```

#### Correct

```java
String templateKey = String.valueOf(node.getParams().get("commandTemplateKey"));
PipelineCommandTemplateRespVO template = pipelineNodeRegistryService.getCommandTemplate(templateKey);
builder.append("goneDevopsUnitTest(command: '").append(template.getCommand()).append("')");
```

## Design Decision: Platform-Owned Definition, Jenkins-Executable Output

**Context**: The platform needs visual editing, versioning, validation, approval, deployment orchestration, and stage visibility. Jenkins is useful as an executor, but it should not own product-level pipeline definition state.

**Decision**: Store pipeline definition/version/spec/Jenkinsfile in the platform database. Generate Jenkinsfile from backend-approved DSL and templates. Defer Jenkins job invocation and run-stage sensing to the deployment execution phase.

**Extensibility**: Later execution can add `pipeline_run` and `pipeline_run_stage` models, Jenkins queue/build mapping, log retrieval, and approval resume callbacks without changing the visual definition ownership model.

## Scenario: Jenkins-Compatible Execution Nodes

### 1. Scope / Trigger

- Trigger: adding or changing Jenkins-executable visual pipeline nodes, node parameter schema, Jenkinsfile stage generation, Jenkins callback node support, or Jenkins Runner documentation.
- Scope: `PipelineNodeRegistryServiceImpl`, `PipelineSpecValidationServiceImpl`, `JenkinsfileGeneratorServiceImpl`, `JenkinsPipelineNodeRuntimeHandler`, pipeline node tests, and `yudao-module-devops/JENKINS_RUNNER_CONFIGURATION.md`.

### 2. Signatures

- Node type API:
  - `GET /devops/pipeline/configurable-node-types`
  - Each node response must include `type`, `name`, `category`, `defaultParams`, and JSON-schema-like `paramSchema`.
- DSL node:
  - `PipelineSpec.ExecutableStep.step`
  - `PipelineSpec.ExecutableStep.name`
  - `PipelineSpec.ExecutableStep.with`
  - optional `timeoutSeconds` and `retryTimes`
- Supported Jenkins node types:
  - `CHECKOUT`
  - `MAVEN_BUILD_JAR`
  - `NPM_BUILD`
  - `DOCKER_BUILD_PUSH`
  - `ARTIFACT_UPLOAD`
  - compatibility aliases: `UNIT_TEST`, `BUILD_ARTIFACT`, `BUILD_IMAGE`, `REPORT_ARTIFACTS`, `MOCK`
- Jenkins shared library vars expected by generated Jenkinsfile:
  - `goneDevopsCallback`
  - `goneDevopsCheckout`
  - `goneDevopsMavenBuildJar`
  - `goneDevopsNpmBuild`
  - `goneDevopsDockerBuildPush`

### 3. Contracts

- One enabled platform node must generate exactly one Jenkins `stage`.
- Jenkins stage display name should use `PipelineSpec.ExecutableStep.name`; stable callback identity must use `stepId`, `stepType`, and `stepName`.
- Common Jenkins stage params live in `step.with`:
  - `agentLabel` -> stage `agent { label '...' }`
  - `toolJdk` -> stage `tools { jdk '...' }`
  - `toolMaven` -> stage `tools { maven '...' }`
  - `env` -> stage `environment { KEY = 'value' }`
  - `timeoutSeconds` -> stage `options { timeout(...) }`
  - `retryTimes` -> stage `options { retry(...) }`
- `MAVEN_BUILD_JAR` required params: `workingDir`, `goals`, `artifactPattern`.
- `NPM_BUILD` required params: `workingDir`, `packageManager`, `installCommand`, `buildCommand`, `distPattern`.
- `DOCKER_BUILD_PUSH` required params: `imageName`, `imageTagExpression`, `dockerfile`, `context`.
- `ARTIFACT_UPLOAD` required params: `artifactPattern`; this phase maps it to Jenkins `archiveArtifacts` only.
- Raw shell commands submitted directly from frontend are forbidden for new Jenkins-compatible nodes. If shell execution is needed, it must be mediated by backend-owned node params and generated Jenkinsfile/shared-library wrappers.
- Existing published versions that use compatibility aliases must remain executable.
- Nexus/MinIO/external artifact repositories are out of scope until a product-level repository and credential model is defined.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| New Jenkins node misses a required param | Return validation error `PARAM_REQUIRED` on `params.<field>` |
| Boolean param such as `skipTests`, `push`, `fingerprint` is not boolean | Return validation error `PARAM_TYPE_INVALID` |
| `env` is present but not an object | Return validation error `PARAM_TYPE_INVALID` |
| `env` key is not `[A-Za-z_][A-Za-z0-9_]*` | Return validation error `PARAM_ENV_KEY_INVALID` |
| Compatibility build/test/image node misses `commandTemplateKey` | Keep returning `COMMAND_TEMPLATE_REQUIRED` |
| Compatibility `REPORT_ARTIFACTS` omits `artifactPattern` | Keep old behavior valid and use generator defaults |
| Jenkins callback arrives for new node type | `JenkinsPipelineNodeRuntimeHandler` must accept and update run log |

### 5. Good / Base / Bad Cases

- Good: `MAVEN_BUILD_JAR` generates a stage that calls `goneDevopsMavenBuildJar(...)` with typed params.
- Good: `ARTIFACT_UPLOAD` generates Jenkins-native `archiveArtifacts`, not an untyped upload shell.
- Good: new node schemas include both required node-specific fields and common Jenkins stage fields.
- Base: old `BUILD_ARTIFACT` with `maven_package_skip_tests` remains valid and maps to Maven wrapper behavior.
- Base: old `BUILD_IMAGE` remains valid and maps to Docker wrapper behavior.
- Bad: frontend submits `params.command = "mvn clean package"` for a new node and backend blindly appends it to Jenkinsfile.
- Bad: generated stage name uses only type/id and loses the platform-visible node name.
- Bad: callback support is added to Jenkinsfile generation but omitted from `JenkinsPipelineNodeRuntimeHandler`.

### 6. Tests Required

- `PipelineNodeRegistryServiceImplTest`:
  - new node types are enabled and expose param schema fields;
  - disabled future nodes remain excluded from configurable list.
- `PipelineSpecValidationServiceImplTest`:
  - missing required params fail by node id and field;
  - invalid common env keys fail;
  - compatibility aliases remain valid.
- `JenkinsfileGeneratorServiceImplTest`:
  - generated stages use node names;
  - common `agent/tools/environment/options` fields are rendered;
  - Maven/NPM/Docker/archive node bodies contain expected wrapper or Jenkins-native steps.
- `PipelineJenkinsCallbackServiceImplTest`:
  - callback lifecycle is accepted for at least one new Jenkins node type.
- Verification commands:
  - `mvn -pl yudao-module-devops/yudao-module-devops-server -am -DskipTests compile`
  - `mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dtest='*Pipeline*Test,JenkinsfileGeneratorServiceImplTest' -Dsurefire.failIfNoSpecifiedTests=false test`

### 7. Wrong vs Correct

#### Wrong

```java
String command = String.valueOf(node.getParams().get("command"));
builder.append("sh '").append(command).append("'");
```

#### Correct

```java
builder.append("goneDevopsMavenBuildJar(workingDir: '")
        .append(param(node, "workingDir", "."))
        .append("', goals: '")
        .append(param(node, "goals", "clean package"))
        .append("')");
```

## Scenario: Application Detail Release Tab Read Model

### 1. Scope / Trigger

- Trigger: adding or changing the application-detail "发布" tab, release environment tabs, read-only pipeline display, or branch mount lists.
- Scope: `ApplicationController`, application release response VOs, `ApplicationService`, application/change/environment/pipeline mappers, and focused application service tests.

### 2. Signatures

- APIs:
  - `GET /devops/application/release/env-tabs?appId={appId}`
  - `GET /devops/application/release/env-detail?applicationEnvId={applicationEnvId}`
  - `POST /devops/application/release/submit-branch`
  - Existing action APIs reused by the page:
    - `POST /devops/change/mount-env`
    - `PUT /devops/change/unmount-env`
- Permissions:
  - Release tab read APIs use `devops:application:query`.
  - Release tab submit-and-trigger action uses `devops:application:release-submit`.
  - Mount action uses `devops:change:mount-env`.
  - Unmount action uses `devops:change:unmount-env`.
- DB ownership:
  - `dev_application_env` owns app-to-environment linkage and display order.
  - `dev_pipeline_definition.application_env_id` owns the pipeline definition for one app environment.
  - `dev_pipeline_definition.published_version_id` identifies the released pipeline shown in the tab.
  - `dev_change` owns app-level effective branches.
  - `dev_change_env` owns branch-to-application-environment mount status.
  - `dev_pipeline_run` owns platform-side pipeline run records created by release-tab submit actions.

### 3. Contracts

- `env-tabs` validates the application exists, then returns application environments ordered by `display_order ASC, id ASC`.
- Environment tab payload must include both relation fields and display fields: `applicationEnvId`, `appId`, `envId`, `envKey`, `envName`, `envStage`, `infraType`, `displayOrder`, `deployBranchNamePattern`, `pipelineDefinitionId`, `hasPublishedPipeline`, and `status`.
- `env-detail` validates the application-environment relation and environment exist, then returns:
  - `env`: same shape as one `env-tabs` item.
  - `pipeline`: only the published pipeline version. Draft versions must not be shown as release pipelines.
  - `mountedBranches`: active app changes with `dev_change_env.mount_status = MOUNTED` for the current `applicationEnvId`.
  - `unmountedBranches`: active app changes with no current mounted relation for the current `applicationEnvId`; historical `UNMOUNTED` / `AUTO_CLEANED` rows belong here.
- Effective branch means `dev_change.status = ChangeStatusEnum.ACTIVE`.
- Pipeline display must be linearized from published `specJson` via topological sort. Do not use designer canvas coordinates for the release tab.
- If no definition or no published version exists, return a pipeline payload with empty `nodes` / `edges` and `emptyReason` instead of throwing.
- `submit-branch` request body is `applicationEnvId + changeIds[]`, where `changeIds` means the final desired mounted set for the current environment.
- `submit-branch` validates every requested change exists, is `ACTIVE`, and belongs to the same application as the target app-environment.
- `submit-branch` treats the request as a full-set sync:
  - requested ids are mounted/restored into `dev_change_env`;
  - currently mounted ids that are absent from the request are marked `UNMOUNTED`;
  - historical `UNMOUNTED` rows can be restored by including their change id again.
- `submit-branch` requires an existing published pipeline version only when `changeIds` is non-empty.
- `submit-branch` stops the currently active run of the same application environment before creating the new run; a failed stop returns `PIPELINE_RUN_STOP_FAILED` to the caller and leaves the target set untouched.
- `submit-branch` with a non-empty `changeIds` creates one `dev_pipeline_run` row, updates every target `dev_change_env.last_pipeline_run_id`, and marks the latest build status as running.
- `submit-branch` with an empty `changeIds` means "remove all deployed branches from this environment"; it updates `dev_change_env` mount status only and does not create a new pipeline run.
- Current `dev_pipeline_run` schema still has single-change anchor fields (`change_id`, `change_env_id`, `branch_name`), so the environment-level sync run stores the first requested change as the compatibility anchor. Jenkins queue/build triggering and callbacks should still attach to `dev_pipeline_run` instead of creating another run concept.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| `appId` does not exist on `env-tabs` | Throw `APPLICATION_NOT_EXISTS` |
| `applicationEnvId` does not exist on `env-detail` | Throw `APPLICATION_ENV_NOT_EXISTS` |
| linked environment does not exist | Throw `ENVIRONMENT_NOT_EXISTS` |
| no pipeline definition | Return `pipeline.emptyReason = NO_PIPELINE_DEFINITION` |
| definition exists but no published version | Return `pipeline.emptyReason = NO_PUBLISHED_VERSION` |
| published version references invalid or cyclic `specJson` | Return `pipeline.emptyReason = SPEC_INVALID` |
| active change has `UNMOUNTED` relation for the current environment | Include it in `unmountedBranches` |
| active change has no relation for the current environment | Include it in `unmountedBranches` |
| `submit-branch` any requested change is not `ACTIVE` | Throw `CHANGE_STATUS_NOT_ACTIVE` |
| `submit-branch` any requested change belongs to another app | Throw `APPLICATION_ENV_NOT_EXISTS` |
| `submit-branch` non-empty `changeIds` has no published pipeline version | Throw `PIPELINE_PUBLISHED_VERSION_NOT_EXISTS` |
| `submit-branch` cannot stop the currently active run | Throw `PIPELINE_RUN_STOP_FAILED` |

### 5. Good / Base / Bad Cases

- Good: frontend loads environment tabs from one application-scoped read API, then loads the selected environment detail from one application-environment-scoped read API.
- Good: frontend uses `POST /devops/application/release/submit-branch` as the single release-tab write API and always submits the final target change-id set for the environment.
- Good: frontend renders `pipeline.nodes` from left to right using `displayOrder`; it may use `edges` for simple connectors.
- Base: existing `/devops/pipeline/get-by-application-env` remains the designer/read API for full pipeline definition and draft/published metadata.
- Base: existing `/devops/change/mount-env` remains a plain mount API and must not be treated as a pipeline trigger.
- Bad: frontend joins application envs, environments, pipeline versions, and change-env rows through several independent table APIs and reimplements branch mount rules.
- Bad: release tab displays a draft pipeline as if it were released.

### 6. Tests Required

- Service test that `env-tabs` returns environment display fields and `hasPublishedPipeline`.
- Service test that `env-detail` returns published pipeline metadata and topologically ordered nodes.
- Service test that active branches split into mounted and unmounted lists using `ChangeEnvMountStatusEnum.MOUNTED`.
- Service test that no published version returns `NO_PUBLISHED_VERSION` with empty node/edge lists.
- Service test that `submit-branch` can add new changes by syncing the target set, creates/restores `ChangeEnvDO`, creates one `PipelineRunDO`, updates all target `lastPipelineRunId` values, and returns mounted/unmounted change ids.
- Service test that `submit-branch` can remove partial changes by syncing the target set and marks removed rows as `UNMOUNTED`.
- Service test that `submit-branch` with empty `changeIds` unmounts all current rows and does not create a pipeline run.
- Service test that `submit-branch` without a published version throws `PIPELINE_PUBLISHED_VERSION_NOT_EXISTS`.
- Service test that `submit-branch` stops the active run of the same application environment before creating the new run.
- Service test that a failed stop, or a run still active after the stop, throws `PIPELINE_RUN_STOP_FAILED` and does not create a pipeline run.
- Compile or run the DevOps server module after controller/VO changes:
  `mvn -pl yudao-module-devops/yudao-module-devops-server -am test`

### 7. Wrong vs Correct

#### Wrong

```java
// Release tab treats a draft as a deployable/released pipeline.
respVO.setSpecJson(definition.getDraftVersion().getSpecJson());
```

#### Correct

```java
if (definition.getPublishedVersionId() == null) {
    pipeline.setEmptyReason("NO_PUBLISHED_VERSION");
    pipeline.setNodes(List.of());
    return pipeline;
}
```

#### Wrong

```java
// Plain mount does not create a run anchor, so later callbacks have nowhere stable to attach.
changeService.mountChangeEnv(reqVO, userId);
```

#### Correct

```java
PipelineRunDO run = createPipelineRun(definition, publishedVersion, changeEnv, change, userId);
changeEnv.setLastPipelineRunId(run.getId());
```

## Scenario: Platform Container Deploy Node

### 1. Scope / Trigger

- Trigger: adding or changing the platform-native container deployment node, deployment order APIs, Kubernetes rollout execution, Jenkins callback advancement, or current-run detail links.
- Scope: pipeline node registry/validation, Jenkinsfile generation, Jenkins callback advancement, `PipelineExecutionService`, deployment-order controller/service/mapper/DO, `dev_deployment_order`, and focused pipeline/deployment service tests.

### 2. Signatures

- Node type:
  - `CONTAINER_DEPLOY`
  - Category `PLATFORM`
  - MVP params: `infraType=K8S`, `deployMode=RAW_MANIFEST`, `manifestYaml`, `containerName`, `image`, optional `replicas`, optional `rolloutTimeoutSeconds`.
- Deployment APIs:
  - `GET /devops/deployment-order/page`
  - `GET /devops/deployment-order/{id}`
  - `POST /devops/deployment-order/{id}/cancel`
  - `POST /devops/deployment-order/{id}/retry`
- DB:
  - `dev_deployment_order`
  - Business idempotency key: `(tenant_id, pipeline_run_id, node_id)`.
  - No deployment-step table in the MVP; `current_stage`, `config_json`, and `result_json` stay on the order row.
  - `result_json.schemaVersion=1.0` stores the current execution snapshot: `stage`, `summary`, `applyResults`,
    final `rollout`, `replicaSet`, `pods`, `events`, `failure`, and `capturedAt`.
- Kubernetes correlation metadata:
  - Deployment/resource annotation: `gone.devops/deployment-order-id` and `gone.devops/pipeline-run-id`.
  - Deployment Pod Template label/annotation: `gone.devops/deployment-order-id=<orderId>`.
- Current-run read model:
  - `CONTAINER_DEPLOY` node logs use `detailType = DEPLOYMENT_ORDER`.
  - `run_log.result_json.deploymentOrderId` is the lightweight link to deployment-order details.

### 3. Contracts

- Jenkinsfile generation must skip `CONTAINER_DEPLOY`; Jenkins never executes this node.
- Phase A topology allows at most one `CONTAINER_DEPLOY`, and it must be the single terminal node after Jenkins nodes.
- If Jenkins is skipped or all Jenkins nodes complete and a container deploy node exists, platform code must call `DeploymentOrderService.startContainerDeploy(...)` instead of marking the run successful.
- Deployment order creation resolves namespace only from `EnvironmentDO.infraConfig.namespace`; node params must not expose a namespace override.
- `RAW_MANIFEST` supports only `Service` resources and exactly one `Deployment`, applied in manifest document order. Other kinds and multiple Deployments remain rejected in that mode. `ORCHESTRATION` has a separate six-kind policy described below; do not widen RAW_MANIFEST's parser whitelist globally.
- Manifest `metadata.name` is the deployment workload name source of truth; execution applies the rendered manifest with `createOrReplace`, not workload patch mode.
- Deployment order snapshot must retain both the original `manifestYaml` and the rendered `renderedManifestYaml`, so retries and audit views are based on the captured execution input instead of later DSL edits.
- If node `replicas` is present, it overrides `spec.replicas` in the rendered YAML before apply.
- Deployment success requires Kubernetes Deployment rollout ready; manifest apply success alone must not mark the order, node log, or run successful.
- Retry reuses the same deployment order and increments `attempt`; it does not create another pipeline run or deployment-order row.
- Cancel marks the order/run/log canceled and stops waiting when the rollout loop observes the canceled order. It does not rollback an already-submitted Kubernetes apply.
- Store rollback prerequisites on the order when available: `previousImage`, `previousReplicas`, and `previousRevision`.
- Deployment-order detail may query Kubernetes live state, but long-lived facts and audit snapshots belong on `dev_deployment_order`.
- Every manifest document must record its own apply status in `result_json.applyResults`. A later Deployment apply failure
  must not erase an earlier Service `APPLIED` result.
- Stage transitions must merge `stage/summary` into the structured result; they must never replace the complete
  `result_json` with a stage-only map.
- Live deployment detail must distinguish persisted execution facts from current Kubernetes observations. New deployments
  correlate Pods through the deployment-order Pod Template label, then verify ReplicaSet/Pod `ownerReference` UIDs.
  Do not treat every Pod matching the application's Deployment selector as part of the current deployment order.
- During execution, Pod/container/Event observations may be queried live without persisting the full timeline. On success,
  failure, or rollout timeout, persist one bounded best-effort final snapshot. Snapshot collection failure must not replace
  the original apply/rollout error.
- Container snapshots include init and normal containers, `image/imageId/containerId`, `state/stateDetail/lastState`,
  `restartCount`, waiting reason/message, and terminated exitCode/signal/timestamps. Event snapshots are bounded and only
  include involvedObject UIDs belonging to the Deployment, target ReplicaSet, or correlated Pods.
- Event snapshots are historical evidence, not the current outcome. Use `health` for current readiness, `diagnosis` for a
  recoverable current issue, and `failure` only for final/terminal failure; a recovered Warning must remain visible without
  making a successful deployment appear failed.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| More than one `CONTAINER_DEPLOY` node | Validation error `CONTAINER_DEPLOY_DUPLICATE` |
| `CONTAINER_DEPLOY` is not the terminal node | Validation error `CONTAINER_DEPLOY_NOT_TERMINAL` |
| `infraType` is not `K8S` | Validation error `PARAM_VALUE_INVALID` |
| `deployMode` is neither `RAW_MANIFEST` nor `ORCHESTRATION` | Validation error `PARAM_VALUE_UNSUPPORTED` |
| Required deploy params are blank | Validation error `PARAM_REQUIRED` |
| In RAW_MANIFEST, `manifestYaml` is blank / invalid / includes unsupported kinds / has zero or multiple Deployments / lacks metadata.name | Deployment step fails with a deployment manifest or node-param business error |
| `containerName` is not present in manifest containers | Validation error `PARAM_VALUE_INVALID` |
| Environment is not K8S or has no kubeconfig/namespace | Throw deployment environment business error |
| Rollout timeout | Mark order/log/run failed with rollout timeout |
| Service apply succeeds and Deployment apply fails | Keep Service `APPLIED`, record Deployment `FAILED`, and fail the order with the Deployment API error |
| Current detail sees a same-name Deployment with a different UID | Return an explicit UID-mismatch state and do not associate its ReplicaSet/Pods with the order |
| Structured-result deployment has not yet applied its Pod Template label | Return `DEPLOYMENT_NOT_APPLIED`; do not fall back to old selector Pods |
| Deployment annotation points to a later deployment order | Return `SUPERSEDED` and direct callers to the persisted final snapshot; do not show later Pods as this order |
| Kubernetes live-status/Event query fails | Keep returning persisted order/result data and report live status unavailable; do not change deployment outcome |
| Pod is `ImagePullBackOff`, `CrashLoopBackOff`, unschedulable, mount-failed, or probe-unhealthy | Return container/Event details and a normalized `diagnosis`; keep `failure` absent until timeout or terminal failure |
| Cancel/retry from an invalid state | Throw `DEPLOYMENT_ORDER_STATE_INVALID` |

### 5. Good / Base / Bad Cases

- Good: Jenkins callback only records Jenkins node state, then advances to the platform deployment service when every Jenkins node is successful.
- Good: deployment config is snapshotted in `dev_deployment_order.config_json`, so later DSL edits do not change retry behavior.
- Good: current-run polling stays lightweight and links to deployment-order detail instead of embedding live Kubernetes pod lists.
- Good: Service is accepted, Deployment is rejected, and deployment detail still shows the Service resource as `APPLIED`.
- Good: live detail follows Deployment UID → target ReplicaSet ownerReference → deployment-order-labeled Pod, so old rollout Pods are excluded.
- Base: pipeline without `CONTAINER_DEPLOY` keeps the existing Jenkins-only success behavior.
- Bad: adding `CONTAINER_DEPLOY` to Jenkinsfile stages or relying on Jenkins callback for that node.
- Bad: adding a namespace node param before product requirements explicitly allow namespace override.
- Bad: persisting pod status history in the MVP deployment order when it can be queried live from Kubernetes.
- Bad: overwriting `result_json` with `{stage, summary}` on every stage transition, because partial apply facts disappear.
- Bad: querying Pods only by the workload selector, because old ReplicaSet Pods and later deployments can be misreported as this order.

### 6. Tests Required

- Registry test: `CONTAINER_DEPLOY` is configurable, has K8S/raw-manifest params, and does not include Jenkins-only params or namespace override.
- Validation tests: terminal success, non-terminal failure, duplicate failure, and param validation failure.
- Validation/deployment tests: YAML parse failure, target container missing, single-doc head `---` allowed, Service + Deployment multi-doc YAML applied in order, missing Deployment rejected, multiple Deployments rejected, unsupported resource kind rejected.
- Jenkinsfile test: generated Jenkinsfile does not contain `CONTAINER_DEPLOY`.
- Callback/execution tests: Jenkins completion or skipped Jenkins starts deployment order instead of directly marking success.
- Deployment service tests: create-order snapshot includes original/rendered YAML, image expression resolution, namespace override from environment, replicas override, cancel state transition, invalid cancel/retry state, retry attempt increment, and failed Kubernetes boundary updates order/log/run.
- Deployment result tests: Service + Deployment both persist `APPLIED`; Service success + Deployment rejection persists partial success and Deployment `FAILED`; stage updates retain `applyResults`.
- Status collector tests: deployment-order label and ownerReference filtering exclude old Pods; UID mismatch does not associate replacement resources; ImagePullBackOff and terminated `lastState` fields are returned; related Warning Event produces normalized `diagnosis`, and recovered Warnings do not pollute successful snapshots.
- Compile/test command:
  `mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dtest='DeploymentOrderServiceImplTest,PipelineNodeRegistryServiceImplTest,PipelineSpecValidationServiceImplTest,JenkinsfileGeneratorServiceImplTest,PipelineJenkinsCallbackServiceImplTest,PipelineExecutionServiceImplTest' -Dsurefire.failIfNoSpecifiedTests=false test`

### 7. Wrong vs Correct

#### Wrong

```java
// Treats platform deployment as a Jenkins stage.
appendStage(builder, containerDeployNode);
```

#### Correct

```java
if (!PipelineNodeRegistryServiceImpl.TYPE_CONTAINER_DEPLOY.equals(node.getType())) {
    appendStage(builder, node);
}
```

#### Wrong

```java
// Apply accepted is not rollout success.
applyDeploymentManifest(client, order, renderedDeployment);
markSuccess(order, renderedDeployment);
```

#### Correct

```java
Deployment renderedDeployment = kubernetesDeploymentManifestSupport.prepareDeployment(config);
applyDeploymentManifest(client, order, renderedDeployment);
Deployment readyDeployment = waitRolloutReady(client, order, config);
markSuccess(order, readyDeployment);
```

#### Wrong

```java
// Current selector state is not proof that a Pod belongs to this deployment order.
client.pods().inNamespace(order.getNamespace()).withLabels(deploymentSelector).list();
```

#### Correct

```java
// Stamp the Pod Template during apply, then verify ReplicaSet/Pod ownership when reading live status.
podTemplateLabels.put("gone.devops/deployment-order-id", String.valueOf(order.getId()));
List<Pod> pods = client.pods().inNamespace(order.getNamespace())
        .withLabels(Map.of("gone.devops/deployment-order-id", String.valueOf(order.getId())))
        .list().getItems();
pods = pods.stream().filter(pod -> isOwnedBy(pod, targetReplicaSetUid)).toList();
```

## Scenario: K8sDeploy Application Orchestration

### 1. Scope / Trigger

- Changes to K8sDeploy mode, application orchestration loading, six-kind execution, deployment snapshot or Secret presentation.
- Relevant classes: DeploymentOrderServiceImpl, ContainerDeployConfigContext, KubernetesDeploymentManifestSupport, KubernetesDeploymentStatusCollector and pipeline registry/validation.

### 2. Signatures

- `step: K8sDeploy`, `with.deployMode: ORCHESTRATION`.
- Required: `containerName`, `image`; optional: `replicas`, `rolloutTimeoutSeconds`; no manifestYaml or runsOn required.
- `ApplicationOrchestrationService.getOrchestration(run.appId, K8S_DEPLOYMENT)` returns the current application draft.
- `config_json` adds `orchestrationId`, `orchestrationUpdateTime` and an internal `variableGroupVariables` map (nulled before any response); original/rendered YAML retain their existing fields. No table migration or new handler.

### 3. Contracts

- ApplicationOrchestrationServiceImpl must depend on ApplicationMapper for application-existence lookup, not ApplicationService. The latter owns pipeline startup and would close the chain ApplicationService → async execution → engine → handler registry → K8sDeploy → deployment → orchestration → ApplicationService. Do not conceal the cycle using @Lazy or allowing raw Bean injection.
- Load once when first creating the deployment order; retries reuse persisted config rather than rereading mutable draft.
- Only enabled components are submitted, sorted by applyOrder; supported kinds are Secret, ConfigMap, PersistentVolumeClaim, Service, Deployment, networking/v1 Ingress. Exactly one enabled Deployment is mandatory.
- Validate dependency order/enabled targets, component kind vs YAML, single-resource documents, names and unique resource identities before writes. Unknown kinds cannot be silently skipped.
- Environment namespace overrides all YAML namespaces. image and optional replicas override only the target Deployment. Deployment variable rendering merges variable groups (`GLOBAL` → `APPLICATION` → application-env binding) first, then the 8 built-in runtime variables overwrite same-named keys; only `ORCHESTRATION` reads variable groups, `RAW_MANIFEST` keeps fixed-variable-only rendering and leaves `${VAR}` untouched.
- Variable-group values are plain text rendered into YAML with a single-pass literal `replace`, no recursion. Sensitive-keyed values (key containing `password`/`secret`/`token`/`credential`/`passwd`/`pwd`) are masked as `******` in response and context; masking runs after `redactSecrets`, and the masked text is never handed back to a YAML parser.
- ORCHESTRATION gates Deployment/ReplicaSet/Pods/containers only; auxiliary resources including Services are apply-only. Both runtime health and later detail collection must skip Service Endpoints, or detail can contradict a successful run.
- Retain raw Secret in the internal snapshot for retry; redact data/stringData and Secret annotations in pipeline context and deployment detail. Do not expose parser diagnostics or apply exception payloads containing Secret contents.
- RAW_MANIFEST keeps its Service/Deployment-only policy and Service health checks; missing config in historical deployment detail remains readable.
- No transactional apply, rollback, deletion of removed resources or version publishing.

### 4. Validation & Error Matrix

| Condition | Behavior |
|---|---|
| ORCHESTRATION without manifestYaml | Pass DSL validation if image/containerName exist |
| Missing/empty draft, no enabled components, disabled dependency, duplicate identity | Fail before Kubernetes client writes with DEPLOYMENT_NODE_PARAM_INVALID |
| Zero/multiple enabled Deployments or wrong containerName | Fail before apply with deployment business error |
| Malformed/unsupported YAML | Fail without echoing YAML or sensitive parser diagnostics |
| Later apply fails | Preserve earlier APPLIED entries; stop remaining writes and mask request payload |
| Draft changes before retry | Retry original captured YAML and retain source metadata |
| Secret-bearing order shown in page/detail | No data/stringData values; internal config is not mutated |
| Variable-group key collides with a built-in variable | Built-in value wins; no error |
| Sensitive-keyed variable-group value shown in page/detail/context | Masked as `******`; internal config snapshot keeps plaintext for retry |
| Raw `variableGroupVariables` on returned config | Must be null; never expose the key/value map |
| `RAW_MANIFEST` manifest containing `${VAR}` | Placeholder stays verbatim; variable groups are not read |

### 5. Good / Base / Bad Cases

- Good: ordered Secret/ConfigMap/PVC/Service/Deployment/Ingress apply with a single Deployment health gate.
- Base: existing RAW_MANIFEST Service/Deployment pipelines remain unchanged.
- Bad: widen the default parser to all kinds and accidentally change RAW_MANIFEST compatibility.
- Bad: collect live Services from applyResults and reintroduce Endpoints gating in ORCHESTRATION detail.

### 6. Tests Required

- Registry mode/default and conditional manifestYaml requirement.
- Six kinds in applyOrder, disabled filtering, environment namespace, image/replicas override.
- Missing/empty/invalid draft, disabled dependencies, multiple/zero Deployment and duplicate identity before cluster writes.
- Retry retains raw/rendered snapshot and calls orchestration service only once.
- Secret absent in response/context/error; partial apply failures retained; live collector skips Service reads in ORCHESTRATION.
- Variable-group values substitute `${VAR}` in orchestration components and the merged manifest; built-in variables win over same-named group keys; `RAW_MANIFEST` never reads variable groups.
- Sensitive-keyed variable-group values are masked in response/context while non-sensitive values stay visible; `order.config_json` keeps the rendered plaintext.
- `SensitiveKeyUtilsTest` plus `DeploymentOrderServiceImplTest` and `KubernetesDeploymentManifestSupportTest` stay green; `OrchestrationBeanWiringTest` registers the real `VariableGroupServiceImpl` in the chain.
- Existing DeploymentOrderServiceImplTest, KubernetesDeploymentManifestSupportTest and K8sDeployStepHandlerTest remain green.
- OrchestrationBeanWiringTest must register the real application/execution/registry/handler/deployment/orchestration chain, prohibit circular references and retain cache/async proxies. Test both application-first and deployment-first initialization; Mockito @InjectMocks alone cannot detect Bean dependency cycles.

### 7. Wrong vs Correct

Wrong: `parseSupportedResources(yaml)` accepts all kinds regardless of deploy mode.

Correct: `parseSupportedResources(yaml, config.getDeployMode())` selects the explicit mode policy; the single-argument overload keeps RAW_MANIFEST.

## Scenario: Pipeline Builder Image

### 1. Scope / Trigger

- Trigger: adding or changing the reusable Docker image used by YAML `runsOn.container` for `local-docker/default` build jobs.
- Scope: `yudao-module-devops/docker/pipeline-builder/Dockerfile`, its README, pipeline YAML examples, and Docker image verification commands.

### 2. Signatures

- Dockerfile path: `yudao-module-devops/docker/pipeline-builder/Dockerfile`.
- Default image tag: `gone-cloud/pipeline-builder:java17-node24-maven3.9`.
- Base image: `alibaba-cloud-linux-3-registry.cn-hangzhou.cr.aliyuncs.com/alinux3/alinux3:220901.1`.
- Default tools:
  - JDK 17 from alinux `java-17-openjdk-devel`.
  - Apache Maven 3.9.9 under `/opt/maven`.
  - Node.js 24.16.0 under `/usr/local`, with npm and corepack.
- Build command:
  `docker build -t gone-cloud/pipeline-builder:java17-node24-maven3.9 yudao-module-devops/docker/pipeline-builder`
- YAML usage:
  `runsOn.group=local-docker/default`, `runsOn.container=gone-cloud/pipeline-builder:java17-node24-maven3.9`.

### 3. Contracts

- The image must keep `WORKDIR /workspace`, because `DockerPipelineCommandExecutor` executes commands there.
- The image `CMD` should remain a long-running shell loop, because `DockerPipelineJobRuntimeManager` starts the container first and later runs step commands with Docker exec.
- Downloads for Node and Maven must complete during build; Node and Maven must be checksum-verified.
- Use `dnf install --setopt=install_weak_deps=False` for base packages to avoid pulling unnecessary runtime packages.
- Do not expose Docker socket, privileged mode, custom host volumes, or custom network settings as YAML controls.
- Keep the image focused on build tools. Application service runtime images stay in each server module's own Dockerfile.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| Docker build cannot download alinux/Node/Maven artifacts | Build fails; fix registry/network/version before publishing the tag. |
| Node checksum does not match `SHASUMS256.txt` | Build fails during `sha256sum -c`. |
| Maven checksum does not match `MAVEN_SHA512` | Build fails during `sha512sum -c`. |
| Runtime `pwd` is not `/workspace` | Fix Dockerfile before using the image in YAML. |
| Required tool command is missing | Fix Dockerfile and rebuild the same tag or publish a new explicit tag. |

### 5. Good / Base / Bad Cases

- Good: build the image once on the Docker host used by the DevOps backend, then reference its explicit tag in pipeline YAML.
- Good: update the README and `PIPELINE_YAML_SPEC.md` examples when the default image tag changes.
- Base: operators may retag/push the image to a private registry as long as YAML references the pushed tag and the Docker daemon can pull it.
- Bad: using raw alinux in YAML and installing Maven/Node inside every build step.
- Bad: exposing `/var/run/docker.sock`, privileged mode, custom host volumes, or custom network mode as user-configurable YAML fields.

### 6. Tests Required

- `docker build -t gone-cloud/pipeline-builder:java17-node24-maven3.9 yudao-module-devops/docker/pipeline-builder`
- `docker run --rm gone-cloud/pipeline-builder:java17-node24-maven3.9 sh -lc 'pwd && java -version && javac -version && mvn -version && node -v && npm -v && corepack --version && git --version'`
- `git diff --check`

### 7. Wrong vs Correct

#### Wrong

```yaml
runsOn:
  group: local-docker/default
  container: alibaba-cloud-linux-3-registry.cn-hangzhou.cr.aliyuncs.com/alinux3/alinux3:220901.1
steps:
  build_step:
    with:
      run: |
        yum install -y maven
        mvn -B clean package
```

#### Correct

```yaml
runsOn:
  group: local-docker/default
  container: gone-cloud/pipeline-builder:java17-node24-maven3.9
steps:
  build_step:
    with:
      run: |
        mvn -B clean package
```

## Scenario: Pipeline YAML Variable Resolver

### 1. Scope / Trigger

- Trigger: adding or changing `${VAR}` substitution in pipeline YAML `with` parameters, step outputs, or handler parameter parsing.
- Scope: `PipelineVariableResolver`, `PipelineExecutionContext#getResolvedWith()`, handler code that reads step `with`, `PipelineSpecValidationServiceImpl`, and `PIPELINE_YAML_SPEC.md`.

### 2. Signatures

- Resolver class: `cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineVariableResolver`.
- Handler entrypoints:
  - `PipelineExecutionContext#getResolvedWith()` returns a resolved `Map<String, Object>`.
  - `PipelineExecutionContext#getResolvedStep()` returns a resolved `PipelineSpec.ExecutableStep` for services that accept the whole step.
- Placeholder syntax: simple `${VAR}` only.

### 3. Contracts

- Resolver must recurse through `with` strings, nested maps, and lists.
- Variables come from run metadata, execution context system state, current stage/job/step metadata, and completed upstream `StepResult.outputs` restored into the run output registry.
- Variable keys must support both original key and env-style uppercase underscore key, for example `${commitSha}` and `${COMMIT_SHA}`.
- Unknown placeholders remain unchanged so downstream domain-specific renderers, such as Kubernetes deployment rendering, can still process their own variables.
- Handlers must read `ctx.getResolvedWith()` instead of `ctx.getStep().getWith()` when consuming YAML parameters.
- Services that receive the whole executable step, such as deployment order creation, must receive `ctx.getResolvedStep()`.
- Static validation may allow placeholder values in enum, boolean, integer, and path-like fields, but handlers must validate the rendered value before executing.
- Credential values may be resolved from variables, but must not be written to logs, `contextJson`, `resultJson`, or step outputs.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| YAML value is `registry/ns/app:${COMMIT_SHA}` and `commitSha=abc` | Handler receives `registry/ns/app:abc`. |
| YAML value contains unknown `${NAMESPACE}` before Kubernetes deploy service | Placeholder remains for deployment renderer. |
| Boolean field is `${TLS_VERIFY}` and resolves to `false` | Handler parses it as boolean false. |
| Boolean field is unresolved `${TLS_VERIFY}` | Handler keeps its field default rather than treating it as false. |
| Enum field is `${ARCHIVE_FORMAT}` at save time | Static validation allows it; runtime handler validates the rendered enum. |
| Step output contains credential material | Do not merge/write it as a normal output; mask or reject in handler-specific code. |

### 5. Good / Base / Bad Cases

- Good: one shared resolver handles `Command`, `CodeMerge`, `PrivateRegistryDockerBuild`, `DockerImageExportObjectStorage`, and deployment step handoff.
- Base: `${VAR}` is absent; `getResolvedWith()` returns values equivalent to the original `with` map.
- Bad: a handler performs ad hoc `.replace("${COMMIT_SHA}", ...)`, because behavior will drift from validation and other step types.
- Bad: resolving unknown placeholders to an empty string, because it breaks downstream renderers and hides missing variables.

### 6. Tests Required

- Resolver unit test for nested map/list recursion and camelCase/env-style aliases.
- Handler tests that assert command scripts, env maps, Docker build args, and image-export env values receive rendered strings.
- Validation tests that assert placeholder enum/boolean/path values can be saved while concrete invalid values are still rejected.
- Focused command:
  `mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dtest='PipelineSpecValidationServiceImplTest,PipelineVariableResolverTest,CommandStepHandlerTest,DockerImageExportObjectStorageStepHandlerTest,PrivateRegistryDockerBuildStepHandlerTest' -Dsurefire.failIfNoSpecifiedTests=false test`

### 7. Wrong vs Correct

#### Wrong

```java
String image = String.valueOf(ctx.getStep().getWith().get("image"));
```

#### Correct

```java
String image = String.valueOf(ctx.getResolvedWith().get("image"));
```

#### Wrong

```java
text = text.replace("${COMMIT_SHA}", ctx.getRun().getCommitSha());
```

#### Correct

```java
Map<String, Object> with = ctx.getResolvedWith();
```

## Scenario: Pipeline Workspace Concurrency Control

### 1. Scope / Trigger

Any time you serialize access to a pipeline runtime resource (workspace dirs, cache dirs,
metadata files). Applies to `LocalPipelineWorkspaceService` and similar runtime services.

### 2. Decision Rule — 分布式锁 vs `synchronized`

先判断「被保护的资源是否跨节点共享」，再决定用哪种锁：

- **资源跨节点共享 → 分布式锁（Redisson）。** 例如工作目录路径可能落在共享存储（NFS/EFS），
  多节点对同一路径的 create/reset 必须互斥。进程内 `synchronized` 在多节点部署下完全失效。
- **资源是节点本地对象 → 保持 `synchronized`。** 例如 `SseEmitter`、`WebSocketSession`、
  单 run 的进程内 `Map`（`sharedState`）。它们物理上只存在于本节点，换分布式锁既加 Redis 往返开销，
  又解决不了「本 JVM 内并发访问本地对象」这一真实竞争，甚至制造「跨节点已安全」的假象。

判据一句话：**分布式锁只在「被保护的资源本身跨节点共享」时才值得它的成本。**

### 3. Contracts

- 编程式 Redisson（不引入 `@Lock4j` / `yudao-spring-boot-starter-protection`）：`RedissonClient`
  已随 `yudao-spring-boot-starter-redis` 在 devops 类路径上，新增锁不应再加 Maven 依赖。
- 锁 key 放 `RedisKeyConstants`，格式 `devops:xxx_lock:%s`，注释写明 VALUE 为 Redisson RLock(HASH)
  与 lease 兜底。
- 锁 key 复用「能唯一标识被保护资源」的现成派生值（如工作目录路径），保证与原 `synchronized` 语义逐字等价，
  不要另造一套 key 派生逻辑。
- 写法对齐 `ImRtcCallLockRedisDAO`：`tryLock(waitMs, leaseMs, MILLISECONDS)` → 失败抛业务/状态异常 →
  `finally` 中 `isHeldByCurrentThread()` 才 `unlock()`；`InterruptedException` 要 `Thread.currentThread().interrupt()` 复位再抛。
- 必须带 `leaseMs` 兜底，避免持锁节点宕机导致死锁。

### 4. Behavior Diff to Document

进程锁是「无限等待」，分布式锁改为「`waitMs` 上限等待 + 超时抛异常」。这是避免跨节点不可见死等的必要取舍，
要在方法注释里写明，并保持对外异常类型不变（如统一收敛为 `IllegalStateException`）。

### 5. Tests Required

- 用 mock `RedissonClient`/`RLock`（`tryLock(...)` 与 `isHeldByCurrentThread()` 返回 true），
  经 `ReflectionTestUtils.setField` 注入，验证锁对原文件系统逻辑透明、原有用例结果不变。
- 锁的跨进程互斥依赖 Redisson 本身，不在单测内做跨进程验证，靠评审确认 tryLock/unlock 配对。

### 6. Wrong vs Correct

#### Wrong

```java
// 给节点本地的 SseEmitter / WebSocketSession / 进程内 Map 套分布式锁
RLock lock = redissonClient.getLock("devops:emitter_lock:" + runId);
```

#### Correct

```java
// 节点本地对象保持进程锁
synchronized (emitter) { emitter.send(...); }

// 仅对跨节点共享资源（工作目录路径）用分布式锁
RLock lock = redissonClient.getLock(String.format(PIPELINE_WORKSPACE_LOCK, workspace.toString()));
```

## Scenario: Pipeline Job Terminal Plugins

### 1. Scope / Trigger

- Trigger: adding or changing `stages.<stageId>.jobs.<jobId>.plugins`, plugin SPI implementations, Job terminal-state plugin dispatch, or external notification plugins.
- Scope: `PipelineSpec`, `PipelineSpecValidationServiceImpl`, `PipelineExecutionEngine`, `service/pipeline/plugin`, DevOps async executor configuration, `PIPELINE_YAML_SPEC.md`, and focused plugin/pipeline tests.

### 2. Signatures

- Job DSL:
  - `plugins[].name: String`
  - `plugins[].plugin: String`
  - `plugins[].triggerState: List<success|fail>`
  - `plugins[].with: Map<String, Object>`
- SPI:

```java
public interface PipelinePlugin {
    String type();
    List<PipelinePluginValidationIssue> validate(Map<String, Object> config);
    void execute(PipelinePluginExecutionContext context, Map<String, Object> config);
}
```

- First implementation: `plugin: DingTalkPlugin` with `with.webhook`, optional `secret`, required `noticeContent`, optional literal `customContent`, and optional scalar/list `at`.
- Async bean: `pipelinePluginExecutor`, bounded queue, `AbortPolicy`, thread prefix `pipeline-plugin-`.

### 3. Contracts

- Keep plugins at Job level. They observe a persisted Job terminal transition; they are not steps and must not consume a Docker runtime, alter DAG topology, or appear in step run logs.
- `success` maps only to Job `SUCCESS`; `fail` maps only to `FAILED`. `PENDING/RUNNING/BLOCKED/SKIPPED/CANCELED` never trigger plugins.
- Persist Job status before dispatch. `PipelinePluginDispatcher#dispatch` is no-throw and submits an immutable event snapshot; the Pipeline scheduler never waits for plugin HTTP completion.
- Use a dedicated executor with `AbortPolicy`. Never use `CallerRunsPolicy` for external plugins because queue rejection would move HTTP work back onto the Pipeline thread.
- Restore tenant context explicitly inside the async task with `TenantUtils.execute(event.tenantId, ...)` before tenant-scoped mapper or RPC calls.
- Resolve Pipeline name and operator display name in the async task; missing definition/user or RPC failure falls back to ids and does not abort remaining plugins.
- Execute matching plugins in YAML order and isolate each exception so one plugin cannot prevent later plugins.
- First-phase delivery is fire-and-forget: no execution table, retry, durable outbox, or restart compensation.
- Plugin context exposes display-safe run/stage/job/status metadata only. Do not pass mutable `PipelineExecutionContext`, shared state, workspace, step outputs, or credential material to plugins.
- DingTalk uses `text`; `noticeContent` is a fixed whitelist, `customContent` is literal, and scalar/list `at` normalizes to ordered `atMobiles`.
- `webhook/access_token/secret`, request URL/body, and raw upstream response must never enter logs, exceptions, run context, `resultJson`, or plugin results. Signed URLs must use structured URI query handling.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| Job omits `plugins` | Parse as an empty list; existing execution behavior is unchanged |
| Plugin name/type/triggerState missing | DSL validation error on the indexed plugin field |
| Plugin type is not registered | `PLUGIN_NOT_SUPPORTED` |
| triggerState is duplicated or outside `success/fail` | `PLUGIN_TRIGGER_STATE_DUPLICATE` or `PLUGIN_TRIGGER_STATE_UNSUPPORTED` |
| DingTalk webhook is blank, non-HTTPS, or not an official robot endpoint | Plugin parameter validation error without echoing the value |
| DingTalk noticeContent is blank, duplicated, or outside the whitelist | Plugin parameter validation error on `with.noticeContent` |
| DingTalk `at` is neither a phone string nor a string list, or contains invalid/duplicate phones | Plugin parameter validation error on `with.at` |
| Job becomes `SKIPPED` or `CANCELED` | Do not submit a plugin event |
| Executor queue rejects the event | Log a safe warning and continue Pipeline scheduling |
| Plugin throws, times out, or returns an upstream business error | Log safe run/stage/job/plugin identifiers; keep Job/Pipeline status unchanged and continue later plugins |
| Service restarts after dispatch | Notification may be lost; no compensation in the first phase |

### 5. Good / Base / Bad Cases

- Good: Job `SUCCESS` is persisted, Dispatcher submits one event, Pipeline schedules downstream Jobs immediately, and DingTalk sends asynchronously under the run tenant.
- Good: first matching plugin fails and the next matching plugin still executes; neither changes the persisted Job/Pipeline result.
- Base: old YAML contains no plugins and follows the same parse, publish, DAG, approval resume, failFast, and cancel behavior.
- Bad: represent DingTalk as a normal step, because a notification outage would fail or block the business DAG and may allocate an unnecessary Job runtime.
- Bad: log the exception stack/message from a generic external plugin when it may contain a tokenized URL or request body.
- Bad: use `CallerRunsPolicy`, join a plugin Future, or perform operator RPC / webhook HTTP synchronously before the Pipeline scheduler continues.

### 6. Tests Required

- DSL validation tests: old YAML compatibility, plugin round-trip/order, unsupported type, missing/duplicate/unsupported trigger states, and DingTalk webhook/secret/noticeContent/at validation with indexed fields.
- Registry test: a second test plugin resolves without Dispatcher/Engine branches; duplicate type fails startup.
- Dispatcher tests: `SUCCESS/FAILED` mapping, non-trigger states, YAML order, explicit tenant context, definition/user fallback, per-plugin exception isolation, and executor rejection no-throw.
- DingTalk tests: text field selection/order, literal `${VAR}` custom content, scalar/list at normalization, unsigned URL, deterministic HMAC-SHA256 signed URL, and no sensitive values in validation/errors.
- Engine tests: dispatch occurs only after `SUCCESS/FAILED` updates; dependency skip/cancel does not dispatch; downstream DAG state is unchanged when plugin dispatch fails.
- Run module compile plus `*Pipeline*Test,*DingTalk*Test`, and run `git diff --check` plus a secret/log residual search.

### 7. Wrong vs Correct

#### Wrong

```java
try {
    dingTalkClient.send(config.getWebhook(), buildBody(job));
    markJobSuccess(jobRun);
} catch (Exception ex) {
    markJobFailed(jobRun, ex.getMessage());
}
```

#### Correct

```java
pipelineRunJobMapper.updateById(successJobRun);
pipelinePluginDispatcher.dispatch(jobContext, PipelineRunJobStatusEnum.SUCCESS.getStatus());
// Dispatcher submits to pipelinePluginExecutor and never changes Job/Pipeline state.
```
## Scenario: DevOps Variable Groups

- Variable groups are tenant-scoped and may target `GLOBAL`, `APPLICATION`, or `APPLICATION_ENV`.
- Store source text as one `KEY=VALUE` per line. Parse on write; trim keys, preserve value text after the first `=`, ignore blank lines, and reject malformed or duplicate keys with line-aware errors.
- Runtime merge order is global, application, then application-environment; later scopes override earlier values. Insert built-in run metadata after groups so reserved runtime variables cannot be shadowed.
- Do not log or persist merged variable values in pipeline context/results.

## Scenario: Build Context Permissions

### 1. Scope / Trigger

- Trigger: changing `umask` handling in the docker-java image build path, `BuildContextPermissions`, `docker/pipeline-builder/*.sh`, or anything that decides the permission bits of files that end up inside a built image.
- Scope: `PrivateRegistryDockerBuildStepHandler`, `BuildContextPermissions`, `ShellQuoting`, `docker/pipeline-builder/*.sh`, and the handler tests that assert the generated build script.

### 2. Signatures

- `BuildContextPermissions.normalize(Path context)` — normalizes a host-side build context to `chmod -R a+rX` semantics.
- `BuildContextPermissions.normalizeCommand(String context, boolean busybox)` — returns the equivalent shell line; `busybox=true` emits `/busybox/chmod`. Kept for callers that execute inside a container.

### 3. Contracts

- **`umask` must never be set for the lifetime of a step process.** Restrict it to a subshell that only creates the secret-bearing file: `(umask 077; ... > "$f")`. A leading `umask 077` is inherited by every child of the step, so files and directories produced by `CodeMerge`, `Command`, or a Dockerfile `RUN` become `0600`/`0700`.
- Docker builds preserve the mode bits of the build context. `0600` files copied into an image stay `0600`, and a non-root runtime user (for example `nginx` `uid=101`) cannot read them, so static assets return `403`. A `0700` directory is worse: the user cannot even traverse it.
- Normalize the build context immediately before every image build, through `BuildContextPermissions.normalize(...)` in `PrivateRegistryDockerBuildStepHandler`. The application-environment workspace is reused across runs and `git checkout -f` does not repair mode bits of already-tracked files, so a one-off fix elsewhere does not heal existing workspaces.
- `a+rX` is the only accepted target mode: add read for all, and add execute only for directories or files that already have an execute bit. Never blanket-apply `a+x`, which would make every file in the image executable.
- Normalization is best-effort and must not fail a build. The shell form ends with `|| echo 'WARN: ...'` and the Java form swallows per-entry `IOException`/`UnsupportedOperationException`, because a context may contain FIFOs or be on a non-POSIX filesystem.
- Registry credentials stay protected: the skopeo `containers-auth.json` must remain `0600`. Scope `umask 077` around that write only; `mktemp -d` already yields `0700`, so the containing directory is a second layer.
- Do not add a global `umask` line to `docker/pipeline-builder/*.sh`. It also downgrades the exported image archive to `0600`.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| Step script contains a leading `umask 077` | Regression. Files created by the step become `0600` and the image serves `403`. |
| Build context still has a `0600` file after normalization | Normalization is broken; verify the context path and that the process can chmod it. |
| Context entry cannot be chmod-ed | Emit the WARN line and continue the build. |
| Credential file is not `0600` | Regression in secret handling; re-scope the `umask` subshell. |

### 5. Good / Base / Bad Cases

- Good: `(umask 077; printf '%s' "$AUTH" > "$auth_dir/config.json")` inside a `mktemp -d` directory, with the build context normalized separately.
- Base: `BuildContextPermissions.normalizeCommand("./", true)` emits `/busybox/chmod -R a+rX -- './' || echo 'WARN: ...'`.
- Bad: `umask 077; mkdir -p /tmp; ... ; sh "$f"`.
- Bad: relying on the exec wrapper alone and skipping context normalization, which leaves already-built workspaces broken.

### 6. Tests Required

- `BuildContextPermissionsTest` — `700`/`600` become `755`/`644`, a non-executable file does not gain `o+x`, an executable file keeps it, missing/`null` contexts are no-ops, and `normalizeCommand` picks the right `chmod` flavour and quotes the path.
- `PrivateRegistryDockerBuildStepHandlerTest` — the build path normalizes the context before invoking docker-java.

### 7. Wrong vs Correct

#### Wrong

```java
String wrapper = "umask 077; mkdir -p /tmp 2>/dev/null || true; f=" + ShellQuoting.quote(path)
        + "; trap 'rm -f \"$f\"' EXIT; head -c " + payload.length + " > \"$f\" && sh \"$f\"";
```

#### Correct

```java
String wrapper = "mkdir -p /tmp 2>/dev/null || true; f=" + ShellQuoting.quote(path)
        + "; trap 'rm -f \"$f\"' EXIT; (umask 077; head -c " + payload.length + " > \"$f\") && sh \"$f\"";
```

```java
// Normalize the host-side context before every docker-java build.
BuildContextPermissions.normalize(context);
```

## Scenario: Pipeline Execution Backend

### 1. Scope / Trigger

- Trigger: adding or changing the backend that executes `JOB_RUNTIME` pipeline steps, `runsOn.group` semantics, or `PipelineJobRuntimeManager` implementations.
- Scope: `PipelineJobRuntimeManager`, `DockerPipelineJobRuntimeManager`, `DockerPipelineCommandExecutor`, `PipelineExecutionEngine` runtime selection, `PipelineSpecValidationServiceImpl#validateRunsOn`, and focused runtime/engine tests.

### 2. Signatures

- `PipelineJobRuntimeManager` exposes exactly two methods: `createRuntime(run, job, workspace)` and `destroyRuntime(runtime)`.
- `runsOn.group` accepts only `local-docker/default`. `runsOn.container` is required for any job that needs a runtime.
- `PipelineRunJobDO.runtimeType` describes the Docker job runtime; platform steps normally record `runtime_type=PLATFORM` on their run logs, while SSH Command records `runtime_type=SSH` with host id/name and resolved remote workspace.

### 3. Contracts

- **There is exactly one `JOB_RUNTIME` execution backend: the local Docker daemon of the host running the platform service** (`yudao.devops.docker.host`). Do not reintroduce a cluster-backed execution path, a runtime-manager factory, or a `devops.config.build` block. A Kubernetes build cluster was implemented and then deliberately reverted; reintroducing it must be a new task that keeps a single backend per deployment rather than a runtime routing layer.
- Because the backend is fixed, `PipelineExecutionEngine` injects `PipelineJobRuntimeManager` directly. There is no `default-executor` switch and no `kubernetes/...` group.
- `JOB_RUNTIME` steps run inside a container created by `DockerPipelineJobRuntimeManager`; `PLATFORM` steps must not create or hold a container. Most (including `CodeMerge`) run in the platform process against the host workspace; SSH Command is orchestrated by the platform but runs on its validated environment host.
- `CodeMerge` has one code path. It must not branch on runtime type, and conflict reads/writes stay in the platform process through `GitWorkspaceService`.
- Source preparation is always platform-side through `GitlabPipelineSourceWorkspacePreparer` and `GitCommandExecutor`. There is no `Checkout` step type: source checkout happens once per run workspace before the first source-requiring step.
- Image building goes through `PrivateRegistryDockerBuild`, which uses the platform `DockerClientFactory` / docker-java client. There is no in-cluster Kaniko build step.
- Do not keep a step type registered without an executable handler. If a step type is retired, remove it from `PipelineNodeRegistryServiceImpl` and from `isSupportedStep` so definitions referencing it fail validation with an unsupported-step error instead of silently doing nothing.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| `runsOn.group` is blank | Validation error `RUNS_ON_GROUP_REQUIRED` |
| `runsOn.group` is not `local-docker/default` | Validation error `RUNS_ON_GROUP_UNSUPPORTED` |
| `runsOn.container` is blank for a runtime-requiring job | Validation error `RUNS_ON_CONTAINER_REQUIRED` |
| Definition references a retired step type | Validation error `STEP_TYPE_UNSUPPORTED` |
| Job contains only `PLATFORM` steps | No container is created |

### 5. Good / Base / Bad Cases

- Good: a build job declares `runsOn.group: local-docker/default` and `runsOn.container: gone-cloud/pipeline-builder:...`; `Command` steps execute in that container while `CodeMerge` runs in the platform process.
- Base: a job with only `APPROVAL` / `K8sDeploy` steps never creates a container.
- Bad: reintroducing a `kubernetes/...` group or a runtime-manager factory to select between backends, which makes it ambiguous where a build actually ran.

### 6. Tests Required

- `PipelineSpecValidationServiceImplTest` — a non-`local-docker/default` group is rejected and a retired step type reports `STEP_TYPE_UNSUPPORTED`.
- `PipelineExecutionEngineTest` — `PLATFORM`-only jobs create no runtime; a `JOB_RUNTIME` step lazy-creates one; a canceled run destroys the runtime without overwriting the run/job status with failure.
- `GitlabPipelineSourceWorkspacePreparerTest` — source preparation issues the expected `git init` / `remote add` / `fetch --depth 1` / `checkout` sequence with credentials only in the remote URL.
- Run the DevOps server module test suite after any change to the runtime interfaces.

### 7. Wrong vs Correct

#### Wrong

```java
if (group.startsWith("kubernetes/")) {
    return k8sManager;
}
return dockerManager;
```

#### Correct

```java
@Resource
private PipelineJobRuntimeManager pipelineJobRuntimeManager;
```


## Scenario: SSH Command on an Environment Host

### Scope and API

- `Command.with.sshHostId` is an optional static positive host record id. Without it the existing Docker path, YAML, cache defaults and runtime lifecycle remain unchanged. SSH `with` accepts only `run`, `env`, `sshHostId`; inline credentials, host/user overrides and arbitrary workspace overrides are rejected.
- `PipelineSshTarget` owns parameter semantics. `PipelineSshTargetResolver.resolveHost(PipelineRunDO run, long hostId)` validates the current non-ignored tenant against run, application-environment and host records, app identity, matching environment id, and HOST infra. Existing pipeline authorization remains the entry boundary; no arbitrary host-command API is introduced.
- `PipelineStepHandler.runtimeRequirement(PipelineExecutionContext ctx)` defaults to the no-arg requirement; Command returns PLATFORM for SSH so SSH-only jobs do not create Docker or require runsOn. Mixed jobs still require Docker runsOn. Source preparation is unchanged and mandatory for SSH.
- `SshPipelineCommandExecutor.exec(PipelineCommandContext ctx, String script, LogSink sink)` / `cancel(String runId)` use existing HostSshClient credentials and LogSink/file-backed live logs, not the collection-oriented HostCommandExecutor. SSH contexts exclude host credentials, env and probe token from toString. Connection errors never include script, env or JSch diagnostic secrets.

### Workspace contract

- `yudao.devops.pipeline.ssh.hosts.<hostId>.local-root/remote-root` is an administrator-only shared-filesystem mapping. Both roots are absolute non-root directories; the platform workspace must be under localRoot. Resolve remoteRoot plus the relative current source workspace. No implicit `/workspace` or same-absolute-path assumption, no remote checkout/sync, and no fallback home directory.
- Create a random workspace probe locally and verify its token after remote cd, before running user code. Missing/mismatched shared storage fails closed with exit 125. Delete the probe in finally. A standalone remote builder without shared storage is intentionally unsupported.
- SSH uses the remote login user's JDK/Maven/Node and cache defaults; do not inject Docker `/root/...` env or mount versioned Docker caches on SSH. Administrator must configure compatible shared filesystem permissions.

### Lifecycle, failures and risk

- Linux bash/setsid are required; no PTY, script stdin is /dev/null, env keys are shell identifiers and values/workdir are shell-quoted. Preserve stdout/stderr stream labels, final partial lines and remote exit code. Bound log fragments and reuse existing log caps.
- Supervisor owns a separate process group, a hard deadline and a stdin heartbeat lease (10 seconds). EOF/cancel/interruption/disconnect revokes lease; cleanup TERM then KILL after 1 second. Deliberate daemonization/session escape is unsupported; use deployment services for long-lived workloads.
- Cancellation is durable run state, not only an in-memory executor notification. The engine rechecks cancellation after source/runtime preparation and before dispatch. SSH Command supplies a run-state callback to the executor; check it before opening a session, after connection setup, before channel connect, while waiting and before accepting the exit status. A cancel received before executor registration must not be lost; do not grant the remote heartbeat lease after cancellation. Canceled SSH step logs remain CANCELED rather than being overwritten with FAILED.
- Execution returns 124 timeout, 130 canceled/interrupted, 125 workspace/prerequisite failure, -1 sanitized connection/execution failure, otherwise the script's exit code. Preserve caller interrupt status. The existing job timeout/cancel engine remains authoritative for run/job aggregate status.
- Existing HostSshClient uses StrictHostKeyChecking=no and the encrypted host model has no trusted key/fingerprint field. Preserve this compatibility only for explicitly configured environment hosts; this is a documented MITM risk, not host identity verification. Use trusted network and least-privilege build accounts. A known-host model/policy rollout is a separate change.
- YAML run/env remains trusted executable input; scripts can print secrets or access everything available to their remote OS account. Do not claim this feature is an OS sandbox.

### Required focused tests

- `PipelineSpecValidationServiceImplTest`: SSH-only YAML without runsOn; mixed Docker requires runsOn; nonstatic/nonpositive host id, inline credentials/directory overrides rejected.
- `PipelineSshTargetResolverTest`: missing/ignored/mismatched tenant, cross-env host, invalid app relation/HOST infra, mapping absence/root/traversal rejected.
- `CommandStepHandlerTest` and `PipelineExecutionEngineTest`: SSH routing, workspace probe cleanup, runtime selection without Docker; cancellation during source preparation prevents dispatch, cancellation before SSH registration prevents target resolution/execution, and canceled logs are not marked FAILED. Existing Docker logs/env/cancel behavior retained.
- `SshPipelineCommandExecutorTest`: mock JSch streams, exit/failure/timeout/cancel/interrupt, durable cancellation before registration/after session open/during channel connect; execute generated supervisor locally to verify quoting, workspace mismatch and process-tree cleanup without real SSH/Docker. Local supervisor tests require bash and python3 (test-only setsid shim for macOS).
- YAML and administrator configuration examples: `yudao-module-devops/PIPELINE_YAML_SPEC.md`, `yudao-module-devops/examples/ssh-host-build-pipeline.yaml`.
