# 回退流水线到平台本机 Docker 构建 - 实施计划

## 实施顺序

按「先断路由 → 再删实现 → 再清配置与文档 → 最后补测试」推进，每一步结束都保证可编译。

### 步骤 1：引擎与运行时接口回到单后端

- [ ] 1.1 `PipelineExecutionEngine`：把 `PipelineJobRuntimeManagerFactory` 换成单个 `PipelineJobRuntimeManager` 注入。
- [ ] 1.2 `PipelineExecutionEngine#executeJob`：删除 `isCodeMergeStep && pipelineJobRuntimeManagerFactory.isKubernetesJob(job)` 的 runtime 提升分支；`finally` 恢复无条件 `destroyRuntime(runtime)`。
- [ ] 1.3 `PipelineExecutionEngine`：删除 `pipelineJobRuntimeManagerFactory.cleanupRun(...)` 调用点（约 174、666 行）与 `ensureJobRuntime` 中的 `restoreRuntime` 分支。
- [ ] 1.4 `PipelineJobRuntimeManager`：删除 `destroyRuntime(runtime, failed)`、`cleanupRun`、`restoreRuntime` 默认方法。

### 步骤 2：删除 K8s 执行实现类

- [ ] 2.1 删除 `K8sPipelineJobRuntimeManager`、`K8sCommandExecutor`、`K8sBuildPodCleanupJob`、`PipelineJobRuntimeManagerFactory`、`RoutingPipelineCommandExecutor`。
- [ ] 2.2 确认 `DockerPipelineCommandExecutor` 成为唯一 `PipelineCommandExecutor` Bean，`PipelineExecutionEngine` 注入不产生歧义。
- [ ] 2.3 删除 `GitCheckoutScript`、`RuntimeGitWorkspaceSupport`、`GitWorkspaceRuntimeRef`；保留 `ShellQuoting`（`BuildContextPermissions` 仍在用）。

### 步骤 3：清理 CodeMerge 与源码准备的双路径

- [ ] 3.1 `CodeMergeStepHandler`：删除 `KUBERNETES` 分支，只保留平台侧调用。
- [ ] 3.2 `CodeMergeService`：删除带 `PipelineJobRuntime` 的重载、`GitWorkspaceRuntimeRef` 构造与 `registerRuntimeWorkspace` 调用。
- [ ] 3.3 `CodeMergeContext`：删除 `runtimeRef`。
- [ ] 3.4 `GitWorkspaceService` / `GitWorkspaceServiceImpl`：删除 `registerRuntimeWorkspace`、`externalWorkspaces`、`isRemote` 及其分支；保留 `--filter=blob:none` 与 `.git` 复用。
- [ ] 3.5 `PipelineExecutionServiceImpl#registerExternalWorkspaceIfPresent`：删除 `runtimeRef` 分支。
- [ ] 3.6 `GitlabPipelineSourceWorkspacePreparer`：删除带 `PipelineJobRuntime` 的 Pod 分支与 `PipelineJobRuntimeManagerFactory` 依赖；`PipelineSourceWorkspacePreparer` 删除重载。
- [ ] 3.7 `PipelineExecutionContext`：删除 `remoteSourcePrepared`、`isKubernetesRuntime()`、`remoteSourceFlag()`。
- [ ] 3.8 `PipelineJobRuntime` 删除 `metadata`；`PipelineWorkspace` 与 `PipelineWorkspaceConstants` 删除 `metadata` / `WORKSPACE_SUB_PATH_KEY`，`LocalPipelineWorkspaceService` 不再写入该 metadata（`resetRunWorkspace` 保留 `.git` 的行为不变）。
- [ ] 3.9 `PipelineCommandContext` 删除 `containerName` / `workingDirectory`。

### 步骤 4：删除步骤类型与校验分支

- [ ] 4.1 删除 `CheckoutStepHandler`、`KanikoDockerBuildStepHandler`。
- [ ] 4.2 `PipelineNodeRegistryServiceImpl`：删除 `TYPE_CHECKOUT` / `TYPE_KANIKO_BUILD` 常量与两处 `registerNode`。
- [ ] 4.3 `PipelineSpecValidationServiceImpl`：`validateRunsOn` 只接受 `local-docker/` 前缀；删除 kubernetes 分支、`validateKanikoJob`、`Checkout` / `KanikoBuild` 参数校验分支、`isSupportedStep` 中的两项；删除 `DevopsConfig` 注入与 import。
- [ ] 4.4 `PipelineSpec`：删除迁移残留的 `System.out.println` 调试输出。

### 步骤 5：清理配置、错误码、文档与脚本

- [ ] 5.1 删除 `DevopsConfig`、`DevopsConfiguration`、`application-devops-k8s-example.yml`。
- [ ] 5.2 从 `yudao-module-devops-server/src/main/resources/application.yaml` 与 `yudao-server/src/main/resources/application.yaml` 删除 `devops.config` 块（两份独立副本都要改）。
- [ ] 5.3 `ErrorCodeConstants`：删除 `1_011_013_000` ~ `1_011_013_003`。
- [ ] 5.4 删除 `yudao-module-devops/K8S_BUILD_*.md`（5 份）、`REFACTOR_PLAN.md`、`REFACTOR_SUMMARY.md`、`.devops/DEPLOYMENT_GUIDE.md`、`.devops/builder.Dockerfile`、`.devops/build-builder-image.sh`、`.devops/k8s-build-example-pipeline.yaml`、`.devops/k8s-git-credentials-secret.yaml`。
- [ ] 5.5 确认 `PIPELINE_YAML_SPEC.md` 无 `Checkout` / `KanikoBuild` 内容（已核对，预期无需改动）。

### 步骤 6：测试

- [ ] 6.1 删除 K8s 构建相关测试类（见 design.md 2.1）。
- [ ] 6.2 更新 `PipelineExecutionEngineTest`：移除 `kubernetes/default` 用例与工厂相关 mock，恢复单 manager 断言。
- [ ] 6.3 更新 `PipelineSpecValidationServiceImplTest`、`PipelineNodeRegistryServiceImplTest`：移除 Checkout / Kaniko / kubernetes 组用例，补一条非 `local-docker/` 组被拒的断言。
- [ ] 6.4 更新 `GitlabPipelineSourceWorkspacePreparerTest`、`GitWorkspaceServiceImplTest`、`LocalPipelineWorkspaceServiceTest`、`PipelineExecutionContextTest`：删除 Pod/远端工作区与 `WORKSPACE_SUB_PATH_KEY`、`remoteSourcePrepared` 断言。
- [ ] 6.5 检查其余测试是否引用已删除类（`DevopsConfig`、工厂、`ShellQuoting` 之外的 K8s 类）。

### 步骤 7：验证

- [ ] 7.1 `mvn -pl yudao-module-devops/yudao-module-devops-server -am -DskipTests compile`
- [ ] 7.2 `mvn -pl yudao-module-devops/yudao-module-devops-server -am test`
- [ ] 7.3 `mvn -pl yudao-server -am -DskipTests compile`（确认两份 yaml 与装配无残留）
- [ ] 7.4 残留扫描：`grep -rn "K8sPipelineJobRuntimeManager\|PipelineJobRuntimeManagerFactory\|KanikoBuild\|TYPE_CHECKOUT\|RuntimeGitWorkspaceSupport\|devops.config" yudao-module-devops yudao-server/src/main/resources`，除部署类 `K8sDeploy` / `K8sImageUpgrade` 外应无命中。

### 步骤 8：spec 更新（Phase 3.3）

- [ ] 8.1 `.trellis/spec/backend/devops-pipeline-guidelines.md`
  - 删除 `Scenario: Pipeline Build Resource Sizing`（Pod 资源配额，整段 K8s 专属）。
  - 修订 `Scenario: Build Context Permissions`：保留 docker-java 路径与 `docker/pipeline-builder/*.sh` 的 umask 契约，删除 `K8sCommandExecutor` / `KanikoDockerBuildStepHandler` 相关条目。
  - 删除 `Pipeline YAML Configuration` 中 `WORKSPACE_SUB_PATH_KEY` / 共享 PVC subPath 条目（约 55 行）与测试要求中的相关断言（约 761 行）。
  - 复核 `runs-on.group` 的表述：只支持 `local-docker/default`。
- [ ] 8.2 新增一条回退决策记录：构建执行后端只保留平台本机 Docker；如需重新引入集群构建，须作为新任务并保留单一后端原则。

## 验证命令

```bash
mvn -pl yudao-module-devops/yudao-module-devops-server -am -DskipTests compile
mvn -pl yudao-module-devops/yudao-module-devops-server -am test
mvn -pl yudao-server -am -DskipTests compile
```

## 风险与回滚点

| 风险 | 应对 |
|---|---|
| 步骤 1~3 之间接口改动跨多个文件，中途不可编译 | 按 1→2→3 顺序推进，每步结束只做编译检查，不做部分提交 |
| `PipelineExecutionEngine` 是核心调度，改动影响所有流水线 | 保留原有 `ensureJobRuntime` 懒创建语义；只去掉 K8s 分支 |
| 删除 `WORKSPACE_SUB_PATH_KEY` 影响 `LocalPipelineWorkspaceServiceTest` | 同步更新测试，不改变 `.git` 保留行为 |
| 存量流水线定义引用已删除步骤类型 | 已确认按硬删除处理，报错后人工改定义 |

回滚点：全部改动在一次提交内，回滚为 `git revert`；无数据库迁移。

## 收尾项（不在代码范围内，需人工执行）

- 清理集群中遗留的构建 Pod、`pipeline-shared-workspace` 等 PVC、`devops-build` 命名空间与 git credentials Secret。
- 删除已构建的 pipeline builder 镜像（如不再复用）。
- 存量流水线定义中 `Checkout` / `KanikoBuild` / `kubernetes/` 组的改写。
