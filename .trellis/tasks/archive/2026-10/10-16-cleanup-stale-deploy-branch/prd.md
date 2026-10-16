# 重新签出部署分支时清理旧部署分支

## Goal

发布提交（`POST /devops/application/release/submit-branch`）在判定需要**重新签出**新的部署分支时，
把被替换掉的旧部署分支从远端仓库删除，避免同一应用环境反复重新签出后远端残留无人引用的垃圾分支。

## Confirmed Facts（已由代码/规范核实）

### 何时会"重新签出"

`ApplicationServiceImpl#resolveDeployBranch`（`ApplicationServiceImpl.java:1581`）是唯一的部署分支决策点：

```java
PipelineRunDO latestReleaseRun = pipelineRunMapper.selectLatestByApplicationEnvIdAndBranchPrefix(
        applicationEnv.getId(), DEPLOY_BRANCH_PREFIX);
if (latestReleaseRun != null && isNotBlank(latestReleaseRun.getBranchName())
        && CollUtil.isEmpty(unmountedChangeIds)
        && !isBaseBranchOutdated(application, latestReleaseRun)) {
    return latestReleaseRun.getBranchName();                       // 复用
}
return DEPLOY_BRANCH_PREFIX + sanitizeRefPart(environment.getEnvKey()) + "/"
        + DEPLOY_BRANCH_TIMESTAMP_FORMATTER.format(now);           // 新建
```

走到"新建"分支的两个触发条件：

1. `unmountedChangeIds` 非空 —— 本次目标变更集合剔除了已挂载变更；
2. `isBaseBranchOutdated(application, latestReleaseRun)`（`ApplicationServiceImpl.java:1667`）为 true ——
   即 `run.baseBranchName == application.defaultBranchName` 且 `run.baseCommitSha != application.baseBranchLatestCommitSha`，
   也就是"主干分支合并了最新提交"。

调用时机在 `submitApplicationReleaseBranch`（`:420`）中，位于 `stopActivePipelineRuns`（`:388`）**之后**，
即旧运行此时已被取消。

### 当前没有任何删除动作

- `resolveDeployBranch` 只拼新分支名，不删旧分支。
- `GitWorkspaceServiceImpl#prepareWorkspaceInPlace`（`GitWorkspaceServiceImpl.java:104`）对不存在的部署分支
  执行 `git checkout -B <deployBranch> origin/<baseBranch>`，纯创建。
- `GitWorkspaceServiceImpl#pushDeployBranch`（`:241`）只有 `git push origin HEAD:refs/heads/<branch>`，无 `--delete`。
- 全模块唯一的远端删除能力是 `RepositoryProviderServiceImpl#deleteRepositoryBranch`（`:158`），
  调用点仅 `ChangeServiceImpl` 两处：
  - `deleteRemoteChangeBranch`（`:275`）删变更自己的 `feat/*`；
  - `deleteRemoteBranch(application, deployBranchName)`（`:277`）删**本次 run 自己**的部署分支。
- `:277` 只在 `finalizePublishedChanges`（`ChangeServiceImpl.java:238`）里执行，入口是流水线 `ChangePublishFinalize`
  步骤（`ChangePublishFinalizeStepHandler.java:48`，传入 `ctx.getRun().getBranchName()`）。
- 因此"重新签出"路径恰好绕过了发布收尾，旧部署分支永久残留。
- `service/application` 与 `job` 下无任何定时任务/过期清理逻辑（搜 `过期`/`Expired`/`Scheduled` 无命中）。

### 部署分支命名不含 appKey —— 但不会跨应用冲突

`.trellis/spec/backend/devops-pipeline-guidelines.md:799` 明确规定：

> Deploy branch naming for new branches uses `release/{envKey}/{yyyyMMddHHmmss}`.
> The application dimension is supplied by the repository/application environment; **do not include `appKey`** in new release branch names.

`release/<envKey>/` 表面上是跨应用共享的命名空间，但实测不会冲突：

- `EnvironmentServiceImpl#validateEnvKeyUnique`（`EnvironmentServiceImpl.java:236`）保证 `envKey` **全局唯一**；
- `ApplicationServiceImpl#validateApplicationUnique`（`:1664`）保证 `(repositoryProviderId, repoIdentifier)`
  **唯一属于一个应用**（`APPLICATION_REPO_IDENTIFIER_DUPLICATE`），即一个仓库最多对应一个应用；
- `validateApplicationEnvList`（`:1676`）保证同一应用内 `envId` 不重复。

因此 `release/<envKey>/` 在任一仓库内唯一指向「一个应用的某一个环境」。
但**删除仍必须按 `application_env_id` 过滤本地 run 记录**，不得扫描远端前缀 —— 本地记录才是归属证明。

### 部署单不引用分支名

`service/deployment/` 与 `dal/dataobject/deployment/` 下无任何 `branchName` 字段引用；
部署单记录的是镜像等制品信息。删除 git 分支不会影响已部署版本或已构建镜像。

### 删除操作已有幂等语义

`deleteRepositoryBranch` 对 GitLab 404 直接 `return`（视为成功），非 404 抛
`REPOSITORY_PROVIDER_GITLAB_BRANCH_DELETE_FAIL`。历史任务
`.trellis/tasks/archive/2026-06/06-22-devops-publish-finalize-deploy-branch/prd.md` 已确立
"删除操作保持幂等 / 已删除分支按成功处理"的契约。

### 没有功能依赖旧部署分支存活

- 无"按旧部署分支回滚/重新部署"能力；`PipelineController#rollback` 是流水线**定义版本**回滚，与部署分支无关。
- `PipelineRunDO.branchName` 只被当前 run 的消费方读取（`PipelineVariableResolver:86`、
  `PipelineApprovalDeployDetailBuilder:96`、`CodeMergeService:735` 等），不回溯历史分支。

### 工作区现状（前置依赖）

工作区存在**未提交**改动，实现了本任务相邻的 `stopActivePipelineRuns` 契约
（`ApplicationServiceImpl.java`、`ApplicationServiceImplTest.java`），
而该契约已提交在 `.trellis/spec/backend/devops-pipeline-guidelines.md`。
本任务将改动同一对文件，提交时会一并带上这批改动。

## Requirements

- `submit-branch` 在 `resolveDeployBranch` 判定为"新建部署分支"时，必须删除被替换的旧部署分支。
- `submit-branch` 在 `resolveDeployBranch` 判定为"新建部署分支"时，必须清理该应用环境**全部历史遗留的部署分支**
  （不只被本次替换的那一条）。
- 清理目标必须是**本应用环境 run 记录中的部署分支**，按 `application_env_id` + `release/` 前缀查询后去重，
  不得通过远端前缀扫描推断归属。
- 复用分支（不新建）时不得删除任何分支。
- 删除操作保持幂等：分支不存在视为成功（`deleteRepositoryBranch` 已对 404 直接返回）。
- 清理范围严格限定为本应用环境的部署分支，不得影响其他应用环境的 `release/` 分支。
- 删除失败仅记录日志（WARN，携带 `applicationEnvId` + `branchName`），不阻断本次发布提交；
  下次重新签出时按全量查询会再次尝试，具备自愈性。

## Open Questions

（无。Q1、Q2 均已决策，见 Question Log。）

## Acceptance Criteria

- [x] AC1：基线分支推进导致重新签出时，该应用环境**全部历史** `release/` 部署分支被远端删除，新分支被创建并推送。
      → `testSubmitApplicationReleaseBranch_rebuildCleansStaleDeployBranches`
- [x] AC2：剔除已挂载变更导致重新签出时，行为同 AC1。
      → `testSubmitApplicationReleaseBranch_unmountRebuildCleansStaleDeployBranches`
- [x] AC3：复用分支路径（不新建）下，`deleteRepositoryBranch` 完全不被调用。
      → `testSubmitApplicationReleaseBranch_reuseKeepsDeployBranch`
- [x] AC4：旧分支远端已不存在时，提交仍然成功（幂等）。
      → `deleteRepositoryBranch` 对 404 直接 return（`RepositoryProviderServiceImplTest#testDeleteRepositoryBranch_notFoundIgnored`）；
      其余异常被 `cleanupStaleDeployBranches` 捕获，见 `testSubmitApplicationReleaseBranch_cleanupFailureIgnored`
- [x] AC5：清理入参只包含本应用环境 run 记录中的分支名；其他应用环境的部署分支不受影响。
      → `testSubmitApplicationReleaseBranch_rebuildCleansStaleDeployBranches` 中 `times(2)` + `never()` 断言
- [x] AC6：历史 run 中重复出现的同一分支名只删除一次（去重）。
      → `testSubmitApplicationReleaseBranch_rebuildCleansDuplicateBranchesOnce`
- [x] AC7：单测覆盖重新签出全量清理、复用不删除、重复分支名去重、删除失败不阻断提交四条路径。
      → 上述 5 个新增用例；`ApplicationServiceImplTest` 40 tests 全绿
- [x] AC8：`.trellis/spec/backend/devops-pipeline-guidelines.md` 中部署分支生命周期契约与实现一致。
      → 第 3 节 Contracts（4 条新增）、第 4 节错误矩阵、第 5 节 Bad Cases、第 6 节 Tests Required 已同步

## Out Of Scope

- 不改变部署分支命名规则（仍为 `release/{envKey}/{yyyyMMddHHmmss}`）。
- 不改变 `resolveDeployBranch` 的复用/新建判定条件。
- 不新增前端接口、请求参数或响应字段。
- 不改变发布收尾（`ChangePublishFinalize`）的删除语义。
- 不新增定时任务或后台清理作业：清理只发生在 `submit-branch` 重新签出路径上。
- 不清理 `upload-image/` 前缀的运行分支。

## Technical Notes

- 主要文件：
  - `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/service/application/ApplicationServiceImpl.java`
  - `yudao-module-devops/yudao-module-devops-server/src/test/java/cn/iocoder/yudao/module/devops/service/application/ApplicationServiceImplTest.java`
- 复用能力：`RepositoryProviderService#deleteRepositoryBranch`（`RepositoryProviderServiceImpl.java:158`），
  `ApplicationServiceImpl` 已注入 `repositoryProviderService`（`:160`）。
- 参考规范：`.trellis/spec/backend/devops-pipeline-guidelines.md`（部署分支契约）、
  `.trellis/spec/backend/devops-repository-guidelines.md`、`.trellis/spec/backend/error-handling.md`、
  `.trellis/spec/backend/logging-guidelines.md`。

## Question Log

- **Q1（已决策）**：清理范围 —— 用户选择「同时清理该应用环境历史遗留的多条」。
  实现上按 `application_env_id` + `release/` 前缀查出全部历史 run 的 `branchName`，去重后逐个删除。
  已核实 `envKey` 全局唯一 + 一仓库仅属一应用，故 `release/<envKey>/` 无跨应用冲突。
- **Q2（已决策）**：删除失败行为 —— 用户选择「记录日志继续」。
  循环内失败 `log.warn`（携带 `applicationEnvId` + `branchName` + 堆栈），不抛异常、不回滚提交；
  成功结果在循环后汇总一条 `log.info`（遵守 `logging-guidelines.md` 的批量循环 INFO 禁令）。
