# Implementation Plan

## Backend

1. Add pipeline-run input context persistence.
   - Add `input_context_json` or equivalent to `dev_pipeline_run`.
   - Add field to `PipelineRunDO`.
   - Update MySQL SQL and any project-required migration/bootstrap SQL.

2. Add upload-image release API.
   - Add request VO with `applicationEnvId` and `fileUrl`.
   - Add response VO or reuse the existing submit response if shape fits.
   - Add `POST /devops/application/release/upload-image` under `ApplicationController`.
   - Add service method that validates application env, current published definition/version, and no active run.
   - Create `PipelineRun` with trigger type such as `APPLICATION_UPLOAD_IMAGE`, empty change snapshot, and persisted input context.
   - Start the pipeline after transaction commit using existing async scheduling.

3. Inject input context into execution variables.
   - Parse `PipelineRunDO.inputContextJson` in `PipelineExecutionEngine.buildSharedState`.
   - Merge non-secret values into `sharedState` before application-derived variables.
   - Ensure `${FILE_URL}` and `${fileUrl}` resolve through the existing variable resolver.

4. Add the new step type.
   - Add a constant to `PipelineNodeRegistryServiceImpl`.
   - Register node metadata, default params, and schema.
   - Add validation in `PipelineSpecValidationServiceImpl`:
     - required `fileUrl`;
     - required `image`;
     - required `certificate.type=usernamePassword`, username, password;
     - `archiveFormat` in `docker-archive|oci-archive`;
     - `compression` in `none|gzip|zstd`;
     - optional `registryTlsVerify` boolean.
   - Update `PIPELINE_YAML_SPEC.md`.

5. Add step handler.
   - Implement a `PipelineStepHandler` with `JOB_RUNTIME`.
   - Reuse `PipelineCommandExecutor`, `PipelineStepLogHelper`, and `PipelineRunLogLineService`.
   - Build sanitized result JSON.
   - Do not emit required downstream outputs.
   - Support cancel by command run id.

6. Add import script/runtime support.
   - Add an import script under the shared pipeline-builder runtime, for example `import-image-archive-to-registry.sh`.
   - Ensure `yudao-module-devops/docker/pipeline-builder` contains the command-line tools required by this step (`curl`, `skopeo`, `gzip`, `zstd`) instead of adding another step-specific runtime image.
   - Script responsibilities:
     - download URL;
     - enforce basic validation and fail fast;
     - decompress when configured;
     - run `skopeo copy <archive>:<file>:<image> docker://<image>`.

## Frontend / API Contract

1. Add release-page "上传镜像部署" button.
2. Reuse existing upload flow to obtain `fileUrl`.
3. Confirm action calls the new application release upload-image endpoint with `applicationEnvId` and `fileUrl`.
4. Reuse existing current-run polling after the endpoint returns `pipelineRunId`.

## Tests

1. `PipelineSpecValidationServiceImplTest`
   - accepts valid new step YAML;
   - rejects missing `fileUrl`, `image`, and credential fields;
   - rejects unsupported archive format/compression.

2. New step handler test
   - builds expected command env;
   - masks password from result JSON/log summaries;
   - returns success/failure based on command result;
   - cancel delegates to command executor.

3. `ApplicationServiceImplTest`
   - upload-image trigger creates a run for the current published version;
   - rejects missing published version and active run;
   - persists input context with `fileUrl`;
   - does not mutate change environment mount state.

4. `PipelineExecutionEngineTest` or focused resolver test
   - run input context is injected into `sharedState`;
   - `${FILE_URL}` resolves in step `with.fileUrl`.

## Verification Commands

```bash
mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dtest='PipelineSpecValidationServiceImplTest,*Image*Import*Test,ApplicationServiceImplTest,PipelineExecutionEngineTest,PipelineVariableResolverTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

If Docker/runtime script behavior is changed, also verify the runtime image locally when Docker is available:

```bash
docker build -t gone-cloud/pipeline-builder:local yudao-module-devops/docker/pipeline-builder
docker run --rm gone-cloud/pipeline-builder:local sh -lc 'skopeo --version && curl --version && gzip --version && zstd --version'
```

## Review Checklist

- No raw registry password appears in logs, `resultJson`, `contextJson`, or step outputs.
- Upload-image trigger does not call `submit-branch` and does not create/mount change records.
- Active run uniqueness remains enforced by `applicationEnvId`.
- Existing `submit-branch`, `PrivateRegistryDockerBuild`, and `DockerImageExportOss` behavior remains unchanged.
- YAML docs and node registry schema match the validator and handler.
