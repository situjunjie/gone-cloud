# Jenkins feasibility and technical plan

## Executive conclusion

总体方向可行，建议作为 DevOps 流水线编排第一版主路线。

推荐边界：

* Gone Cloud DevOps 平台负责：可视化编排、DSL 校验、Jenkinsfile 生成、版本审计、部署触发、运行状态聚合、日志展示、审批权限和审批记录。
* Jenkins 负责：实际 CI/CD 执行，包括合并、构建、测试、部署命令和运行环境。

核心原则：

* 前端不直接生成可信 Jenkinsfile。
* 前端生成结构化 DSL，后端通过白名单模板生成 Jenkinsfile。
* Jenkinsfile 尽量薄，复杂逻辑放 Jenkins Shared Library。
* 平台必须保存每次运行使用的 DSL、生成的 Jenkinsfile、Jenkins job/build number、stage 映射、日志索引和审批记录。

## Official references

* Jenkins Remote Access API: https://www.jenkins.io/doc/book/using/remote-access-api/
* Jenkins CSRF Protection: https://www.jenkins.io/doc/book/security/csrf-protection/
* Jenkins Pipeline as Code: https://www.jenkins.io/doc/book/pipeline/pipeline-as-code/
* Jenkinsfile docs: https://www.jenkins.io/doc/book/pipeline/jenkinsfile/
* Jenkins Pipeline Syntax: https://www.jenkins.io/doc/book/pipeline/syntax/
* Jenkins Shared Libraries: https://www.jenkins.io/doc/book/pipeline/shared-libraries/
* Pipeline REST API plugin: https://plugins.jenkins.io/pipeline-rest-api
* Pipeline Input Step: https://www.jenkins.io/doc/pipeline/steps/pipeline-input-step/

## Feasibility matrix

| Requirement | Feasibility | Recommended implementation | Main risk |
|---|---:|---|---|
| Frontend drag/drop orchestration | High | Vue Flow edits DSL nodes/edges; backend owns templates | Need strict DSL validation |
| Generate Jenkinsfile | High | Backend template engine + whitelisted node registry | Groovy injection if careless |
| Trigger Jenkins deployment | High | Remote Access API `buildWithParameters` | Queue-to-build mapping |
| Sense each pipeline stage | High with plugin | Pipeline REST API `wfapi/describe` | Plugin must be installed and compatible |
| View stage details/logs | High with plugin | Stage node `wfapi/describe` + node `wfapi/log`; fallback progressive console log | Large logs and partial polling |
| Pause for approval | High | Jenkins `input` step with explicit `id` | Jenkins approval actor differs from platform actor |
| Platform approval continues Jenkins | Medium-high | Platform calls pending input `proceedUrl` / `abortUrl` using Jenkins service account | Endpoint permissions and CSRF/auth |
| Audit and replay | High | Store DSL, Jenkinsfile, run snapshot, external ids | Must version everything |

## Recommended end-to-end flow

1. User edits pipeline in platform UI.
2. UI submits `pipeline_spec_json` and `diagram_json`.
3. Backend validates node schema, topology, allowed transitions, permissions, environment constraints.
4. Backend generates Jenkinsfile from templates and stores generated content/checksum.
5. Backend publishes Jenkinsfile to a pipeline source location.
6. User clicks deploy in platform.
7. Backend creates `PipelineRun` and `PipelineStageRun` pending rows.
8. Backend calls Jenkins `buildWithParameters`.
9. Backend resolves Jenkins queue item into `jobUrl` + `buildNumber`.
10. Backend polls Jenkins run/stage APIs and updates platform stage states.
11. If Jenkins enters pending input, backend creates platform approval task.
12. Platform approver approves/rejects.
13. Backend calls Jenkins pending input proceed/abort URL.
14. Jenkins continues or aborts.
15. Backend syncs final status, logs, artifacts, and updates `dev_change_env.last_*_status`.

## Frontend orchestration design

Use Vue Flow for MVP.

Canvas behavior:

* Left palette: Merge, Build, Unit Test, Approval, Deploy.
* Center canvas: drag nodes, connect edges, select node.
* Right panel: node parameters from backend-provided JSON schema.
* Top toolbar: save draft, validate, preview Jenkinsfile, publish version.
* Bottom/run drawer: stage timeline, logs, artifacts, Jenkins external link.

Frontend output should be structured DSL:

```json
{
  "version": "1.0",
  "nodes": [
    {
      "id": "merge_code",
      "type": "MERGE_CODE",
      "name": "代码合并",
      "params": {
        "sourceBranch": "${change.branchName}",
        "targetBranch": "${env.deployBranchName}"
      }
    },
    {
      "id": "build_image",
      "type": "BUILD_IMAGE",
      "name": "构建镜像",
      "params": {
        "command": "mvn -DskipTests package",
        "imageName": "${app.appKey}"
      }
    },
    {
      "id": "unit_test",
      "type": "UNIT_TEST",
      "name": "单元测试",
      "params": {
        "command": "mvn test"
      }
    },
    {
      "id": "approval_prod",
      "type": "APPROVAL",
      "name": "发布审批",
      "params": {
        "approverType": "ROLE",
        "approverIds": ["devops_release_manager"]
      }
    },
    {
      "id": "deploy_k8s",
      "type": "DEPLOY_K8S",
      "name": "部署到 K8S",
      "params": {
        "namespace": "${env.kubernetes.namespace}",
        "workload": "${app.appKey}"
      }
    }
  ],
  "edges": [
    {"source": "merge_code", "target": "build_image"},
    {"source": "build_image", "target": "unit_test"},
    {"source": "unit_test", "target": "approval_prod"},
    {"source": "approval_prod", "target": "deploy_k8s"}
  ]
}
```

## Jenkinsfile generation strategy

Recommended: Declarative Pipeline + Shared Library.

Generated Jenkinsfile should be thin:

```groovy
@Library('gone-devops-shared') _

pipeline {
  agent none
  options {
    timestamps()
    disableConcurrentBuilds()
  }
  parameters {
    string(name: 'PIPELINE_RUN_ID')
    string(name: 'CHANGE_ENV_ID')
    string(name: 'APP_ID')
    string(name: 'ENV_ID')
    string(name: 'BRANCH_NAME')
  }
  stages {
    stage('merge_code__MERGE_CODE') {
      agent any
      steps {
        goneDevopsMerge(nodeId: 'merge_code')
      }
    }
    stage('build_image__BUILD_IMAGE') {
      agent any
      steps {
        goneDevopsBuildImage(nodeId: 'build_image')
      }
    }
    stage('unit_test__UNIT_TEST') {
      agent any
      steps {
        goneDevopsUnitTest(nodeId: 'unit_test')
      }
    }
    stage('approval_prod__APPROVAL') {
      agent none
      steps {
        input id: "approval-${params.PIPELINE_RUN_ID}-approval_prod",
              message: '请在 Gone Cloud DevOps 平台完成发布审批',
              ok: '继续部署'
      }
    }
    stage('deploy_k8s__DEPLOY_K8S') {
      agent any
      steps {
        goneDevopsDeployK8s(nodeId: 'deploy_k8s')
      }
    }
  }
}
```

Stage naming rule:

* Use `${nodeId}__${nodeType}` as Jenkins stage name.
* Store `nodeId -> stageName` in `PipelineStageRun`.
* This makes Jenkins stage responses mappable back to platform nodes.

## Jenkinsfile storage options

Jenkinsfile can be stored in the platform database as a generated, versioned artifact. The important distinction is:

* Platform storage is easy: store Jenkinsfile text, checksum, generated-from DSL version, creator, publish time, and audit metadata.
* Jenkins execution handoff is separate: a standard Jenkins Pipeline job does not directly accept arbitrary Jenkinsfile text through `buildWithParameters` as its Pipeline definition.

### Option A: Commit to application repository

Pros:

* Aligns with Pipeline as Code.
* Application pipeline changes are visible in app repo history.

Cons:

* Platform edits modify application source repository.
* Harder to isolate pipeline governance from application code owners.

### Option B: Commit to independent pipeline repository

Pros:

* Clean separation between application code and platform-generated pipeline files.
* Easy audit and rollback.
* Jenkins can use Pipeline from SCM without mutating app repositories.

Cons:

* Need manage mapping from application/env/version to Jenkinsfile path.

Recommendation: use Option B for MVP.

Example path:

```text
pipelines/{tenantId}/{appKey}/{envKey}/Jenkinsfile
pipelines/{tenantId}/{appKey}/{envKey}/pipeline-spec.json
```

### Option C: Save script in Jenkins job config

Pros:

* No Git write operation needed.
* Easy for a quick POC.

Cons:

* Weaker audit.
* Job config update via API is more fragile and permission-sensitive.
* Harder to review and roll back generated Jenkinsfile.

Recommendation: only use for internal POC.

### Option D: Store Jenkinsfile in platform DB and update Jenkins job config before build

Flow:

1. Platform stores generated Jenkinsfile in DB.
2. On publish or deploy, platform renders Jenkins job `config.xml`.
3. Platform calls Jenkins job create/update API to set the Pipeline script in job config.
4. Platform triggers the job through Remote Access API.

Pros:

* No Git repository required for Jenkinsfile.
* The platform is the single source of truth for generated Jenkinsfile.
* Useful for fast POC.

Cons:

* Updating a shared Jenkins job before every deployment creates concurrency risks.
* Need one job per pipeline version/run, or a strict lock, otherwise two deploys can overwrite each other.
* Jenkins job config mutation needs high Jenkins permission.
* Audit is weaker on Jenkins side unless the platform records every version.

Recommendation: acceptable for POC; for production use either one immutable job per published version or prefer SCM/pipeline repo.

### Option E: Store Jenkinsfile in platform DB and use a fixed bootstrap Jenkinsfile

Flow:

1. Jenkins job has a stable bootstrap Pipeline script.
2. Platform triggers job with `PIPELINE_RUN_ID` or `PIPELINE_VERSION_ID`.
3. Bootstrap script calls platform API to fetch DSL or execution plan.
4. Jenkins Shared Library executes stages according to the fetched plan.

Pros:

* No Jenkins job config mutation per deploy.
* No generated Jenkinsfile needs to be committed to Git.
* Platform DB remains the source of truth.
* Easier to run concurrent deploys of different pipeline versions.

Cons:

* This is no longer “Jenkins executes a generated Jenkinsfile” in the strict sense; Jenkins executes a stable bootstrap script plus shared library.
* Dynamic stage generation is easier in Scripted Pipeline than Declarative Pipeline.
* Requires a well-designed Shared Library and platform execution-plan API.

Recommendation: best database-first production approach if the team strongly prefers not to write Jenkinsfile to Git.

## How Jenkins can receive or resolve Pipeline script

Standard Jenkins options:

| Method | How it works | Can Jenkinsfile stay only in platform DB? | Recommendation |
|---|---|---:|---|
| Pipeline script from SCM | Job fetches Jenkinsfile from Git path | No, must materialize to SCM before build | Best standard production mode |
| Inline Pipeline script in job config | Job `config.xml` stores script text | Yes, if platform updates job config | POC or one-job-per-version |
| Parameterized build | `buildWithParameters` passes strings/files | Not directly as Pipeline definition | Use only to pass IDs/branch/env |
| File parameter | Uploads a file to build workspace | Not directly as Pipeline definition | Useful only with bootstrap script |
| Fixed bootstrap Jenkinsfile | Stable job fetches platform DSL/plan by run id | Yes | Best DB-first mode |
| HTTP Pipeline plugin | Plugin fetches script over HTTP | Possible with non-core plugin | Avoid first version unless needed |

Important finding:

* Jenkins Remote Access API supports triggering jobs and parameterized builds, including file parameters.
* However, those parameters are build inputs. They do not replace the Pipeline job definition for a normal Jenkins Pipeline job.
* A Pipeline job needs its script definition to already exist as inline job config or SCM-resolved Jenkinsfile before the build starts.

## Recommended answer for platform DB storage

Yes, store Jenkinsfile in the platform DB as:

* `pipeline_spec_json`
* `diagram_json`
* `jenkinsfile_text`
* `jenkinsfile_checksum`
* `generator_version`
* `published_version`
* `published_by`
* `published_at`

But for Jenkins execution, choose one of two production paths:

1. Standard path: DB stores version and audit copy; platform also writes Jenkinsfile to an independent pipeline Git repo; Jenkins runs `Pipeline script from SCM`.
2. DB-first path: DB stores the execution plan; Jenkins runs a fixed bootstrap Pipeline and Shared Library; platform passes `PIPELINE_RUN_ID`, and Jenkins fetches the plan from the platform.

Avoid for production:

* Passing raw Jenkinsfile text as a parameter and dynamically executing it.
* Updating one shared Jenkins job's inline script before every deploy without locking/version isolation.

## Jenkins API integration

### Provider configuration

Store Jenkins provider:

* name
* baseUrl
* username/serviceAccount
* apiToken encrypted
* defaultFolder
* defaultJob
* crumb mode
* connection check result

Use API token authentication. Jenkins docs indicate API-token authenticated POST requests are exempt from CSRF crumbs on modern Jenkins. Still keep crumb support for older or specially configured instances.

### Trigger

Call:

```text
POST {jenkinsBase}/job/{jobPath}/buildWithParameters
```

Parameters:

* `PIPELINE_RUN_ID`
* `PIPELINE_VERSION_ID`
* `CHANGE_ID`
* `CHANGE_ENV_ID`
* `APP_ID`
* `ENV_ID`
* `BRANCH_NAME`
* `TRIGGER_USER_ID`

Persist:

* Jenkins queue item URL from response header if available.
* Jenkins job path.
* Jenkins build number after queue item resolves.
* Jenkins build URL.

### Status sync

Primary API:

```text
GET {buildUrl}/wfapi/describe
GET {buildUrl}/wfapi/pendingInputActions
GET {buildUrl}/execution/node/{nodeId}/wfapi/describe
GET {buildUrl}/execution/node/{nodeId}/wfapi/log
GET {buildUrl}/wfapi/artifacts
```

Fallback APIs:

```text
GET {buildUrl}/api/json
GET {buildUrl}/logText/progressiveText?start={offset}
GET {buildUrl}/consoleText
```

Sync strategy:

* Poll every 2-5 seconds while build is running.
* Poll every 10-30 seconds during long approval wait.
* Stop high-frequency polling after terminal state.
* Store log offsets to avoid re-reading full logs.
* Allow manual refresh from UI.

## Approval design

Recommended first version: Jenkins native `input` pause + platform approval bridge.

Jenkins side:

* Generated Jenkinsfile inserts an approval stage.
* Approval stage uses `agent none` to avoid occupying build executors.
* `input id` must include platform run id and node id.
* Jenkins pipeline enters `PAUSED_PENDING_INPUT`.

Platform side:

* Poll `wfapi/pendingInputActions`.
* Match input id to `PipelineApprovalTask`.
* Display approval task in platform UI.
* Approver approves/rejects under platform permission model.
* Backend calls Jenkins `proceedUrl` or `abortUrl`.
* Platform stores real approver id, approval time, comment, Jenkins input id, and Jenkins API response.

Important caveat:

* Jenkins will see the Jenkins service account as the user who mechanically proceeded the input.
* The platform database must be the source of truth for actual business approver.

Alternative design:

* Do not use Jenkins `input`.
* Shared Library calls platform API to create approval task, then waits/polls platform status.

This gives platform full control but requires custom Jenkins shared library logic and careful timeout handling. Use it only if native `input` endpoint permissions become hard to manage.

## Backend data model additions

Suggested tables:

* `dev_jenkins_provider`
* `dev_pipeline_definition`
* `dev_pipeline_definition_version`
* `dev_pipeline_jenkinsfile_artifact`
* `dev_pipeline_run`
* `dev_pipeline_stage_run`
* `dev_pipeline_approval_task`
* `dev_pipeline_log_cursor`
* `dev_pipeline_artifact`

Important fields:

* definition version: `spec_json`, `diagram_json`, `jenkinsfile_text`, `jenkinsfile_checksum`, `published`
* run: `application_env_id`, `change_env_id`, `definition_version_id`, `jenkins_provider_id`, `jenkins_job_path`, `jenkins_queue_url`, `jenkins_build_number`, `jenkins_build_url`, `status`
* stage run: `node_id`, `node_type`, `stage_name`, `jenkins_stage_id`, `status`, `start_time`, `duration`, `error_message`, `log_url`
* approval task: `run_id`, `stage_run_id`, `input_id`, `proceed_url`, `abort_url`, `status`, `approved_by`, `approved_at`, `comment`

## Security requirements

* Never accept raw Groovy/Jenkinsfile from frontend as executable content.
* Generate Jenkinsfile only from backend-owned templates and node registry.
* Escape all user-visible strings inserted into Jenkinsfile.
* Keep secrets in Jenkins Credentials or platform encrypted fields; never emit secrets into Jenkinsfile text or logs.
* Use Jenkins service account with least privilege: build/read target jobs and proceed/abort input where needed.
* Validate tenant/application/environment ownership before triggering Jenkins.
* Treat Jenkins logs as potentially sensitive; gate log viewing by platform permission.
* Store Jenkins API token encrypted and mask it in responses/logs.

## Risks and mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Pipeline REST API plugin missing | Cannot read stage-level status/logs cleanly | Add provider connection check that validates plugin endpoints |
| Jenkins `input` proceed API permission issues | Platform cannot continue paused build | Use service account with correct permissions; fallback to platform-wait shared library |
| Generated Jenkinsfile syntax error | Deployment fails before useful stages | Add preview and Jenkinsfile syntax validation in publish step |
| Logs too large | Slow UI, storage pressure | Cursor-based incremental sync, retention policy, on-demand loading |
| Stage mapping drift | Platform cannot map Jenkins stages to nodes | Deterministic stage names and persisted node/stage mapping |
| Queue item never resolves | Run stuck in queued | Timeout and retry/cancel controls |
| Jenkins unavailable | Deploy blocked | Provider health check, clear error surfaces, retry policy |
| Cross-tenant leakage | Security incident | Tenant-scoped provider binding and strict permission checks |

## MVP recommendation

Phase 1 POC:

* Use Vue Flow to build a static 5-node pipeline editor.
* Backend stores DSL and generates a thin Jenkinsfile.
* Use one Jenkins provider and one manually created Pipeline job.
* Trigger job through `buildWithParameters`.
* Poll `wfapi/describe` and show stage status.
* Poll stage logs or console progressive text.
* Add one approval stage using Jenkins `input`.
* Approve/reject from platform and call Jenkins proceed/abort URL.

Phase 2 productization:

* Add pipeline version publishing.
* Add Jenkinsfile commit to independent pipeline repo.
* Add provider/job binding management.
* Add run history, artifacts, cancellation, retry, permission model.
* Add node schema registry and node-specific property panels.

Phase 3 hardening:

* Add webhook callback from Jenkins Shared Library to reduce polling.
* Add complex DAG/parallel stage support.
* Add reusable pipeline templates.
* Add external CI provider abstraction if GitLab CI/GitHub Actions are needed later.
