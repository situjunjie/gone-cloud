# 回退流水线到平台本机 Docker 构建 - 技术设计

## 1. 目标形态

回退后只有一条执行路径：

```
PipelineExecutionEngine
  └── PipelineJobRuntimeManager (唯一实现: DockerPipelineJobRuntimeManager)
        └── 本机 Docker daemon (DockerClientFactory / yudao.devops.docker.host)
              └── 容器内 PipelineCommandExecutor (唯一实现: DockerPipelineCommandExecutor)
```

`runs-on.group` 只接受 `local-docker/...`；`PLATFORM` 步骤（CodeMerge / Approval / K8sDeploy / K8sImageUpgrade / ChangePublishFinalize / PrivateRegistryDockerBuild）在工作区里由平台进程直接处理；源码检出由 `GitlabPipelineSourceWorkspacePreparer` 用本机 `GitCommandExecutor` 完成。

## 2. 删除 / 保留清单

### 2.1 删除（K8s 构建执行专用）

运行时与执行器：

| 文件 | 理由 |
|---|---|
| `framework/pipeline/runtime/K8sPipelineJobRuntimeManager.java` | 创建 builder Pod / Kaniko sidecar |
| `framework/pipeline/runtime/K8sCommandExecutor.java` | pods/exec |
| `framework/pipeline/runtime/K8sBuildPodCleanupJob.java` | Pod 到期回收 |
| `framework/pipeline/runtime/PipelineJobRuntimeManagerFactory.java` | Docker/K8s 路由，唯一实现后无意义 |
| `framework/pipeline/runtime/RoutingPipelineCommandExecutor.java` | `@Primary` 按 runtimeType 路由 |

git 下沉到 Pod：

| 文件 | 理由 |
|---|---|
| `framework/git/GitCheckoutScript.java` | 生成 Pod 内 clone 脚本（平台侧走 `GitCommandExecutor`） |
| `framework/git/RuntimeGitWorkspaceSupport.java` | 把 git/文件读写转发到 Pod |
| `framework/git/GitWorkspaceRuntimeRef.java` | Pod 工作区引用 |

配置：

| 文件 | 理由 |
|---|---|
| `framework/config/DevopsConfig.java` | 仅承载 `default-executor` + `build.cluster` |
| `framework/config/DevopsConfiguration.java` | 上者的 `@ConfigurationProperties` 装配 |
| `resources/application-devops-k8s-example.yml` | K8s 集群示例配置 |
| `yudao-module-devops-server/src/main/resources/application.yaml` 的 `devops.config` 块 | `default-executor: KUBERNETES` + cluster |
| `yudao-server/src/main/resources/application.yaml` 的 `devops.config` 块 | 同上，两份独立副本 |

步骤：

| 文件 | 理由 |
|---|---|
| `service/pipeline/execution/handler/CheckoutStepHandler.java` | 步骤类型 `Checkout`，源码检出已由平台侧 preparer 负责 |
| `service/pipeline/execution/handler/KanikoDockerBuildStepHandler.java` | 步骤类型 `KanikoBuild`，依赖 Pod 内 kaniko sidecar |

文档与脚本：

| 文件 | 理由 |
|---|---|
| `yudao-module-devops/K8S_BUILD_CLUSTER_GUIDE.md` | 构建集群部署指南 |
| `yudao-module-devops/K8S_BUILD_IMPLEMENTATION_SUMMARY.md` | 同上 |
| `yudao-module-devops/K8S_BUILD_SINGLE_NODE_GUIDE.md` | 同上 |
| `yudao-module-devops/K8S_BUILD_TEST_PLAN.md` | 同上 |
| `yudao-module-devops/K8S_BUILD_UI_TEST_NOTES.md` | 同上 |
| `REFACTOR_PLAN.md` / `REFACTOR_SUMMARY.md` | 迁移方案与总结 |
| `.devops/DEPLOYMENT_GUIDE.md` / `.devops/builder.Dockerfile` / `.devops/build-builder-image.sh` / `.devops/k8s-build-example-pipeline.yaml` / `.devops/k8s-git-credentials-secret.yaml` | builder Pod 镜像与集群凭证 |

测试：`K8sPipelineJobRuntimeManagerTest`、`K8sCommandExecutorTest`、`K8sBuildPodCleanupJobTest`、`RoutingPipelineCommandExecutorTest`、`CheckoutStepHandlerTest`、`KanikoDockerBuildStepHandlerTest`、`RuntimeGitWorkspaceSupportTest`、`GitCheckoutScriptTest`、`DevopsConfigTest`。

错误码：`ErrorCodeConstants` 中 `1_011_013_000` ~ `1_011_013_003`（构建集群 / Pod 创建 / Pod 就绪 / 命令执行）。

### 2.2 保留（不是 K8s 构建专属）

| 保留项 | 理由 |
|---|---|
| `DockerPipelineJobRuntimeManager` / `DockerPipelineCommandExecutor` / `DockerClientFactory` / `DockerClientConfiguration` | 回退目标本体 |
| `LocalPipelineWorkspaceService` / `PipelineWorkspaceService` / `PipelineCacheConfig*` / `PipelineCacheMount` | 本地工作区与依赖缓存，与执行后端无关 |
| `PrivateRegistryDockerBuildStepHandler` | 平台 docker-java 本地构建并推送，回退后的镜像构建入口 |
| `BuildContextPermissions` + `ShellQuoting` | `BuildContextPermissions` 仍被 `PrivateRegistryDockerBuildStepHandler` 使用；`ShellQuoting` 被 `BuildContextPermissions` 使用 |
| `SensitiveKeyUtils` | 被 `PipelineExecutionEngine` 与 `DeploymentOrderServiceImpl` 使用，与构建后端无关 |
| `GitWorkspaceServiceImpl` 的 `--filter=blob:none` partial clone | 后端无关的 CodeMerge 传输量优化 |
| `LocalPipelineWorkspaceService.resetRunWorkspace` 保留 `.git` | 本地 run workspace 同样按 app-env-definition 稳定，跨运行复用 Git 对象库依然成立 |
| `docker/pipeline-builder/*` 与 `export/import-image-*.sh` 的 umask 修正 | `local-docker/default` 执行池的构建镜像与脚本 |
| `K8sDeploy` / `K8sImageUpgrade` / 部署单 / 应用编排 / 环境管理 / Pod 日志与终端 | 与构建执行无关的 K8s 能力 |

### 2.3 需要改代码而非删文件的位置

| 位置 | 改动 |
|---|---|
| `PipelineExecutionEngine` | 注入 `PipelineJobRuntimeManager`（唯一 Bean）替代工厂；`finally` 恢复无条件 `destroyRuntime(runtime)`；删除 `isKubernetesJob(job)` 对 CodeMerge 的 runtime 提升、`cleanupRun`、`restoreRuntime` 分支；`prepareSourceOnce` 只走平台侧 |
| `PipelineJobRuntimeManager` | 删除迁移新增的 `destroyRuntime(runtime, failed)`、`cleanupRun`、`restoreRuntime` 默认方法 |
| `PipelineJobRuntime` | 删除 `metadata`（仅供 K8s 工作区/环境传递） |
| `PipelineWorkspace` + `PipelineWorkspaceConstants` | 删除 `metadata` 与 `WORKSPACE_SUB_PATH_KEY`（K8s 挂载 subPath 专用） |
| `PipelineCommandContext` | 删除 `containerName` / `workingDirectory`（Pod 内执行专用） |
| `PipelineExecutionContext` | 删除 `remoteSourcePrepared`、`isKubernetesRuntime()` 分支与 `remoteSourceFlag()` |
| `PipelineSourceWorkspacePreparer` | 删除带 `PipelineJobRuntime` 的重载 |
| `GitlabPipelineSourceWorkspacePreparer` | 删除 Pod 分支（`GitCheckoutScript` + `selectCommandExecutor`），只保留平台侧 `GitCommandExecutor` 路径 |
| `CodeMergeStepHandler` | 只保留平台侧单次调用，删除 `KUBERNETES` 分支 |
| `CodeMergeService` | 删除带 `PipelineJobRuntime` 的重载、`GitWorkspaceRuntimeRef` 构造与 `registerRuntimeWorkspace` |
| `CodeMergeContext` | 删除 `runtimeRef` |
| `GitWorkspaceService` / `GitWorkspaceServiceImpl` | 删除 `registerRuntimeWorkspace` 与 `externalWorkspaces` / `isRemote` 分支 |
| `PipelineExecutionServiceImpl` | 删除 `registerExternalWorkspaceIfPresent` 的 `runtimeRef` 分支 |
| `PipelineNodeRegistryServiceImpl` | 删除 `TYPE_CHECKOUT` / `TYPE_KANIKO_BUILD` 常量与 `registerNode` |
| `PipelineSpecValidationServiceImpl` | `runsOn.group` 只接受 `local-docker/`；删除 kubernetes 分支、`validateKanikoJob`、`Checkout` / `KanikoBuild` 参数校验与 `isSupportedStep` 项；删除 `DevopsConfig` 注入 |
| `PipelineSpec` | 删除迁移残留的 `System.out.println` 调试输出 |
| `PIPELINE_YAML_SPEC.md` | 无需改动：该文档从未记录 `Checkout` / `KanikoBuild`（已核对） |

## 3. 兼容与数据影响

- 无数据库结构变更，无 SQL 迁移。
- `dev_pipeline_run_log.runtime_type` / `executor_group` 中历史的 `KUBERNETES` / `kubernetes/...` 值保留只读展示，不做数据回填。
- 存量流水线定义若引用了 `Checkout`、`KanikoBuild` 或 `kubernetes/` 组，回退后校验失败并报未支持步骤 / 运行集群格式错误，需人工改为 `local-docker/default` 并去掉 `Checkout`、把 `KanikoBuild` 换成 `PrivateRegistryDockerBuild`。这是已确认接受的行为（用户选择硬删除方案）。
- 历史 K8s 构建 Pod / PVC 不由此代码回收，需人工清理（记录在 implement.md 的收尾项）。

## 4. 权衡

- 选择删除而不是保留开关：双路径并存会让「以为在本机构建、实际路由到 Pod」这类误判持续存在，且 CodeMerge 的双分支、Pod 清理定时任务、集群配置都要继续维护。
- 代价：以后再上 K8s 构建需要重新实现；相关代码在 git 历史（`fe0d9ea6f` 及后续 4 个提交）中完整保留，可作为重建依据。
- 保留 `--filter=blob:none` 与 `.git` 复用：这两条在本地路径下同样有效，不属于 K8s 专属逻辑。

## 5. 回滚方式

改动集中在一次提交内，回滚即 `git revert` 该提交；无数据迁移，无需额外回滚步骤。

## 6. 验证策略

- 编译：`mvn -pl yudao-module-devops/yudao-module-devops-server -am -DskipTests compile`，以及 `yudao-server` 模块编译。
- 测试：`mvn -pl yudao-module-devops/yudao-module-devops-server -am test`，重点更新 `PipelineExecutionEngineTest`、`PipelineSpecValidationServiceImplTest`、`PipelineNodeRegistryServiceImplTest`、`GitlabPipelineSourceWorkspacePreparerTest`、`GitWorkspaceServiceImplTest`、`LocalPipelineWorkspaceServiceTest`、`PipelineExecutionContextTest`。
- 静态核对：`grep -rn "KUBERNETES\|K8sPipelineJobRuntimeManager\|KanikoBuild\|TYPE_CHECKOUT\|RuntimeGitWorkspaceSupport"` 在 main 源码中应无残留（部署类 `K8sDeploy` / `K8sImageUpgrade` 除外）。
