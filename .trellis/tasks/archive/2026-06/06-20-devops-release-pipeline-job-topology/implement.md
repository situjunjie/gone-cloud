# Implementation Plan

## Checklist

1. Load backend spec before editing.
2. Extend release pipeline VO classes:
   - static job node metadata and nested static steps;
   - current-run top-level edges;
   - current-run job node metadata, duration, nested running steps.
3. Inject `PipelineRunJobMapper` into `ApplicationServiceImpl`.
4. Replace `buildReleasePipeline` step flattening with `PipelineSpec.ExecutableGraph`.
5. Build static job nodes and nested static steps from `ExecutableJob`.
6. Generate edges from `ExecutableJob.needs` only.
7. Rework `buildCurrentRunNodes` to:
   - base on job nodes;
   - load job runs and step logs;
   - apply job state from `PipelineRunJobDO`;
   - populate nested step details from `PipelineRunLogDO`;
   - preserve detail/action compatibility for blocked approval/code-merge steps.
8. Add `current-run.edges` response assignment.
9. Update or add focused tests in `ApplicationServiceImplTest`.
10. Run targeted Maven tests.

## Validation Commands

```bash
mvn -pl yudao-module-devops/yudao-module-devops-server -Dtest=ApplicationServiceImplTest test
```

If dependency compilation requires upstream modules:

```bash
mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dtest=ApplicationServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test
```

## Files To Touch

- `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/controller/admin/application/vo/ApplicationReleasePipelineNodeRespVO.java`
- `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/controller/admin/application/vo/ApplicationReleaseCurrentRunRespVO.java`
- `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/service/application/ApplicationServiceImpl.java`
- `yudao-module-devops/yudao-module-devops-server/src/test/java/cn/iocoder/yudao/module/devops/service/application/ApplicationServiceImplTest.java`

## Rollback Point

All changes are limited to release-page read models and tests. If the new response breaks unrelated callers, revert the VO/service changes in the files above; pipeline execution persistence and scheduling are not modified.
