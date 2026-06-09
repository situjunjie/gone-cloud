# DevOps Pipeline Guidelines

DevOps 流水线定义由平台持有，Jenkins 在当前阶段只作为构建/测试/Jenkinsfile 执行器的下游目标。不要把流水线定义所有权下放到 Jenkins Job 配置里。

## Scenario: Pipeline Code Merge Execution MVP

### 1. Scope / Trigger

- Trigger: adding or changing pipeline run execution, release-submit trigger behavior, code-merge conflict APIs, or run log persistence.
- Scope: `ApplicationService.submitApplicationReleaseBranch`, pipeline run controllers, execution services, Git workspace integration, run/log mappers, `dev_pipeline_run` / `dev_pipeline_run_log` SQL, enums, error codes, and focused tests.

### 2. Signatures

- Trigger API:
  - `POST /devops/application/release/submit-branch`
  - Every release submission creates one `dev_pipeline_run` and starts built-in `CODE_MERGE`, including an empty `changeIds[]` baseline release.
- Release-page polling API:
  - `GET /devops/application/release/current-run?applicationEnvId={id}`
  - Returns the current or latest run for the application environment, plus lightweight node execution state for card rendering.
- Run APIs:
  - `GET /devops/pipeline-run/{runId}/logs`
  - `GET /devops/pipeline-run/{runId}/code-merge/conflicts`
  - `GET /devops/pipeline-run/{runId}/code-merge/conflict-detail?filePath={path}`
  - `PUT /devops/pipeline-run/{runId}/code-merge/conflict-resolution`
  - `POST /devops/pipeline-run/{runId}/code-merge/continue`
  - `POST /devops/pipeline-run/{runId}/code-merge/retry-current-change`
  - `POST /devops/pipeline-run/{runId}/cancel`
- DB:
  - `dev_pipeline_run` remains the run master record.
  - `dev_pipeline_run_log` stores generic `NODE` / `STEP` / `EVENT` logs with `context_json` and `result_json`.

### 3. Contracts

- `CODE_MERGE` is a fixed built-in first node; do not require visual DSL configuration for this first step.
- Code merge runtime details belong in `dev_pipeline_run_log.context_json`, not in merge-specific DOs or tables.
- Deploy branch selection is decided during `submit-branch` and stored in `dev_pipeline_run.branch_name`.
- Deploy branch naming for new branches uses `release/{envKey}/{yyyyMMddHHmmss}`. The application dimension is supplied by the repository/application environment; do not include `appKey` in new release branch names.
- When `submit-branch` only adds or refreshes changes and removes no currently mounted change, reuse the latest `release/` branch recorded by the same application environment when it exists, regardless of whether the later Jenkins/build stage succeeded.
- When `submit-branch` removes any currently mounted change from the target set, create a new timestamp release branch and rebuild from the application default branch.
- When `submit-branch` has no target changes, create a new timestamp release branch from the application default branch, skip change merges, push the branch, and trigger Jenkins with the base commit SHA.
- Empty-change release runs may have null compatibility anchor fields `dev_pipeline_run.change_id` and `dev_pipeline_run.change_env_id`; non-empty runs still set them from the first submitted change.
- Git workspace preparation first tries to fetch `origin/{deployBranch}` and check out from it; if the remote deploy branch is missing, it falls back to checking out from `origin/{baseBranch}` with the same deploy branch name.
- The deploy branch is pushed only after every target change branch merges successfully.
- Conflict text/content is read from the isolated Git workspace on demand; do not persist large conflict bodies in DB.
- GitLab access-token repositories are the only supported code source for this MVP. Do not expose raw tokens, tokenized clone URLs, or workspace absolute paths in API responses or error messages.
- `submit-branch` must reject a non-empty target set when the same application environment already has `QUEUED` or `RUNNING` runs.
- The release page should poll `current-run` for card-level node status. It must use `logs` / `conflicts` / `conflict-detail` only when opening detail dialogs or conflict resolution views.
- `current-run` must not return raw `context_json`; return only sanitized summaries, result output, detail type, and conflict count so workspace keys and blob metadata are not exposed during polling.
- `current-run` is a polling read model and should use declarative Spring Cache keyed by `applicationEnvId`, with a short TTL as a stale-data safety net.
- Current-run cache invalidation must cover release submission, pipeline publication, and public pipeline execution mutation APIs such as start, conflict resolution save, continue, retry, and cancel.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| Same app environment has an active run on submit | Throw `PIPELINE_RUN_ACTIVE_EXISTS` before creating another run |
| Run id does not exist | Throw `PIPELINE_RUN_NOT_EXISTS` |
| Code-merge log does not exist | Throw `PIPELINE_RUN_LOG_NOT_EXISTS` |
| Continue/retry when node is not `WAITING_INPUT` | Throw `PIPELINE_RUN_LOG_STATE_INVALID` |
| Any text conflict lacks a saved resolution | Throw `PIPELINE_CODE_MERGE_CONFLICT_UNRESOLVED` |
| Conflict is unsupported/non-text | Throw `PIPELINE_CODE_MERGE_CONFLICT_UNSUPPORTED` for online resolution/continue |
| Repository source is not supported for merge | Throw `PIPELINE_CODE_MERGE_REPOSITORY_AUTH_NOT_SUPPORTED` |
| Git command fails unexpectedly | Mark run/log `FAILED` and store sanitized `PIPELINE_CODE_MERGE_GIT_EXEC_FAIL`-style detail |

### 5. Good / Base / Bad Cases

- Good: `submit-branch` commits the release target set, creates one run, then starts code merge after transaction commit.
- Good: every branch merge attempt has a `STEP` log, while user actions such as saving resolution and retrying create `EVENT` logs.
- Good: unsupported conflict can be fixed externally, then the original run retries only the current change branch after refreshing that branch SHA.
- Base: after code merge success, this MVP marks the run successful until Jenkins/build/deploy execution nodes are implemented.
- Bad: adding `merge_item`, `merge_conflict`, or `merge_resolution` persistent tables for this temporary execution state.
- Bad: pushing the deploy branch while some target changes are still pending or conflicting.
- Bad: deriving the deploy branch again inside pipeline execution when `dev_pipeline_run.branch_name` already stores the submit-time decision.

### 6. Tests Required

- Compile DevOps server with reactor:
  `mvn -pl yudao-module-devops/yudao-module-devops-server -am -DskipTests compile`
- Run focused tests:
  `mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dtest='ApplicationServiceImplTest,PipelineExecutionServiceImplTest,*Pipeline*Test' -Dsurefire.failIfNoSpecifiedTests=false test`
- Cover:
  - `current-run` service method is annotated with `@Cacheable` using the application environment id as key;
  - release submission, pipeline publication, and public pipeline execution mutation methods are annotated with `@CacheEvict` for the same current-run cache;
  - release submit creates a run and calls `PipelineExecutionService.startCodeMerge`;
  - current-run returns static nodes as `PENDING` when no run exists;
  - current-run maps `CODE_MERGE` to the release page checkout/code node and exposes detail type/conflict count during `WAITING_INPUT`;
  - empty target set does not create a run;
  - active run blocks submit before new run creation;
  - successful merge pushes deploy branch only after all items succeed;
  - submit without removals reuses the latest successful deploy branch;
  - submit with removals creates a timestamp deploy branch;
  - workspace preparation checks out from the remote deploy branch when present and from the base branch when absent;
  - conflict pauses node as `WAITING_INPUT` and does not push;
  - save/continue/retry/cancel keep run/log state and conflict context consistent.

### 7. Wrong vs Correct

#### Wrong

```java
pipelineRunMapper.insert(pipelineRun);
pipelineExecutionService.startCodeMerge(pipelineRun.getId(), changeIds, userId);
```

#### Correct

```java
validateNoActivePipelineRun(applicationEnv.getId());
pipelineRunMapper.insert(pipelineRun);
scheduleCodeMergeStart(pipelineRun.getId(), changeIds, userId);
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
- Jenkins descriptor reads:
  - `/descriptorByName/hudson.model.JDK/api/json?tree=installations[name,home]`
  - `/descriptorByName/hudson.tasks.Maven/api/json?tree=installations[name,home]`

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

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| `type` is blank | Return JDK and Maven tools in one list |
| `type=JDK` | Query only the JDK descriptor |
| `type=MAVEN` | Query only the Maven descriptor |
| unsupported `type` | Throw `PIPELINE_JENKINS_CONFIG_INVALID` |
| Jenkins descriptor returns 404 | Return an empty list for that descriptor |
| Jenkins request fails for other reasons | Throw `PIPELINE_JENKINS_TOOL_FETCH_FAIL` with a sanitized message |

### 5. Good / Base / Bad Cases

- Good: frontend renders `toolJdk` and `toolMaven` as selects backed by `jenkins-tools`, then saves the selected `name` string in the existing DSL.
- Base: no Jenkins tools are configured; the endpoint returns `[]` and the stage can still omit tool names.
- Bad: frontend hard-codes tool names such as `jdk-17.0.12` or `mvn`, because Jenkins global tool names are environment-specific.
- Bad: generated Jenkinsfile writes tool home paths instead of Jenkins tool names.

### 6. Tests Required

- Jenkins client unit test parses descriptor `installations[name,home]` into `type/name/home`.
- Jenkins client unit test treats descriptor 404 as empty list.
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
  - `PipelineSpec.Node.type`
  - `PipelineSpec.Node.name`
  - `PipelineSpec.Node.params`
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
- Jenkins stage display name should use `PipelineSpec.Node.name`; stable callback identity must use `nodeId`, `nodeType`, and `nodeName`.
- Common Jenkins stage params live in `node.params`:
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
