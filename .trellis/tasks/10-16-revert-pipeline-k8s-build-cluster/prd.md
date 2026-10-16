# 回退流水线到平台本机 Docker 构建

## Goal

把 DevOps 流水线的构建执行从「Kubernetes 构建集群（构建 Pod）」回退为「平台服务所在机器的本地 Docker 执行」，并彻底删除这次迁移引入的 K8s 构建执行代码，让流水线行为回到 K8s 构建集群改造之前。

回退后只有一条执行路径：`PipelineExecutionEngine` → `DockerPipelineJobRuntimeManager` → 平台宿主机 Docker daemon（`yudao.devops.docker.host`）。

## Background

### 迁移的引入点

- 迁移主体是 `fe0d9ea6f feat(devops): support Kubernetes build cluster for pipeline execution`（2026-10-10）。commit message 明确写了「将流水线构建从平台本地执行迁移到 Kubernetes 构建集群：源码准备、Checkout、CodeMerge、Command、KanikoBuild 全部在构建 Pod 内完成，平台只负责编排、审批、状态管理与 K8s API 调用」。
- 后续叠加的同类改动：`807119d8e`（kaniko-image 镜像源）、`76bb9223a`（构建 Pod 资源配额）、`d7560dadc`（K8s exec umask 与构建上下文权限）、`d9bfd9945`（共享 PVC subPath 复用 CodeMerge 工作区）。
- 归档任务：`.trellis/tasks/archive/2026-08/08-31-devops-global-config-build-cluster/`（原 PRD 仅覆盖 `DevopsConfig` 类结构，实际实现散落在上述提交）。

### 当前状态（回退前）

- 双后端并存：`PipelineJobRuntimeManagerFactory` 按 `runs-on.group` 前缀（`kubernetes/` vs `local-docker/`）路由，`default-executor` 在两份 `application.yaml` 中都写死为 `KUBERNETES`。
- 本地 Docker 路径仍完整存在：`DockerPipelineJobRuntimeManager`、`DockerPipelineCommandExecutor`、`PrivateRegistryDockerBuildStepHandler`（平台 docker-java 本地构建并推送）。
- K8s 构建专属增量：`Checkout` 步骤、`KanikoBuild` 步骤、`K8sPipelineJobRuntimeManager`、`K8sCommandExecutor`、`K8sBuildPodCleanupJob`、`RoutingPipelineCommandExecutor`、`RuntimeGitWorkspaceSupport` / `GitCheckoutScript` / `GitWorkspaceRuntimeRef`、`devops.config.build.cluster` 配置。
- 本仓库无前端代码；步骤类型由后端 `PipelineNodeRegistryServiceImpl` 注册后下发。

### 已确认的决策

- **回退边界：彻底回退**。删除 K8s 构建执行代码与配置，而不是保留代码只切默认值。理由：双路径并存会让「以为在本机构建、实际路由到 Pod」的误判持续存在，且 CodeMerge 双分支、Pod 清理定时任务、集群配置都要继续维护。代价是以后再上 K8s 构建需重做，相关代码在 git 历史中完整保留。
- **「本机」的定义**：平台服务（`yudao-server` / `devops-server`）所在宿主机的本地 Docker daemon，通过 `yudao.devops.docker.host` 配置（`application-local.yaml` 中为 `unix:///.../docker.sock`）。不是被部署业务应用所在的机器。
- **存量定义的兼容策略：硬删除**。`Checkout` / `KanikoBuild` 从注册表注销，handler 删除。引用它们的定义校验失败并报未支持步骤，由人工改为去掉 `Checkout`、把 `KanikoBuild` 换成 `PrivateRegistryDockerBuild`、组改为 `local-docker/default`。不做「墓碑式」兼容提示，也不保留 `Checkout` 空操作。

## Requirements

- R1：流水线 job 的执行后端只保留平台本机 Docker，不再创建 K8s 构建 Pod；`PipelineJobRuntimeManager` 只有 `DockerPipelineJobRuntimeManager` 一个实现。
- R2：`runs-on.group` 只接受 `local-docker/...` 前缀；`kubernetes/...` 不再是合法执行池，校验报运行集群格式错误。
- R3：源码检出回到平台侧语义：由 `GitlabPipelineSourceWorkspacePreparer` 用本机 `GitCommandExecutor` 在工作区完成，不依赖 Pod 内 git；删除 `Checkout` 步骤类型与 `GitCheckoutScript` / `RuntimeGitWorkspaceSupport` / `GitWorkspaceRuntimeRef`。
- R4：镜像构建回到平台本机 Docker 路径（`PrivateRegistryDockerBuild`）；删除依赖 Pod 内 kaniko sidecar 的 `KanikoBuild` 步骤。
- R5：CodeMerge 回到纯 `PLATFORM` 单路径，删除 `KUBERNETES` 分支、Pod 内冲突读写与远端工作区注册。
- R6：删除 `devops.config.build.cluster` 相关配置类与两份 `application.yaml` 中的 `devops.config` 块，避免配置残留导致误路由。
- R7：删除 K8s 构建专属的错误码、文档、脚本与测试，保持模块可编译、测试通过。
- R8：保留与执行后端无关的既有能力：`K8sDeploy` / `K8sImageUpgrade` / 部署单 / 应用编排 / 环境管理 / Pod 日志与终端；`BuildContextPermissions`（docker-java 路径仍在用）、`ShellQuoting`、`SensitiveKeyUtils`；CodeMerge 的 `--filter=blob:none` partial clone 与 run workspace 保留 `.git` 的复用优化。
- R9：清理迁移残留的调试输出（`PipelineSpec` 中的 `System.out.println`）。

## Acceptance Criteria

- [ ] AC1：`PipelineJobRuntimeManager` 只有 `DockerPipelineJobRuntimeManager` 一个实现，`PipelineJobRuntimeManagerFactory` 与 `RoutingPipelineCommandExecutor` 已删除；流水线运行不创建任何 K8s 构建 Pod。
- [ ] AC2：`Checkout` / `KanikoBuild` 已从 `PipelineNodeRegistryServiceImpl` 注销且 handler 删除；引用它们的定义校验失败并给出未支持步骤错误。
- [ ] AC3：`runs-on.group` 非 `local-docker/` 前缀时校验失败；`PipelineSpecValidationServiceImpl` 中不再有 kubernetes 分支与 `DevopsConfig` 依赖。
- [ ] AC4：`DevopsConfig`、`DevopsConfiguration`、`application-devops-k8s-example.yml` 已删除，`yudao-module-devops-server` 与 `yudao-server` 两份 `application.yaml` 中不再有 `devops.config` 块与 `default-executor: KUBERNETES`。
- [ ] AC5：CodeMerge 只有平台侧单一路径，`CodeMergeService` / `CodeMergeContext` / `GitWorkspaceService` 中不再有 runtime 相关分支。
- [ ] AC6：`PipelineJobRuntimeManager` 恢复为迁移前的接口（仅 `createRuntime` / `destroyRuntime`），`PipelineJobRuntime`、`PipelineWorkspace`、`PipelineCommandContext`、`PipelineExecutionContext` 中 K8s 专用字段已移除。
- [ ] AC7：`ErrorCodeConstants` 中 `1_011_013_*` 构建集群错误码已删除；K8s 构建专属文档（`K8S_BUILD_*.md`、`REFACTOR_PLAN.md`、`REFACTOR_SUMMARY.md`、`.devops/*`）已删除。
- [ ] AC8：`mvn -pl yudao-module-devops/yudao-module-devops-server -am test` 通过；`mvn -pl yudao-module-devops/yudao-module-devops-server -am -DskipTests compile` 与 `mvn -pl yudao-server -am -DskipTests compile` 通过。
- [ ] AC9：残留扫描无命中（除部署类 `K8sDeploy` / `K8sImageUpgrade`）：`K8sPipelineJobRuntimeManager`、`PipelineJobRuntimeManagerFactory`、`KanikoBuild`、`TYPE_CHECKOUT`、`RuntimeGitWorkspaceSupport`、`devops.config`。
- [ ] AC10：`.trellis/spec/backend/devops-pipeline-guidelines.md` 已删除 Pod 资源配额场景、修订构建上下文权限场景、移除 `WORKSPACE_SUB_PATH_KEY` 与共享 PVC subPath 条目，并记录「构建执行后端只保留平台本机 Docker」的决策。
- [ ] AC11：`K8sDeploy` / `K8sImageUpgrade` / 部署单 / 应用编排 / 环境管理相关测试保持通过，未被本次回退破坏。

## Out Of Scope

- 与构建执行无关的 K8s 能力：`K8sDeploy` / `K8sImageUpgrade` 部署步骤、部署单与 rollout 健康门禁、应用编排、环境管理、Pod 日志与终端。
- 前端仓库改动（步骤面板项随注册表下发生效，前端不在本仓库）。
- 历史 run 数据回填：`dev_pipeline_run_log.runtime_type` / `executor_group` 中的 `KUBERNETES` 历史值保留只读展示。
- 集群侧人工清理（遗留构建 Pod、`pipeline-shared-workspace` PVC、`devops-build` 命名空间、git credentials Secret、builder 镜像）与存量流水线定义的改写，记录在 `implement.md` 收尾项。
- 重新实现集群构建能力。

## Notes

- 改动集中在一次提交内，回滚即 `git revert`；无数据库结构变更与数据迁移。
- 技术设计与逐文件改动清单见 `design.md`，执行顺序与验证命令见 `implement.md`。
