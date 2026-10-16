# Implement

## Preconditions

- 任务状态已通过 `task.py start` 置为 `in_progress`。
- 已读 `.trellis/spec/backend/index.md` 与 `devops-pipeline-guidelines.md` 第 3/4 节。
- **工作区已有一批未提交改动**（`stopActivePipelineRuns` 契约，含 `ApplicationServiceImpl.java` 与其测试）。
  本任务改动同一对文件，提交时一并带上；开工前不要 stash/丢弃。

## Steps

### 1. `PipelineRunMapper` 新增列表查询

文件：`yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/dal/mysql/pipeline/PipelineRunMapper.java`

- [ ] 1.1 在 `selectLatestByApplicationEnvIdAndBranchPrefix` 之后新增
      `selectListByApplicationEnvIdAndBranchPrefix(Long, String)`，用 `LambdaQueryWrapperX` 的
      `eq(applicationEnvId)` + `likeRightIfPresent(branchName, prefix)` + `selectList`。
      不加排序（清理顺序由 service 层 `LinkedHashSet` 决定）。

### 2. `ApplicationServiceImpl` 决策结果显式化

文件：`yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/service/application/ApplicationServiceImpl.java`

- [ ] 2.1 新增嵌套 record `DeployBranchSelection(String branchName, boolean reused)`，
      放在 `resolveDeployBranch` 附近（或与 `ReleaseBranches` 并列）。
- [ ] 2.2 `resolveDeployBranch` 返回类型 `String` → `DeployBranchSelection`；
      复用分支返回 `new DeployBranchSelection(latestReleaseRun.getBranchName(), true)`；
      新建分支返回 `new DeployBranchSelection(<新时间戳分支名>, false)`。
      **判定条件一行不改**。
- [ ] 2.3 `submitApplicationReleaseBranch` 中 `String deployBranch = resolveDeployBranch(...)`
      → `DeployBranchSelection deployBranchSelection = resolveDeployBranch(...)`，
      `buildPipelineRun(..., deployBranchSelection.branchName(), ...)`。

### 3. 新增 `cleanupStaleDeployBranches`

- [ ] 3.1 按 design D2 查询 `selectListByApplicationEnvIdAndBranchPrefix(applicationEnv.getId(), DEPLOY_BRANCH_PREFIX)`。
- [ ] 3.2 `map(PipelineRunDO::getBranchName)` → `filter(StrUtil::isNotBlank)`
      → `filter(!equals(currentDeployBranch))` → `collect(toCollection(LinkedHashSet::new))`（D3/D4）。
- [ ] 3.3 空集合直接 return。
- [ ] 3.4 循环调用 `repositoryProviderService.deleteRepositoryBranch(
      application.getRepositoryProviderId(), application.getRepoIdentifier(), staleBranch)`；
      成功名收集进 `cleanedBranches`；`catch (Exception ex)` → `log.warn` 带 `applicationEnvId` + `branchName` + 堆栈（D5）。
- [ ] 3.5 循环后若 `cleanedBranches` 非空，输出一条汇总 `log.info`（D6，禁止循环内 INFO）。
- [ ] 3.6 在 `submitApplicationReleaseBranch` 中 `pipelineRunMapper.insert` 与 changeEnv 更新之后、
      `schedulePipelineStart` 之前调用：`if (!deployBranchSelection.reused()) { cleanupStaleDeployBranches(...); }`（D1）。

### 4. 单元测试

文件：`yudao-module-devops/yudao-module-devops-server/src/test/java/cn/iocoder/yudao/module/devops/service/application/ApplicationServiceImplTest.java`

- [ ] 4.1 让 `buildApplication()` 补 `repositoryProviderId` / `repoIdentifier`，使删除入参可断言。
      检查现有用例是否依赖这两个字段为 null（`validateApplicationUnique` 不在提交路径上，应无影响）。
- [ ] 4.2 `testSubmitApplicationReleaseBranch_rebuildCleansStaleDeployBranches`：
      历史 run 含 `release/test/20260607120000`、`release/test/20260608120000`，基线已推进
      → 断言两次 `deleteRepositoryBranch` 分别以这两个分支名为入参，且**不以新分支名为入参**（AC1）。
- [ ] 4.3 `testSubmitApplicationReleaseBranch_rebuildCleansDuplicateBranchesOnce`：
      历史 run 中同一分支名重复出现 → 该分支名只删一次（AC6）。
- [ ] 4.4 `testSubmitApplicationReleaseBranch_reuseKeepsDeployBranch`：
      复用路径 → `verify(repositoryProviderService, never()).deleteRepositoryBranch(any(), any(), any())`（AC3）。
- [ ] 4.5 `testSubmitApplicationReleaseBranch_cleanupFailureIgnored`：
      `deleteRepositoryBranch` 抛 `ServiceException` → 提交成功、run 已插入、`startPipelineAsync` 被调用（AC7）。
- [ ] 4.6 `testSubmitApplicationReleaseBranch_unmountRebuildCleansStaleDeployBranches`：
      剔除已挂载变更触发新建 → 同样清理（AC2）。
- [ ] 4.7 检查既有 `testSubmitApplicationReleaseBranch_baseBranchOutdatedRebuildDeployBranch` 等用例：
      未 stub 新查询方法时 Mockito 对 `List` 返回空列表，清理自动跳过，断言应保持通过。

### 5. Spec 更新

- [ ] 5.1 `.trellis/spec/backend/devops-pipeline-guidelines.md` 第 3 节 Contracts：
      在部署分支命名/复用条目后补充生命周期契约（见 design「Spec Updates Required」）。
- [ ] 5.2 第 4 节 Validation & Error Matrix：补一行"清理历史部署分支失败 → 仅记录日志，提交继续"。

## Validation Commands

```bash
# 编译
mvn -q -pl yudao-module-devops/yudao-module-devops-server -am -DskipTests compile

# 本任务聚焦测试
mvn -pl yudao-module-devops/yudao-module-devops-server test \
  -Dtest=ApplicationServiceImplTest

# 回归：模块内 DevOps 相关测试
mvn -pl yudao-module-devops/yudao-module-devops-server test
```

## Review Gates

- G1（步骤 2 后）：`resolveDeployBranch` 的判定条件与改动前**逐字一致**，仅返回类型变化。
- G2（步骤 3 后）：`cleanupStaleDeployBranches` 内**无循环 INFO**；无 `git` 命令直调；
      未出现远端前缀扫描（无 `listBranches` / 通配符删除）。
- G3（步骤 4 后）：AC1–AC7 每条都有对应断言；`verify(never())` 用例确实覆盖复用路径。
- G4（提交前）：`git diff` 中不含与本任务无关的改动；确认未提交的 `stopActivePipelineRuns` 改动随本次一并提交。

## Rollback Points

- R1：步骤 1–3 完成后（未动测试）→ 直接 `git checkout --` 两个源文件即可回到干净状态。
- R2：步骤 4 后若测试暴露设计缺陷 → 回到 design.md 修订，再重跑步骤 2–4。
- R3：整体回滚 = revert 本次 commit；纯增量改动，无数据迁移与配置项，无需补偿。

## Risks

| 风险 | 缓解 |
|---|---|
| 误删其他应用环境的部署分支 | 只按 `application_env_id` 查本地 run 记录，不扫远端前缀（D2）；AC5 断言入参 |
| 删除失败被静默忽略 | WARN 日志带 `applicationEnvId` + `branchName`；下次重新签出自愈（D5） |
| 既有 submit 测试因新增 mock 交互而失败 | 未 stub 的 `List` 返回空列表，清理跳过；步骤 4.7 显式核对 |
| 事务内远端调用不可回滚 | 清理在 `insert` 之后且全程 catch，失败不回滚 run 记录（D1） |
