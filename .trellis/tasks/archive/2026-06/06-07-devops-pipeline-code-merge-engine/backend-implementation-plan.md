# Backend Implementation Plan

## Architecture

后端采用“通用执行引擎 + 节点处理器”结构：

* `PipelineExecutionService`：创建/启动 run，调度节点，处理继续/取消。
* `PipelineRunLogService`：创建、更新、查询通用日志，封装 `context_json` / `result_json` 读写。
* `PipelineNodeHandler`：节点处理器接口。
* `CodeMergeNodeHandler`：内置 `CODE_MERGE` 节点处理器。
* `GitWorkspaceService`：封装 clone/fetch/checkout/merge/conflict extraction/continue/push/abort。

`CodeMergeNodeHandler` 不拥有专用 DO；它只读写 `PipelineRunDO` 和 `PipelineRunLogDO`。

## Database Changes

### Add `dev_pipeline_run_log`

```sql
CREATE TABLE `dev_pipeline_run_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '流水线运行日志编号',
  `pipeline_run_id` bigint NOT NULL COMMENT '流水线运行编号',
  `parent_id` bigint DEFAULT NULL COMMENT '父日志编号',
  `node_id` varchar(128) NOT NULL COMMENT '节点编号',
  `node_type` varchar(64) NOT NULL COMMENT '节点类型',
  `node_name` varchar(128) NOT NULL COMMENT '节点名称',
  `log_level` varchar(16) NOT NULL COMMENT '日志层级：NODE/STEP/EVENT',
  `status` varchar(32) NOT NULL COMMENT '状态',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
  `started_at` datetime DEFAULT NULL COMMENT '开始时间',
  `finished_at` datetime DEFAULT NULL COMMENT '结束时间',
  `summary` varchar(500) DEFAULT NULL COMMENT '摘要',
  `context_json` mediumtext COMMENT '运行上下文 JSON',
  `result_json` mediumtext COMMENT '运行结果 JSON',
  `error_message` varchar(1000) DEFAULT NULL COMMENT '错误信息',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_run_sort` (`tenant_id`, `pipeline_run_id`, `sort`),
  KEY `idx_tenant_parent_sort` (`tenant_id`, `parent_id`, `sort`),
  KEY `idx_tenant_run_node` (`tenant_id`, `pipeline_run_id`, `node_type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='DevOps 流水线运行日志表';
```

### Optional `dev_pipeline_run` Extensions

第一版可以不扩主表，deploy branch/commit 只放 `CODE_MERGE.result_json`。

如果后续 Jenkins checkout 查询需要高频读取，可追加：

* `deploy_branch_name`
* `deploy_commit_sha`

## Java Model

### DO / Mapper

* `PipelineRunLogDO extends TenantBaseDO`
* `PipelineRunLogMapper extends BaseMapperX<PipelineRunLogDO>`

Mapper default methods:

* `selectListByPipelineRunId(Long pipelineRunId)`
* `selectByPipelineRunIdAndNodeType(Long pipelineRunId, String nodeType)`
* `selectListByParentId(Long parentId)`
* `selectActiveByApplicationEnvId(...)` can be implemented through run table + log status, or through pipeline run status first.

### Enums

Prefer string enum values for log extensibility:

* `PipelineRunLogLevelEnum`: `NODE`, `STEP`, `EVENT`
* `PipelineRunLogStatusEnum`: `PENDING`, `RUNNING`, `WAITING_INPUT`, `SUCCESS`, `FAILED`, `CANCELED`
* `PipelineNodeTypeEnum`: `CODE_MERGE`, later `JENKINS_BUILD`, `DEPLOY`, `APPROVAL`

### Context DTOs

Context DTOs are Java classes used for JSON serialization, not database DOs:

* `CodeMergeContext`
* `CodeMergeItemContext`
* `CodeMergeConflictContext`
* `CodeMergeResolutionContext`
* `CodeMergeResult`

These live under `service/pipeline/context` or `framework/pipeline/execution` and are serialized into log JSON.

## Service Flow

### Submit Branch Trigger

1. `ApplicationServiceImpl.submitApplicationReleaseBranch` validates target set.
2. It creates `PipelineRunDO`.
3. It updates target `ChangeEnvDO.lastPipelineRunId`.
4. It calls `pipelineExecutionService.startRun(pipelineRun.getId(), targetChangeIds, userId)`.

MVP can be synchronous for deterministic tests. The service boundary must allow switching to async later.

### Start Run

1. Validate run exists.
2. Reject active run for same `applicationEnvId`.
3. Create `NODE` log:
   * `nodeId=builtin.code_merge`
   * `nodeType=CODE_MERGE`
   * `nodeName=代码合并`
   * `status=RUNNING`
4. Dispatch to `CodeMergeNodeHandler.execute(...)`.

### Code Merge Node

1. Load application, application env, environment, repository provider, target changes.
2. Validate GitLab + access token provider.
3. Build deploy branch: `deploy/{appKey}/{envKey}/{pipelineRunId}`.
4. Create workspace and clone repo.
5. Checkout deploy branch from origin base branch locally.
6. Freeze every target branch SHA.
7. Save initial `CodeMergeContext` to node log.
8. For each item:
   * append/update child `STEP` log.
   * run `git merge --no-ff --no-commit <sha>`.
   * success: `git commit`, update item and step log.
   * conflict: extract conflict metadata, update node log status to `WAITING_INPUT`, update step log `WAITING_INPUT`, stop dispatch.
9. After all items success:
   * push `HEAD:refs/heads/{deployBranch}`.
   * write `result_json`.
   * mark node `SUCCESS`.
   * later dispatch next node.

## Conflict APIs

Recommended controller under `/devops/pipeline-run`:

* `GET /devops/pipeline-run/{runId}/logs`
  * Returns tree/flat logs for run detail.
* `GET /devops/pipeline-run/{runId}/code-merge/conflicts`
  * Returns `context_json.conflicts[]`.
* `GET /devops/pipeline-run/{runId}/code-merge/conflict-detail?filePath=...`
  * Reads base/ours/theirs/working/result content from Git workspace.
* `PUT /devops/pipeline-run/{runId}/code-merge/conflict-resolution`
  * Body: `filePath`, `resolutionType`, `resolvedContent`, `comment`.
  * Updates `context_json.resolutions[]`, marks conflict resolved if valid, appends `EVENT` log.
* `POST /devops/pipeline-run/{runId}/code-merge/continue`
  * Validates all current text conflicts resolved.
  * Writes resolved files, stages, commits current merge, resumes remaining items.
* `POST /devops/pipeline-run/{runId}/code-merge/retry-current-change`
  * Only allowed when current conflict is unsupported or user explicitly retries current item.
  * Aborts current merge, fetches current branch latest SHA, updates only current item SHA, retries current item.
* `POST /devops/pipeline-run/{runId}/cancel`
  * Aborts workspace and marks active logs/run canceled.

## Git Workspace

Workspace root:

* Config: `yudao.devops.git-workspace-root`
* Default: `${java.io.tmpdir}/gone-devops/git-workspaces`

Rules:

* One run owns one workspace.
* Workspace key stored only in `CODE_MERGE.context_json`.
* Do not expose workspace path to frontend.
* Cleanup on success/cancel/failure best-effort.
* Add TTL cleanup later.

Suggested Git flow:

```bash
git clone --no-tags <auth-repo-url> <workspace>
git fetch origin <base-branch>
git checkout -B <deploy-branch> origin/<base-branch>
git fetch origin <change-branch>
git rev-parse origin/<change-branch>
git merge --no-ff --no-commit <change-sha>
git commit -m "Merge change <change-key> into <deploy-branch>"
git push origin HEAD:refs/heads/<deploy-branch>
```

Conflict extraction:

```bash
git ls-files -u
git show :1:<path>
git show :2:<path>
git show :3:<path>
```

## Error Handling

Add DevOps error constants:

* `PIPELINE_RUN_NOT_EXISTS`
* `PIPELINE_RUN_LOG_NOT_EXISTS`
* `PIPELINE_RUN_ACTIVE_EXISTS`
* `PIPELINE_RUN_LOG_STATE_INVALID`
* `PIPELINE_CODE_MERGE_CONFLICT_UNRESOLVED`
* `PIPELINE_CODE_MERGE_CONFLICT_UNSUPPORTED`
* `PIPELINE_CODE_MERGE_GIT_EXEC_FAIL`
* `PIPELINE_CODE_MERGE_WORKSPACE_NOT_EXISTS`
* `PIPELINE_CODE_MERGE_REPOSITORY_AUTH_NOT_SUPPORTED`

All upstream Git output must be truncated and scrubbed for token-like material.

## Testing Plan

Service tests:

* submit branch creates run and invokes execution service.
* active run for same app env is rejected.
* code merge success writes node log, step logs, and result JSON.
* text conflict writes `WAITING_INPUT`, conflict metadata, and does not push.
* save resolution updates context JSON and appends event log.
* continue after all conflicts resolved marks item success and resumes.
* unsupported conflict blocks online resolution.
* retry current change refreshes only current item SHA.
* cancel marks run/log canceled and calls workspace abort.

Verification:

```bash
mvn -pl yudao-module-devops/yudao-module-devops-server -am -DskipTests compile
mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dtest='*Pipeline*Test,*CodeMerge*Test,ApplicationServiceImplTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

## Implementation Order

1. Add enums, `PipelineRunLogDO`, mapper, SQL.
2. Add context DTOs and log JSON helper service.
3. Add `PipelineExecutionService` skeleton and node handler registry.
4. Wire `submit-branch` to execution service.
5. Add Git workspace abstraction with mockable interface.
6. Implement `CodeMergeNodeHandler`.
7. Add run log/conflict APIs.
8. Add tests.
9. Compile and adjust.
