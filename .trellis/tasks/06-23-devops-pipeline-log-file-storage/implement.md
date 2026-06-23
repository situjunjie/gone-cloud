# Implementation Plan

## Checklist

- [x] Load pre-development backend guidance before editing code.
- [x] Add a mounted log storage helper under the devops pipeline execution or framework package.
- [x] Extend command execution context with container-visible stdout/stderr log paths.
- [x] Refactor `DockerPipelineCommandExecutor` to run the script through a shell wrapper that redirects stdout/stderr to the mounted log files.
- [x] Refactor `CommandStepHandler` to prepare log files before execution, avoid row-per-line DB writes, upload the completed log through `FileApi`, and store `PipelineRunLogDO.logFileUrl`.
- [x] Refactor Docker job-runtime image archive import/export handlers to use container-side stdout/stderr redirection and upload full logs.
- [x] Keep Docker API build/push handler on file-first append and upload its assembled full log.
- [x] Refactor `getLogLines` to read from mounted files first and fallback to `PipelineRunLogLineMapper` when file logs are absent.
- [x] Keep SSE logic on the existing service API so controller and frontend contracts stay unchanged.
- [x] Update `PipelineRunLogLineServiceImplTest` from mapper-write assertions to file-backed assertions; keep DB fallback tests.
- [x] Update DevOps pipeline guidelines to reflect file-backed live log storage.
- [x] Run focused tests for pipeline log service and related command handlers.
- [x] Run residual search for `dev_pipeline_run_log_line`, `PipelineRunLogLineMapper.insert`, and stale “row id” wording.

## Candidate Files

- `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/service/pipeline/execution/PipelineRunLogLineServiceImpl.java`
- `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/service/pipeline/execution/PipelineRunLogLineService.java`
- `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/framework/pipeline/runtime/PipelineCommandContext.java`
- `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/framework/pipeline/runtime/DockerPipelineCommandExecutor.java`
- `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/service/pipeline/execution/handler/CommandStepHandler.java`
- `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/service/pipeline/execution/handler/DockerImageArchiveImportStepHandler.java`
- `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/service/pipeline/execution/handler/DockerImageExportObjectStorageStepHandler.java`
- `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/service/pipeline/execution/handler/PrivateRegistryDockerBuildStepHandler.java`
- `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/service/pipeline/execution/handler/PipelineStepLogFileHelper.java`
- `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/dal/mysql/pipeline/log/PipelineRunLogLineMapper.java`
- `yudao-module-devops/yudao-module-devops-server/src/test/java/cn/iocoder/yudao/module/devops/service/pipeline/execution/PipelineRunLogLineServiceImplTest.java`
- `yudao-module-devops/yudao-module-devops-server/src/test/java/cn/iocoder/yudao/module/devops/service/pipeline/execution/handler/CommandStepHandlerTest.java`
- `.trellis/spec/backend/devops-pipeline-guidelines.md`

## Validation Commands

```bash
mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dtest='PipelineRunLogLineServiceImplTest,CommandStepHandlerTest,PrivateRegistryDockerBuildStepHandlerTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

If the focused test set exposes unrelated module setup problems, run the narrowest compiling subset and record the blocker.

## Rollback Points

- Before changing service storage: existing DB-backed service can be restored by reverting `PipelineRunLogLineServiceImpl` and tests.
- Before spec/schema cleanup: keep `dev_pipeline_run_log_line` schema untouched unless a separate migration is explicitly requested.
