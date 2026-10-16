# Design

## Scope

改动集中在 `ApplicationServiceImpl#submitApplicationReleaseBranch` 的部署分支决策与提交收尾，
外加一个 `PipelineRunMapper` 查询方法。不新增接口、不新增表字段、不引入定时任务。

## Current Data Flow

```
POST /devops/application/release/submit-branch
  → ApplicationServiceImpl#submitApplicationReleaseBranch
      1. validateApplicationEnvExists / validateTargetReleaseChanges
      2. validatePublishedPipelineDefinition + Version
      3. stopActivePipelineRuns(applicationEnvId, userId)      // 旧运行已取消
      4. 计算 unmountedChangeIds（本次目标集合剔除掉的已挂载变更）
      5. resolveDeployBranch(applicationEnv, application, unmountedChangeIds, now)
           → 复用：返回 latestReleaseRun.branchName
           → 新建：返回 release/{envKey}/{yyyyMMddHHmmss}
      6. buildPipelineRun(... deployBranch ...) → pipelineRunMapper.insert
      7. 更新 ChangeEnvDO 挂载状态 + lastPipelineRunId
      8. schedulePipelineStart(...)  // 事务提交后异步执行
```

异步 `CodeMerge` 步骤随后用 `run.branchName` 作为部署分支：
`GitWorkspaceServiceImpl#prepareWorkspaceInPlace` 从 `origin/<baseBranch>` 新建，
`finishCodeMerge` 调 `pushDeployBranch` 推送。

**缺口**：第 5 步"新建"分支时，`latestReleaseRun.branchName` 及更早的历史部署分支
不再被任何后续步骤引用（`finalizePublishedChanges` 只删本次 run 自己的分支），永久残留在远端。

## Change

### 1. `PipelineRunMapper` 新增列表查询

```java
default List<PipelineRunDO> selectListByApplicationEnvIdAndBranchPrefix(Long applicationEnvId, String branchPrefix) {
    return selectList(new LambdaQueryWrapperX<PipelineRunDO>()
            .eq(PipelineRunDO::getApplicationEnvId, applicationEnvId)
            .likeRightIfPresent(PipelineRunDO::getBranchName, branchPrefix));
}
```

与既有 `selectLatestByApplicationEnvIdAndBranchPrefix` 同构，只把 `selectOne + LIMIT 1` 换成 `selectList`。

### 2. `resolveDeployBranch` 返回决策结果而非裸分支名

当前返回 `String`，调用方无法知道走的是"复用"还是"新建"。引入嵌套 record：

```java
private record DeployBranchSelection(String branchName, boolean reused) {}
```

```java
private DeployBranchSelection resolveDeployBranch(ApplicationEnvDO applicationEnv, ApplicationDO application,
                                                 List<Long> unmountedChangeIds, LocalDateTime now) {
    PipelineRunDO latestReleaseRun = pipelineRunMapper.selectLatestByApplicationEnvIdAndBranchPrefix(
            applicationEnv.getId(), DEPLOY_BRANCH_PREFIX);
    if (latestReleaseRun != null
            && StrUtil.isNotBlank(latestReleaseRun.getBranchName())
            && CollUtil.isEmpty(unmountedChangeIds)
            && !isBaseBranchOutdated(application, latestReleaseRun)) {
        return new DeployBranchSelection(latestReleaseRun.getBranchName(), true);
    }
    EnvironmentDO environment = validateEnvironmentExists(applicationEnv.getEnvId());
    return new DeployBranchSelection(DEPLOY_BRANCH_PREFIX + sanitizeRefPart(environment.getEnvKey()) + "/"
            + DEPLOY_BRANCH_TIMESTAMP_FORMATTER.format(now), false);
}
```

复用/新建的**判定条件完全不变**，只是把布尔语义显式带出来，避免调用方重复推导条件
（重复推导会与 `unmountedChangeIds` / `isBaseBranchOutdated` 两处逻辑漂移）。
命名沿用文件内已有的嵌套静态类风格（`ReleaseBranches`）。

### 3. 新增 `cleanupStaleDeployBranches`，best-effort 清理

```java
private void cleanupStaleDeployBranches(ApplicationDO application, ApplicationEnvDO applicationEnv,
                                        String currentDeployBranch) {
    List<PipelineRunDO> historyRuns = pipelineRunMapper.selectListByApplicationEnvIdAndBranchPrefix(
            applicationEnv.getId(), DEPLOY_BRANCH_PREFIX);
    LinkedHashSet<String> staleBranches = historyRuns.stream()
            .map(PipelineRunDO::getBranchName)
            .filter(StrUtil::isNotBlank)
            .filter(branchName -> !branchName.equals(currentDeployBranch))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    if (CollUtil.isEmpty(staleBranches)) {
        return;
    }
    List<String> cleanedBranches = new ArrayList<>();
    for (String staleBranch : staleBranches) {
        try {
            repositoryProviderService.deleteRepositoryBranch(application.getRepositoryProviderId(),
                    application.getRepoIdentifier(), staleBranch);
            cleanedBranches.add(staleBranch);
        } catch (Exception ex) {
            // 清理失败不阻断发布提交：旧分支已无功能引用，下次重新签出时会再次尝试
            log.warn("[cleanupStaleDeployBranches][应用环境({}) 部署分支({}) 清理失败]",
                    applicationEnv.getId(), staleBranch, ex);
        }
    }
    if (CollUtil.isNotEmpty(cleanedBranches)) {
        log.info("[cleanupStaleDeployBranches][应用环境({}) 清理历史部署分支({})]",
                applicationEnv.getId(), cleanedBranches);
    }
}
```

### 4. 调用点

```java
DeployBranchSelection deployBranchSelection = resolveDeployBranch(applicationEnv, application, unmountedChangeIds, now);
PipelineRunDO pipelineRun = buildPipelineRun(pipelineDefinition, publishedVersion, applicationEnv,
        targetChangeEnvs, orderedTargetChanges, deployBranchSelection.branchName(), userId, now);
pipelineRunMapper.insert(pipelineRun);

for (ChangeEnvDO changeEnv : targetChangeEnvs) { ... }

// 新建部署分支时清理本应用环境历史遗留的部署分支
if (!deployBranchSelection.reused()) {
    cleanupStaleDeployBranches(application, applicationEnv, deployBranchSelection.branchName());
}
schedulePipelineStart(pipelineRun.getId(), new ArrayList<>(targetChangeIds), userId);
```

## Key Decisions

### D1 清理放在 `pipelineRunMapper.insert` 之后

新 run 行先落库，再执行 best-effort 清理。即使清理出现未预期的异常（已 catch，属防御），
run 记录也已存在，不会出现"分支被删但没有新 run"的状态。

### D2 按 `application_env_id` 查本地 run 记录，不扫远端前缀

已核实 `envKey` 全局唯一（`EnvironmentServiceImpl#validateEnvKeyUnique`）、
`(repositoryProviderId, repoIdentifier)` 唯一属于一个应用（`ApplicationServiceImpl#validateApplicationUnique`），
所以 `release/<envKey>/` 在一个仓库内只指向本应用环境。
即便如此，**归属证明取本地 run 记录**：远端前缀扫描会把"非本应用环境创建的、恰好同前缀的分支"
一并删除，而本地记录天然带 `application_env_id` 约束，物理上不可能越界。

### D3 去重

同一分支名会被多次 run 复用（复用路径下 `branch_name` 重复），
用 `LinkedHashSet` 去重，避免对同一分支重复发删除请求。保留插入顺序便于日志阅读。

### D4 排除 `currentDeployBranch`

新建分支名是 `now` 时间戳，理论上不可能出现在历史 run 中，仍显式排除，
使方法在任何调用时序下都安全（防御性，零成本）。

### D5 失败仅 WARN，不抛异常（用户决策）

理由：清理是 housekeeping，旧分支无功能引用；失败会在下次重新签出时自愈（D2 的全量查询会重新覆盖）。
代价：失败被静默，需靠日志排查 —— 故 WARN 日志携带 `applicationEnvId` + `branchName`。
这与 `finalizePublishedChanges` 的 fail-fast 不同，因为那里删除是发布收尾契约的一部分，
而这里纯属垃圾清理。

### D6 日志按 guidelines 收敛

`logging-guidelines.md` 明确禁止"在批量循环里加永久 INFO 日志"。
故循环内只在失败时 WARN，成功结果在循环后汇总成一条 INFO（携带清理列表）。

## Compatibility

- 接口契约不变：`ApplicationReleaseSubmitBranchRespVO` 不新增字段。
- 部署分支命名规则不变：`release/{envKey}/{yyyyMMddHHmmss}`（`devops-pipeline-guidelines.md:799`）。
- 复用/新建判定条件不变，`submit-branch` 的既有测试语义保持。
- `buildPipelineRun` 签名不变，仅入参来源从 `String` 改为 `selection.branchName()`。
- 不影响 `upload-image` 触发路径（它不走 `resolveDeployBranch`）。
- 不影响 `ChangePublishFinalize`：它删的是本次 run 自己的分支，与本清理互补不重叠。
- 已部署版本不受影响：`service/deployment/` 与 `dal/dataobject/deployment/` 无 `branchName` 引用，
  部署单记录制品信息；删 git 分支不回滚运行中的工作负载。

## Trade-Offs

- **全量清理 vs 只删被替换的一条**：全量清理能一次清掉历史 backlog，代价是每次重新签出多 N 次远端删除调用
  （N = 该应用环境历史 `release/` run 数，去重后通常远小于 run 数）。用户已选择全量。
- **删除时机在提交时 vs 等 CodeMerge 成功后**：提交时删除实现简单、无跨步骤状态；
  代价是新分支尚未推送就先删了旧分支，若新 run 早期失败则旧分支已不在
  —— 但旧分支本就已被放弃（判定为不复用），且 `feat/*` 变更分支仍在，可从基线重建，风险可接受。
- **catch `Exception` vs `ServiceException`**：与既有 `stopActivePipelineRuns` 一致地 catch 宽异常，
  保证任何远端异常都不会阻断提交。代价是可能吞掉非预期异常，用 WARN + 堆栈缓解。

## Rollback Shape

改动是纯增量（新增 mapper 方法、新增私有方法、`resolveDeployBranch` 返回类型收窄为私有 record），
回滚即 revert 本次 commit。无数据迁移、无配置项、无对外契约变更，回滚不需要补偿动作。

## Spec Updates Required

- `.trellis/spec/backend/devops-pipeline-guidelines.md` 第 3 节 Contracts：在部署分支命名/复用条目后，
  补充"重新签出部署分支时必须清理该应用环境历史 `release/` 部署分支，按 `application_env_id` 查本地 run 记录，
  失败仅记录日志不阻断提交"。
- 第 4 节 Validation & Error Matrix 视需要补一行清理失败行为。
