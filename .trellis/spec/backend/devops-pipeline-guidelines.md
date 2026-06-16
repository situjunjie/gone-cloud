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
- Job retry is job-level, not single-step retry. A retry increments `attempt`, creates a fresh runtime/workspace, starts from the first step, and does not rerun already successful upstream jobs.
- Resuming a `BLOCKED` job re-enters the suspended step through an idempotent handler; it must not hold a Docker runtime or executor permit while blocked.
- Canceling a run cancels `PENDING/BLOCKED/RUNNING` jobs, interrupts running commands, destroys runtime, and calls platform-step cancellation hooks where applicable.
- First implementation defaults: `failStrategy` is fail-fast only, `retryTimes` defaults to 0, job timeout defaults to 1800 seconds, and `runsOn.group` supports config-driven local Docker only through `local-docker/default`.
- First implementation must support multiple `PipelineRun` instances building/deploying concurrently, with all ready jobs sharing executor-group concurrency limits.
- First implementation assumes a single backend service scheduler. Backend service multi-replica scheduling is a later enhancement and must use DB/Redis coordination for job claiming, executor permits, and lease expiry.
- An application environment has only one effective pipeline definition at a time. Multiple `PipelineRun` instances may still exist historically or concurrently for different application environments.
- YAML `sources` is the optional source workspace declaration. First implementation supports at most one source, only `type: gitlab`; when `sources` is absent and the run has an application, checkout uses the application-linked GitLab repository and the application default branch. Only create an empty temporary workspace when both `sources` and application context are absent. For `submit-branch` runs, do not run a hidden pre-merge step.
- Job runtime is lazy-created. `PLATFORM` steps must not create or hold Docker containers; the first `JOB_RUNTIME` step in a job creates the runtime.
- Every job must use an isolated source workspace. Parallel jobs must not share a writable source or artifact directory; dependency caches may be shared by tenant or executor group.
- Variable expansion must go through a shared resolver. First phase supports simple `${VAR}` substitution only, and credential values must be masked in logs, `contextJson`, and `resultJson`.
- Step output variables should be stored in `resultJson.outputs`, use uppercase alphanumeric/underscore names, remain scoped to the same pipeline run, and must not contain secrets.
- `ArtifactUpload` and `UnitTestReport` should write standard metadata to `resultJson.artifacts` and `resultJson.reports` using workspace-relative paths, never host absolute paths.
- First implementation does not expose YAML-configurable Docker socket mount, custom host volumes, `privileged`, or custom network mode.
- Full build logs and artifact/report URLs may be deferred; DB stores sanitized summaries and workspace-relative metadata first.
- Step ids must be unique across the whole pipeline because run logs and frontend step detail lookups use `stepId`.
- Parser accepts both JSON and YAML text in `specJson`; YAML is the primary authoring format.
- Code, comments, logs, class names, and tests must use neutral product wording such as “流水线 YAML” or “Pipeline YAML”; do not name external competitor products in implementation artifacts.
- Unknown step types fail validation unless registered in `PipelineNodeRegistryServiceImpl`.
- First implementation needs real `Command`, `CodeMerge`, and `APPROVAL` step handlers. `Command` executes `with.run` in the job runtime. `CodeMerge` is a platform step that merges branch arrays or submit-time change branches before downstream build jobs. `APPROVAL` is a platform step backed by the BPM process instance API.
- Built-in steps other than `Command` should not silently succeed in the first implementation. If encountered before their handlers exist, validation or execution must return a clear unsupported-step error.
- `dev_pipeline_run_log` is YAML-first storage: persist `stage_id/stage_name/job_id/job_name/step_id/step_type/step_name`, runtime fields such as `runtime_type/executor_group/executor_image/runtime_id/runtime_name/workspace_path`, and `duration_millis`.
- Do not keep Java/API compatibility aliases named `nodeId/nodeType/nodeName` in new or changed pipeline run APIs. Use `stepId/stepType/stepName` and stage/job fields directly. Do not add `node_id/node_type/node_name` back to the database schema.
- New execution or Docker-runtime code should fill runtime fields on run logs. Platform-control steps such as code merge, approval, and deployment use `runtime_type=PLATFORM`; containerized build steps use `runtime_type=DOCKER`.
- The execution extension point is `PipelineStepHandler`, not the old graph-oriented node handler. New code should use `PipelineStepHandler`, `PipelineStepContext`, `StepResult`, and `PipelineStepHandlerRegistry`; migrate existing `*NodeHandler` classes to `*StepHandler` before implementing Docker job runtime.
- `StepResult` should carry type, summary, sanitized error code/message, and non-sensitive outputs so the engine can update step log, job state, and run aggregate consistently.
- New pipeline interfaces must include Javadocs on the interface and each method, including parameter and return semantics. New pipeline model/context/result/DO fields must include field comments. Implementation classes do not need extra comments unless the logic is non-obvious.

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
| Two ready jobs run in parallel | Each job uses an isolated workspace |
| YAML value contains `${VAR}` | Shared resolver expands it from run/job/step variables |
| Step output contains credential material | Reject or mask before writing `resultJson.outputs`, logs, or context |
| `Command` without `with.run` | Validation error on `with.run` |
| `APPROVAL` without `with.processDefinitionKey` | Validation error on `with.processDefinitionKey` |
| Non-`Command`/`CodeMerge` step before handler exists | Validation or execution error indicating the step is not supported yet |
| `CodeMerge` without `with.baseBranch` or `with.targetBranch` | Validation error on the missing field |
| `CodeMerge` has `with.branches` | Merge those branches in array order; this takes precedence over `branchesFromSubmit` |
| `CodeMerge` omits `with.branches` and has `branchesFromSubmit=true` | Merge the submit-time change branches captured on the pipeline run |
| `CodeMerge` succeeds before a downstream `JOB_RUNTIME` job | Downstream source checkout uses `mergedBranch/mergedCommitSha` from step outputs |
| `CodeMerge` conflicts | Step log becomes `WAITING_INPUT`; owning job becomes `BLOCKED`; conflict APIs continue to resolve and resume the suspended job |

### 5. Tests Required

- Validation tests for JSON and YAML parsing, successful flattening, duplicate job ids, duplicate step ids, missing/self/cyclic `needs`, and required `with` params.
- Execution-engine tests must verify job DAG scheduling order and parallel-ready job discovery. Existing `sortExecutableSteps` tests may remain only as internal YAML flattening coverage.
- Runtime tests must verify PLATFORM-only jobs do not create Docker runtime and JOB_RUNTIME steps lazy-create it.
- Workspace tests must verify parallel jobs use isolated source/artifact directories.
- Variable resolver tests must verify `${VAR}` expansion and credential masking.
- Retry/resume/cancel tests must verify job attempt increments, blocked resume is idempotent, cancellation destroys runtime, and run aggregate status follows job states.
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
- Source checkout for YAML `sources` happens before creating the job runtime container and uses an isolated job workspace.
- Deploy branch selection is decided during `submit-branch` and stored in `dev_pipeline_run.branch_name`.
- Deploy branch naming for new branches uses `release/{envKey}/{yyyyMMddHHmmss}`. The application dimension is supplied by the repository/application environment; do not include `appKey` in new release branch names.
- When `submit-branch` only adds or refreshes changes and removes no currently mounted change, reuse the latest `release/` branch recorded by the same application environment when it exists, regardless of whether the later Jenkins/build stage succeeded.
- When `submit-branch` removes any currently mounted change from the target set, create a new timestamp release branch and rebuild from the application default branch.
- When `submit-branch` has no target changes, create a run against the application default branch source workspace; do not create or push a merged branch as a precondition.
- Empty-change release runs may have null compatibility anchor fields `dev_pipeline_run.change_id` and `dev_pipeline_run.change_env_id`; non-empty runs still set them from the first submitted change.
- Source workspace preparation clones the application-linked GitLab repository at the application default branch when `submit-branch` triggers a run. Do not expose raw tokens, tokenized clone URLs, or workspace absolute paths in API responses or error messages.
- `submit-branch` must reject a non-empty target set when the same application environment already has `QUEUED` or `RUNNING` runs.
- The release page should poll `current-run` for card-level job/step status. It must use `logs` only when opening detail dialogs.
- `current-run` must not return raw `context_json`; return only sanitized summaries and result output so workspace keys and runtime metadata are not exposed during polling.
- `current-run` is a polling read model and should use declarative Spring Cache keyed by `applicationEnvId`, with a short TTL as a stale-data safety net.
- Current-run cache invalidation must cover release submission, explicit change-env mount/unmount, pipeline publication, public pipeline execution mutation APIs such as start and cancel, and change mutations that affect mounted branch cards.
- Prefer declarative `@CacheEvict` when the method input directly carries `applicationEnvId`; use programmatic cache eviction by `change_env.application_env_id` when a change-level mutation can affect multiple environments.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| Same app environment has an active run on submit | Throw `PIPELINE_RUN_ACTIVE_EXISTS` before creating another run |
| Run id does not exist | Throw `PIPELINE_RUN_NOT_EXISTS` |
| Repository source is not supported for checkout | Mark run/job `FAILED` with sanitized detail |
| Git command fails unexpectedly | Mark run/job/log `FAILED` and store sanitized detail |

### 5. Good / Base / Bad Cases

- Good: `submit-branch` commits the release target set, creates one run, then after transaction commit dispatches published YAML execution to an async Spring bean.
- Good: source checkout happens per job workspace before the first `JOB_RUNTIME` step creates a Docker container.
- Good: YAML with no `sources` and an application-linked run checks out the application repository; ad-hoc runs without application context still run `Command` steps in an empty workspace.
- Bad: running source checkout in the request thread or inside `TransactionSynchronization.afterCommit`.
- Bad: doing a hidden pre-merge before the YAML execution engine starts.
- Bad: deriving the deploy branch again inside pipeline execution when `dev_pipeline_run.branch_name` already stores the submit-time decision.

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
  - active run blocks submit before new run creation;
  - submit without removals reuses the latest successful deploy branch;
  - submit with removals creates a timestamp deploy branch;
  - workspace preparation clones the application-linked GitLab repository at the application default branch for `submit-branch`;
  - YAML without `sources` uses the application-linked GitLab repository when `run.appId` exists, and creates an empty workspace only when no application context exists.

### 7. Wrong vs Correct

#### Wrong

```java
pipelineRunMapper.insert(pipelineRun);
pipelineExecutionEngine.execute(pipelineRun, userId);
```

#### Correct

```java
validateNoActivePipelineRun(applicationEnv.getId());
pipelineRunMapper.insert(pipelineRun);
schedulePipelineStart(pipelineRun.getId(), changeIds, userId);
```

`schedulePipelineStart` should register `TransactionSynchronization.afterCommit` and call an external `@Async` bean there; do not put `@Async` on a self-invoked private/local method.

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
  - `PipelineStepHandler#runtimeRequirement` returns `PLATFORM` or `JOB_RUNTIME`.
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
- Current-run read model:
  - `CONTAINER_DEPLOY` node logs use `detailType = DEPLOYMENT_ORDER`.
  - `run_log.result_json.deploymentOrderId` is the lightweight link to deployment-order details.

### 3. Contracts

- Jenkinsfile generation must skip `CONTAINER_DEPLOY`; Jenkins never executes this node.
- Phase A topology allows at most one `CONTAINER_DEPLOY`, and it must be the single terminal node after Jenkins nodes.
- If Jenkins is skipped or all Jenkins nodes complete and a container deploy node exists, platform code must call `DeploymentOrderService.startContainerDeploy(...)` instead of marking the run successful.
- Deployment order creation resolves namespace only from `EnvironmentDO.infraConfig.namespace`; node params must not expose a namespace override.
- MVP only supports single-document Kubernetes `Deployment` YAML and existing container name. `Service`/`Ingress`/multi-doc YAML are rejected during validation.
- Manifest `metadata.name` is the deployment workload name source of truth; execution applies the rendered manifest with `createOrReplace`, not workload patch mode.
- Deployment order snapshot must retain both the original `manifestYaml` and the rendered `renderedManifestYaml`, so retries and audit views are based on the captured execution input instead of later DSL edits.
- If node `replicas` is present, it overrides `spec.replicas` in the rendered YAML before apply.
- Deployment success requires Kubernetes Deployment rollout ready; manifest apply success alone must not mark the order, node log, or run successful.
- Retry reuses the same deployment order and increments `attempt`; it does not create another pipeline run or deployment-order row.
- Cancel marks the order/run/log canceled and stops waiting when the rollout loop observes the canceled order. It does not rollback an already-submitted Kubernetes apply.
- Store rollback prerequisites on the order when available: `previousImage`, `previousReplicas`, and `previousRevision`.
- Deployment-order detail may query Kubernetes live state, but long-lived facts and audit snapshots belong on `dev_deployment_order`.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| More than one `CONTAINER_DEPLOY` node | Validation error `CONTAINER_DEPLOY_DUPLICATE` |
| `CONTAINER_DEPLOY` is not the terminal node | Validation error `CONTAINER_DEPLOY_NOT_TERMINAL` |
| `infraType` is not `K8S` | Validation error `PARAM_VALUE_INVALID` |
| `deployMode` is not `RAW_MANIFEST` | Validation error `PARAM_VALUE_INVALID` |
| Required deploy params are blank | Validation error `PARAM_REQUIRED` |
| `manifestYaml` is blank / invalid YAML / not Deployment / contains multiple documents / missing `metadata.name` | Validation error `PARAM_VALUE_INVALID` |
| `containerName` is not present in manifest containers | Validation error `PARAM_VALUE_INVALID` |
| Environment is not K8S or has no kubeconfig/namespace | Throw deployment environment business error |
| Rollout timeout | Mark order/log/run failed with rollout timeout |
| Cancel/retry from an invalid state | Throw `DEPLOYMENT_ORDER_STATE_INVALID` |

### 5. Good / Base / Bad Cases

- Good: Jenkins callback only records Jenkins node state, then advances to the platform deployment service when every Jenkins node is successful.
- Good: deployment config is snapshotted in `dev_deployment_order.config_json`, so later DSL edits do not change retry behavior.
- Good: current-run polling stays lightweight and links to deployment-order detail instead of embedding live Kubernetes pod lists.
- Base: pipeline without `CONTAINER_DEPLOY` keeps the existing Jenkins-only success behavior.
- Bad: adding `CONTAINER_DEPLOY` to Jenkinsfile stages or relying on Jenkins callback for that node.
- Bad: adding a namespace node param before product requirements explicitly allow namespace override.
- Bad: persisting pod status history in the MVP deployment order when it can be queried live from Kubernetes.

### 6. Tests Required

- Registry test: `CONTAINER_DEPLOY` is configurable, has K8S/raw-manifest params, and does not include Jenkins-only params or namespace override.
- Validation tests: terminal success, non-terminal failure, duplicate failure, and param validation failure.
- Validation tests: YAML parse failure, target container missing, single-doc head `---` allowed, multi-doc YAML rejected.
- Jenkinsfile test: generated Jenkinsfile does not contain `CONTAINER_DEPLOY`.
- Callback/execution tests: Jenkins completion or skipped Jenkins starts deployment order instead of directly marking success.
- Deployment service tests: create-order snapshot includes original/rendered YAML, image expression resolution, namespace override from environment, replicas override, cancel state transition, invalid cancel/retry state, retry attempt increment, and failed Kubernetes boundary updates order/log/run.
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
- Downloads for Node and Maven must be checksum-verified during build.
- Use `dnf install --setopt=install_weak_deps=False` for base packages to avoid pulling unnecessary runtime packages.
- Do not assume Docker socket, privileged mode, custom host volumes, or custom network settings are available inside the build image. The first pipeline runtime does not expose those YAML controls.
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
- Bad: mounting `/var/run/docker.sock` into this image for arbitrary build scripts.

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
