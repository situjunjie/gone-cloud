# DevOps Pipeline YAML Spec

本文档是前后端联调用的流水线 YAML 契约。后端保存字段名仍为 `specJson`，但内容可以是 YAML 文本；JSON 仅作为兼容输入格式。

## API

### 获取应用环境流水线

`GET /devops/pipeline/get-by-application-env?applicationEnvId={applicationEnvId}`

返回当前应用环境的流水线定义、草稿版本、已发布版本。前端编辑时优先展示草稿版本；没有草稿时展示已发布版本；都没有时展示空编辑器。

版本对象包含 `rollbackFromVersionId`、`rollbackFromVersionNo`、`basedOnCurrentVersionId`、`basedOnCurrentVersionNo`、`rollbackReason`。普通发布版本这些字段为空；版本回退生成的新版本会记录回退来源和回退发生时的当前版本。

### 校验 YAML

`POST /devops/pipeline/validate`

```json
{
  "applicationEnvId": 1,
  "diagramJson": "{}",
  "specJson": "stages:\n  smoke_stage:\n    name: 冒烟测试\n    jobs:\n      smoke_job:\n        name: Docker Command 测试\n        runsOn:\n          group: local-docker/default\n          container: docker.m.daocloud.io/library/busybox:latest\n        steps:\n          command_step:\n            name: 执行命令\n            step: Command\n            with:\n              run: |\n                echo docker-command-ok\n"
}
```

响应：

```json
{
  "valid": true,
  "errors": [],
  "warnings": []
}
```

错误项结构：

```json
{
  "field": "stages.smoke_stage.jobs.smoke_job.steps.command_step.with.run",
  "nodeId": "command_step",
  "code": "PARAM_REQUIRED",
  "message": "命令步骤必须配置 run"
}
```

### 保存草稿

`POST /devops/pipeline/save-draft`

```json
{
  "applicationEnvId": 1,
  "name": "测试环境流水线",
  "diagramJson": "{}",
  "specJson": "stages:\n  smoke_stage:\n    name: 冒烟测试\n    jobs:\n      smoke_job:\n        name: Docker Command 测试\n        runsOn:\n          group: local-docker/default\n          container: docker.m.daocloud.io/library/busybox:latest\n        steps:\n          command_step:\n            name: 执行命令\n            step: Command\n            with:\n              run: |\n                echo docker-command-ok\n",
  "remark": "前端联调草稿"
}
```

返回草稿版本编号。保存草稿会执行校验并保存校验结果；即使校验失败也允许保存草稿，方便前端保留用户编辑内容。

### 发布版本

`POST /devops/pipeline/publish`

```json
{
  "definitionId": 1,
  "draftVersionId": 10,
  "versionName": "v1"
}
```

发布时后端会重新校验草稿 YAML。只有 `valid=true` 的草稿才能发布，发布成功后返回已发布版本编号。

### 回退版本

`POST /devops/pipeline/rollback`

```json
{
  "definitionId": 1,
  "targetVersionId": 6,
  "versionName": "v11",
  "rollbackReason": "发布后验证异常，回退到稳定版本"
}
```

回退不会修改历史版本，也不会把当前版本记录直接改回目标版本。后端会复制 `targetVersionId` 指向的已发布版本内容，生成一个新的已发布版本并立即更新为当前生效版本。新版本号继续按已发布版本递增，并记录：

- `rollbackFromVersionId` / `rollbackFromVersionNo`：被回退到的历史版本。
- `basedOnCurrentVersionId` / `basedOnCurrentVersionNo`：发起回退时的当前已发布版本。
- `rollbackReason`：本次回退原因。

返回新生成的已发布版本编号。

## YAML Shape

顶层结构：

```yaml
sources:
  <source_id>:
    type: <string>
    name: <string>
    endpoint: <string>
    branch: <string>
    with: {}
stages:
  <stage_id>:
    name: <string>
    enabled: <boolean>
    jobs:
      <job_id>:
        name: <string>
        enabled: <boolean>
        runsOn:
          group: <string>
          container: <string>
        needs:
          - <job_id>
        timeoutSeconds: <integer>
        retryTimes: <integer>
        failStrategy: failFast
        steps:
          <step_id>:
            name: <string>
            step: Command
            enabled: <boolean>
            timeoutSeconds: <integer>
            retryTimes: <integer>
            failStrategy: failFast
            with:
              run: |
                echo hello
              env:
                KEY: VALUE
```

## Fields

| 字段 | 必填 | 当前支持 | 说明 |
|---|---:|---|---|
| `sources` | 否 | 最多 1 个 | 代码源配置。存在时用于准备 job 的构建工作目录；不存在且 run 关联应用时，使用应用实体配置的仓库地址、代码源和默认分支。 |
| `sources.<source_id>.type` | 有 source 时必填 | `gitlab` | 代码源类型，当前仅支持 `gitlab`。 |
| `sources.<source_id>.name` | 否 | 任意字符串 | 展示名称。 |
| `sources.<source_id>.endpoint` | 有 source 时必填 | 任意非空字符串 | 代码源地址。 |
| `sources.<source_id>.branch` | 有 source 时必填 | 任意非空字符串 | 默认分支。 |
| `sources.<source_id>.with` | 否 | 对象 | 扩展参数。 |
| `stages` | 是 | 对象 | 阶段集合，key 为 `stageId`。 |
| `stages.<stage_id>.name` | 否 | 字符串 | 阶段展示名称。 |
| `stages.<stage_id>.enabled` | 否 | `true/false` | 默认 `true`。为 `false` 时阶段下任务/步骤不会执行。 |
| `jobs` | 是 | 对象 | 阶段内任务集合，key 为 `jobId`。 |
| `jobs.<job_id>.name` | 否 | 字符串 | 任务展示名称。 |
| `jobs.<job_id>.enabled` | 否 | `true/false` | 默认 `true`。 |
| `jobs.<job_id>.runsOn.group` | 容器型 step 必填 | `local-docker/default` | 执行资源池。当前只支持本地 Docker。仅包含平台 step 的 job 可不配置。 |
| `jobs.<job_id>.runsOn.container` | 容器型 step 必填 | Docker 镜像名 | 执行容器镜像。后端当前不自动 pull，测试前请确保镜像已存在。 |
| `jobs.<job_id>.needs` | 否 | 字符串或字符串数组 | 依赖任务编号。为空时同阶段/跨阶段其他任务不会自动成为前置依赖。 |
| `jobs.<job_id>.timeoutSeconds` | 否 | 正整数 | 任务超时时间，第一版默认 1800 秒。 |
| `jobs.<job_id>.retryTimes` | 否 | `0` | 第一版不做自动重试。 |
| `jobs.<job_id>.failStrategy` | 否 | `failFast` | 默认 `failFast`。 |
| `steps` | 是 | 对象 | 任务内步骤集合，key 为 `stepId`。 |
| `steps.<step_id>.name` | 否 | 字符串 | 步骤展示名称。 |
| `steps.<step_id>.step` | 是 | `Command` / `CodeMerge` / `APPROVAL` / `K8sDeploy` / `K8sImageUpgrade` / `PrivateRegistryDockerBuild` | 当前可执行步骤类型。 |
| `steps.<step_id>.enabled` | 否 | `true/false` | 默认 `true`。 |
| `steps.<step_id>.with.run` | `Command` 必填 | Shell 脚本 | 在 job 容器 `/workspace` 目录中执行。 |
| `steps.<step_id>.with.env` | 否 | 对象 | 环境变量，key 需匹配 `[A-Za-z_][A-Za-z0-9_]*`。 |
| `steps.<step_id>.with.baseBranch` | `CodeMerge` 必填 | 字符串 | 合并基础分支，支持 `${SOURCE_BRANCH}`。 |
| `steps.<step_id>.with.targetBranch` | `CodeMerge` 必填 | 字符串 | 合并目标分支，支持 `${BRANCH_NAME}`。 |
| `steps.<step_id>.with.branches` | 否 | 字符串数组 | 待合并分支列表。配置后优先使用该数组。 |
| `steps.<step_id>.with.branchesFromSubmit` | 否 | `true/false` | 默认 `true`。未配置 `branches` 时，使用本次发布提交携带的变更分支。 |
| `steps.<step_id>.with.pushOnSuccess` | 否 | `true/false` | 默认 `true`。合并成功后是否推送目标分支。 |
| `steps.<step_id>.with.processDefinitionKey` | `APPROVAL` 必填 | 字符串 | BPM 流程定义 key。审批步骤由平台执行，不创建 Docker 容器。 |
| `steps.<step_id>.with.deployMode` | `K8sDeploy` 必填 | `RAW_MANIFEST` | K8s 集群部署模式，当前仅支持单个 Deployment YAML。 |
| `steps.<step_id>.with.manifestYaml` | `K8sDeploy` 必填 | 字符串 | Deployment YAML，支持 `${IMAGE}`、`${APP_KEY}`、`${COMMIT_SHA}`、`${BRANCH_NAME}`、`${PIPELINE_RUN_ID}`、`${ENV_KEY}`、`${NAMESPACE}`。 |
| `steps.<step_id>.with.workloadKind` | `K8sImageUpgrade` 必填 | `Deployment` | 镜像升级目标工作负载类型，当前仅支持已存在的 Deployment。 |
| `steps.<step_id>.with.workloadName` | `K8sImageUpgrade` 必填 | 字符串 | 已存在的 Deployment 名称。不存在时步骤失败，不做自动创建。 |
| `steps.<step_id>.with.containerName` | `K8sDeploy` / `K8sImageUpgrade` 必填 | 字符串 | 目标容器名称。 |
| `steps.<step_id>.with.image` | `K8sDeploy` / `K8sImageUpgrade` 必填 | 字符串 | 目标镜像，支持 `${APP_KEY}`、`${COMMIT_SHA}`、`${BRANCH_NAME}`、`${PIPELINE_RUN_ID}`、`${ENV_KEY}`。 |
| `steps.<step_id>.with.replicas` | 否 | 整数 | 目标副本数；为空时保留 YAML 或现有 Deployment 副本数。 |
| `steps.<step_id>.with.rolloutTimeoutSeconds` | 否 | 整数 | 等待 Deployment rollout ready 的超时时间，默认 300 秒。 |
| `steps.<step_id>.with.artifact` | `PrivateRegistryDockerBuild` 必填 | 字符串 | 制品名称，成功后写入 step outputs/resultJson，供后续部署步骤选择。 |
| `steps.<step_id>.with.image` | `PrivateRegistryDockerBuild` 必填 | 字符串 | Docker 镜像上传地址，例如 `registry.cn-hangzhou.aliyuncs.com/ns/demo:1.0`。 |
| `steps.<step_id>.with.certificate.type` | `PrivateRegistryDockerBuild` 必填 | `usernamePassword` | 当前仅支持用户名密码；`serviceConnection` 暂不实现且校验会拒绝。 |
| `steps.<step_id>.with.certificate.username` | `certificate.type=usernamePassword` 必填 | 字符串 | 私有镜像仓库用户名。 |
| `steps.<step_id>.with.certificate.password` | `certificate.type=usernamePassword` 必填 | 字符串 | 私有镜像仓库密码。后端不会写入 resultJson；前端展示和保存时也应按敏感字段处理。 |
| `steps.<step_id>.with.dockerfilePath` | `PrivateRegistryDockerBuild` 必填 | 字符串 | Dockerfile 相对代码库根目录路径。 |
| `steps.<step_id>.with.contextPath` | 否 | 字符串 | Docker build 执行上下文，相对代码库根目录；为空时默认使用 Dockerfile 所在目录。 |
| `steps.<step_id>.with.noCache` | 否 | `true/false` | 默认 `false`。为 `true` 时追加 `--no-cache=true`。 |
| `steps.<step_id>.with.variables` | 否 | 数组 | 构建参数数组，每项 `{ key, value }` 会转换为 `--build-arg key=value`。 |
| `steps.<step_id>.with.buildkitVersion` | 否 | `v0.8.0` / `v0.9.0` / `v0.11.6` | 当前只做参数校验和前端展示；执行由后端平台 DockerClient 完成，暂不按该字段切换 BuildKit 版本。 |

## ID Rules

`source_id`、`stage_id`、`job_id`、`step_id` 必须满足：

```text
[a-zA-Z][a-zA-Z0-9_-]{0,63}
```

额外约束：

- `job_id` 必须在整条流水线内全局唯一。
- `step_id` 必须在整条流水线内全局唯一。
- `needs` 填写的是 `job_id`。
- `needs` 可以跨 stage 引用任务。
- `needs` 不能引用不存在的任务。
- `needs` 不能依赖自身。
- `needs` 不能形成循环依赖。

## Execution Semantics

- 每个容器型 job 在创建 Docker 容器前先准备独立 workspace。
- `sources` 当前最多只能定义 1 个来源，`type` 当前只支持 `gitlab`。
- 通过 `submit-branch` 触发运行时，如果 YAML 未配置 `sources`，源码 checkout 使用应用绑定的 GitLab 代码源和应用默认分支；不执行隐藏的前置代码合并。
- 没有配置 `sources` 且 run 没有关联应用时，后端只创建空临时 workspace，`Command` step 仍在容器 `/workspace` 执行。
- stage 只用于展示分组，不是隐式执行屏障。
- 没有 `needs` 的 job 立即具备调度条件。
- 有 `needs` 的 job 只有在依赖 job 全部成功后才会执行。
- 当前后端调度实现按 DAG 顺序推进，后续再扩展真正并发 worker。
- 任一 job 失败时，默认 `failFast`，剩余待执行 job 会被标记为跳过。
- 同一个 job 内多个 `Command` step 共享同一个容器和 `/workspace`。
- 不同 job 使用不同容器和不同 workspace。
- `CodeMerge` 是平台 step，不创建 Docker 容器，不要求 `runsOn`。
- `CodeMerge` 合并成功后会输出 `mergedBranch`、`mergedCommitSha`、`branchName`、`commitSha`；下游容器型 job 的源码 checkout 会优先使用合并后的目标分支和提交。
- `CodeMerge` 遇到冲突时 step log 进入 `WAITING_INPUT`，所属 job 进入 `BLOCKED`，继续使用现有代码冲突查看、保存解决结果、继续合并、重试当前分支接口处理。
- `APPROVAL` 是平台 step，不创建 Docker 容器，不要求 `runsOn`。
- `APPROVAL` 发起 BPM 流程实例后 step log 进入 `WAITING_INPUT`，所属 job 进入 `BLOCKED`；审批通过后再次调度会从该 step 继续向下执行，审批拒绝或取消会使流水线失败或取消。
- `K8sDeploy` 和 `K8sImageUpgrade` 是平台 step，不创建 Docker 容器，不要求 `runsOn`。
- `K8sDeploy` 会创建部署单，渲染并提交单个 Kubernetes Deployment YAML，然后等待 rollout ready。
- `K8sImageUpgrade` 会创建部署单，只升级已存在 Deployment 的目标容器镜像和可选副本数；Deployment 或容器不存在时步骤失败，不自动创建工作负载。
- 部署单只更新自身和 step log 状态；整条流水线的 job/run 状态由 `PipelineExecutionEngine` 聚合。
- 取消流水线时，未结束的 `APPROVAL` step 会主动取消关联的 BPM 流程实例，未结束的 K8s 部署 step 会主动取消关联部署单。
- `PrivateRegistryDockerBuild` 是平台 step，不创建 Docker job runtime，不要求 `runsOn`。后端会准备源码 workspace，并通过平台 DockerClient 构建和推送镜像。
- `PrivateRegistryDockerBuild` 不会主动把 `certificate.password` 写入 step result；前端展示和日志处理时不要回显密码输入值。

## Minimal Runnable YAML

用于前后端联调保存、发布、运行的最小配置：

```yaml
sources:
  my_repo:
    type: gitlab
    name: "应用代码源"
    endpoint: https://example.com/group/repo.git
    branch: master
stages:
  smoke_stage:
    name: "冒烟测试"
    jobs:
      smoke_job:
        name: "Docker Command 测试"
        runsOn:
          group: local-docker/default
          container: docker.m.daocloud.io/library/busybox:latest
        steps:
          command_step:
            name: "执行命令"
            step: Command
            with:
              run: |
                echo docker-command-ok
                uname -m
                pwd
```

运行前请确保后端所在机器 Docker daemon 可访问，并且镜像已存在：

```bash
docker pull docker.m.daocloud.io/library/busybox:latest
```

## CodeMerge + Build YAML

用于多个变更分支先合并到部署分支，再基于合并结果构建：

```yaml
sources:
  my_repo:
    type: gitlab
    name: "应用代码源"
    endpoint: https://example.com/group/repo.git
    branch: master
stages:
  merge_stage:
    name: "代码合并"
    jobs:
      code_merge_job:
        name: "合并变更分支"
        steps:
          code_merge_step:
            name: "合并到部署分支"
            step: CodeMerge
            with:
              baseBranch: "${SOURCE_BRANCH}"
              targetBranch: "${BRANCH_NAME}"
              pushOnSuccess: true
              branches:
                - feature/order-api
                - feature/order-ui
  build_stage:
    name: "构建验证"
    jobs:
      maven_build_job:
        name: "Maven 构建"
        needs:
          - code_merge_job
        runsOn:
          group: local-docker/default
          container: gone-cloud/pipeline-builder:java17-node24-maven3.9
        steps:
          build_step:
            name: "Maven 构建"
            step: Command
            with:
              run: |
                java -version
                node -v
                mvn -v
                mvn -B clean package -DskipTests
```

如果希望 `CodeMerge` 直接使用 `submit-branch` 本次提交的变更分支，不手写分支数组：

```yaml
with:
  baseBranch: "${SOURCE_BRANCH}"
  targetBranch: "${BRANCH_NAME}"
  branchesFromSubmit: true
  pushOnSuccess: true
```

## Approval + Build YAML

用于发布前先走人工审批，审批通过后再执行构建：

```yaml
sources:
  my_repo:
    type: gitlab
    name: "应用代码源"
    endpoint: https://example.com/group/repo.git
    branch: master
stages:
  approval_stage:
    name: "发布审批"
    jobs:
      approval_job:
        name: "人工审批"
        steps:
          approval_step:
            name: "发布审批"
            step: APPROVAL
            with:
              processDefinitionKey: devops_deploy_approval
  build_stage:
    name: "构建验证"
    jobs:
      maven_build_job:
        name: "Maven 构建"
        needs:
          - approval_job
        runsOn:
          group: local-docker/default
          container: alibaba-cloud-linux-3-registry.cn-hangzhou.cr.aliyuncs.com/alinux3/alinux3:220901.1
        steps:
          build_step:
            name: "安装 Maven 并构建"
            step: Command
            with:
              run: |
                if ! command -v java >/dev/null 2>&1; then
                  dnf install -y java-17-openjdk-devel
                fi
                if ! command -v mvn >/dev/null 2>&1; then
                  dnf install -y maven
                fi
                java -version
                mvn -version
                mvn -B clean package -DskipTests
```

## Private Registry Docker Build YAML

构建镜像并推送至自定义私有镜像仓库。当前仅支持 `certificate.type=usernamePassword`，不支持 `serviceConnection`。

```yaml
stages:
  build_stage:
    name: 镜像构建
    jobs:
      build_job:
        name: 构建任务
        steps:
          private_registry_docker_build:
            step: PrivateRegistryDockerBuild
            name: 镜像构建并推送至自定义镜像仓库
            with:
              artifact: my_image
              image: registry.cn-hangzhou.aliyuncs.com/ns/demo:1.0
              certificate:
                type: usernamePassword
                username: <your-registry-username>
                password: <your-registry-password>
              dockerfilePath: Dockerfile
              contextPath: .
              noCache: false
              variables:
                - key: PROFILE
                  value: prod
              buildkitVersion: v0.8.0
```

## Validation Error Codes

| code | 含义 |
|---|---|
| `SPEC_REQUIRED` | YAML 内容为空。 |
| `SPEC_INVALID` | YAML/JSON 格式错误或无法解析。 |
| `SOURCE_COUNT_UNSUPPORTED` | 当前版本最多支持一个代码源。 |
| `SOURCE_TYPE_REQUIRED` | 代码源类型为空。 |
| `SOURCE_TYPE_UNSUPPORTED` | 当前版本仅支持 `gitlab` 代码源。 |
| `STAGE_REQUIRED` | 缺少 stage 或 stage 配置为空。 |
| `JOB_REQUIRED` | 缺少 job 或 job 配置为空。 |
| `STEP_REQUIRED` | 缺少 step 或 step 配置为空。 |
| `SOURCE_ID_INVALID` / `STAGE_ID_INVALID` / `JOB_ID_INVALID` / `STEP_ID_INVALID` | ID 格式错误。 |
| `JOB_ID_DUPLICATE` | jobId 全局重复。 |
| `STEP_ID_DUPLICATE` | stepId 全局重复。 |
| `RUNS_ON_GROUP_REQUIRED` | `runsOn.group` 为空。 |
| `RUNS_ON_GROUP_UNSUPPORTED` | 当前版本不支持该执行资源池。 |
| `RUNS_ON_CONTAINER_REQUIRED` | `runsOn.container` 为空。 |
| `STEP_TYPE_NOT_SUPPORTED` | 未注册的 step 类型。 |
| `STEP_TYPE_UNSUPPORTED` | 已识别但当前版本还不能执行的 step 类型。 |
| `PARAM_REQUIRED` | 必填参数缺失，例如 `Command.with.run`、`CodeMerge.with.baseBranch`、`CodeMerge.with.targetBranch`、`APPROVAL.with.processDefinitionKey`、K8s 部署参数、`PrivateRegistryDockerBuild.with.artifact/image/certificate/dockerfilePath`。 |
| `PARAM_VALUE_UNSUPPORTED` | 参数值当前版本不支持，例如 `K8sImageUpgrade.with.workloadKind` 不是 `Deployment`，或 `PrivateRegistryDockerBuild.with.certificate.type` 不是 `usernamePassword`。 |
| `PARAM_ENV_KEY_INVALID` | 环境变量名格式错误。 |
| `PARAM_TYPE_INVALID` | 参数类型错误。 |
| `JOB_NEEDS_NOT_FOUND` | 依赖任务不存在。 |
| `JOB_NEEDS_SELF` | 任务依赖自身。 |
| `JOB_NEEDS_CYCLE` | 任务依赖存在循环。 |
| `FAIL_STRATEGY_UNSUPPORTED` | 当前版本不支持该失败策略。 |

## Current Limits

- 第一版执行 `Command`、`CodeMerge`、`APPROVAL`、`K8sDeploy`、`K8sImageUpgrade` 和 `PrivateRegistryDockerBuild` step。
- `SetupJava`、`SetupMavenSettings`、`UnitTestReport`、`ArtifactUpload`、`JavaP3CScan` 可作为后续扩展类型，但当前发布校验会拒绝执行。
- K8s 部署当前仅支持 Deployment，且使用应用环境绑定的 Kubernetes Namespace，不支持节点级 namespace 覆盖。
- 后端当前不会自动拉取 `runsOn.container` 镜像。
- 配置 `sources` 后，后端会在每个 job 的独立 workspace 中自动 checkout 源码；未配置 `sources` 且 run 关联应用时，使用应用实体里的仓库配置 checkout；未关联应用时 workspace 为空目录。
- `diagramJson` 暂由前端自管，后端只保存，不参与 YAML 执行。
