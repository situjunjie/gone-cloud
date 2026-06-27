# Implementation

1. Create a shared execution-log writer utility for step handlers.
2. Update `CommandStepHandler` to write each executor line to the step log file.
3. Update `DockerImageArchiveImportStepHandler` and `DockerImageExportObjectStorageStepHandler` the same way.
4. Keep `PrivateRegistryDockerBuildStepHandler` on the same helper path.
5. Preserve bounded `resultJson.logLines` in each handler.
6. Verify `markSuccess()` / `markFailed()` still archive logs through `PipelineStepLogHelper`.
7. Add or update unit tests for one command handler and one Docker handler to prove live file writes happen.
8. Run the relevant tests for the changed handlers.
