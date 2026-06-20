# Design: DevOps 发布页流水线拓扑 job 粒度

## Current State

`ApplicationServiceImpl#buildReleasePipeline` 解析已发布版本 YAML 后调用 `sortExecutableSteps(spec)`，把 steps 扁平化为发布页 `pipeline.nodes`。`buildReleasePipelineEdges` 又按扁平 step 顺序串线，因此：

- 一级 nodes 是 YAML step / execution step 粒度。
- edges 是声明顺序线性关系，不是 `job.needs` DAG。
- `current-run.nodes` 复用这些 pipeline nodes，再按 step `nodeId` 匹配 `dev_pipeline_run_log`。

这与运行时模型不一致。`PipelineSpec` 已有 `ExecutableGraph` / `ExecutableJob`，`PipelineExecutionEngine` 也按 job DAG 和 `dev_pipeline_run_job` 调度。

## Target Contract

发布页 read model 保持 `nodes` 字段名，但一级节点为 job：

- `pipeline.nodes[]`: YAML job 静态节点。
- `pipeline.nodes[].steps[]`: job 内 YAML steps 静态明细。
- `pipeline.edges[]`: `job.needs` 生成的 DAG edges。
- `current-run.nodes[]`: job 运行态节点，包含聚合状态和 `steps[]` 运行明细。
- `current-run.edges[]`: 与静态 pipeline edges 同语义，用于运行态拓扑。

## Model Changes

Extend existing VO classes instead of replacing fields:

- `ApplicationReleasePipelineNodeRespVO`
  - keep: `nodeId/type/name/enabled/displayOrder/params/timeoutSeconds/retryTimes/failStrategy`
  - add: `nodeType/stageId/stageName/jobId/needs/steps`
  - for job nodes: `nodeId=jobId`, `type=JOB`, `nodeType=JOB`, `name=job.name`
  - `steps` item contains step static fields: `stepId/name/step/enabled/displayOrder/params/timeoutSeconds/retryTimes/failStrategy`

- `ApplicationReleaseCurrentRunRespVO`
  - add top-level `edges`
  - extend `Node` with `nodeType/stageId/stageName/jobId/durationMillis/steps`
  - step item contains `stepId/name/step/status/executionStatus/runLogId/summary/errorMessage/startedAt/finishedAt/durationMillis/detailType/detailRef/actions/result`

Use constant `JOB_NODE_TYPE = "JOB"` in service layer.

## Static Pipeline Build

Replace release-page static topology build with `spec.toExecutableGraph()`:

1. Parse YAML with existing validation service.
2. Use `ExecutableGraph graph = spec.toExecutableGraph()`.
3. If graph has no jobs, return `SPEC_INVALID`.
4. Build nodes from `graph.getJobs()`.
5. Build each node's `steps` from `ExecutableJob.getSteps()`.
6. Build edges from each job's `needs`.

`displayOrder` remains declaration-order index over jobs only.

## Current Run Build

`current-run` should use job nodes as the base:

1. Build static pipeline as above.
2. If `run == null`, return job nodes with `PENDING` / `NOT_STARTED` and static `steps`.
3. If `run != null`:
   - load `PipelineRunJobDO` list by `pipelineRunId`;
   - load `PipelineRunLogDO` list by `pipelineRunId`;
   - group logs by `jobId` and index logs by `stepId`;
   - for each job node, copy static job fields and apply job run state if present;
   - populate `node.steps` by matching each static step with its run log.

Status source preference:

- Prefer `PipelineRunJobDO.status` for job `executionStatus` because the engine already aggregates job state.
- Use step logs to fill `node.steps`, step detail/actions, and fallback aggregation when a job run row is missing.
- Map job statuses to existing generic node statuses:
  - `PENDING` -> `NOT_STARTED`
  - `RUNNING` -> `RUNNING`
  - `BLOCKED` -> `BLOCKED`
  - terminal statuses (`SUCCESS`, `FAILED`, `SKIPPED`, `CANCELED`) -> `COMPLETED`

Compatibility:

- Keep old `runLogId` on the job node as the first matching step log id when useful, but `detailRef` should include `runId` and `jobId`.
- Preserve approval/code-merge actions on the step item where the action belongs. For job-level convenience, expose the first active step action/detail on the job node only when the job is blocked by that step.

## Edges

Generate only dependency edges:

```text
for job in graph.jobs:
  for need in job.needs:
    edge.source = need
    edge.target = job.jobId
```

No synthetic edges are created for multiple root jobs or display order.

## Tests

Update `ApplicationServiceImplTest` around release env-detail/current-run:

- static pipeline returns job nodes with nested steps;
- static edges come from needs, including fan-in and fan-out;
- current-run returns job nodes with nested step statuses;
- two root jobs are not linearly connected;
- two independent running jobs can both return `RUNNING`;
- failed step/job produces failed job status/error;
- compatibility fields remain present.

## Risks

- Existing tests and possibly frontend assumptions expect `nodeId` to equal step id. This task intentionally changes release-page default to job id while retaining step ids inside `node.steps`.
- Some older runs may not have `dev_pipeline_run_job` rows. Fallback aggregation from step logs protects read model compatibility.
