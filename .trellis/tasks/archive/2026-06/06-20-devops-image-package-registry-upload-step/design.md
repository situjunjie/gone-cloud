# Design

## Summary

Add an explicit pipeline step for importing an uploaded image archive into a configured image registry, plus an application-release trigger that starts the current published pipeline for an `applicationEnvId` with a `fileUrl` runtime input.

The feature is not based on `OfflineImagePackage`. The frontend upload flow produces a new `fileUrl`; the DevOps trigger API persists that URL into the pipeline run input context and execution injects it into variable resolution as `${FILE_URL}` / `${fileUrl}`.

## User Flow

1. User clicks "上传镜像部署" on the application release page.
2. User uploads an image archive in the UI.
3. Frontend receives `fileUrl` from the file upload flow.
4. Frontend calls a new application-release endpoint with `applicationEnvId` and `fileUrl`.
5. Backend creates a `PipelineRun` for the current published pipeline definition of that application environment.
6. Execution injects the persisted run input into `sharedState`.
7. The YAML-defined image-import step reads `with.fileUrl: ${FILE_URL}`, downloads the archive, and pushes it to the registry/image configured in YAML.
8. Later deployment steps continue to use the YAML-configured image expression, typically with tag `${runId}`.

## API Contract

New endpoint belongs to the application release domain, not the generic pipeline-run domain.

Suggested endpoint:

```http
POST /devops/application/release/upload-image
```

Request:

```json
{
  "applicationEnvId": 1,
  "fileUrl": "https://example.com/path/demo.docker.tar.gz"
}
```

Response should mirror the existing submit-branch trigger response shape where practical:

```json
{
  "applicationEnvId": 1,
  "pipelineRunId": 800,
  "runStatus": 1
}
```

Backend resolves the current published pipeline version from `applicationEnvId`. The request must not accept a pipeline version id, target image, or registry credential.

## Pipeline YAML Contract

Add a new explicit step type. Tentative name:

```yaml
import_image:
  name: 导入镜像包到仓库
  step: DockerImageArchiveImport
  with:
    fileUrl: ${FILE_URL}
    image: registry.example.com/ns/app:${runId}
    archiveFormat: docker-archive
    compression: gzip
    registryTlsVerify: true
    certificate:
      type: usernamePassword
      username: <registry-username>
      password: <registry-password>
```

The exact step type name can still be adjusted during implementation, but the contract is:

- explicit step in YAML;
- `with.fileUrl` required;
- `with.image` required and resolved from normal variables;
- registry credentials live in YAML, not in the trigger API;
- v1 supports only archives compatible with `DockerImageExportOss`: `docker-archive` / `oci-archive` and `none` / `gzip` / `zstd`;
- the step does not need to publish downstream outputs.

## Runtime Context

Add a persisted pipeline-run input context, for example `dev_pipeline_run.input_context_json` and `PipelineRunDO.inputContextJson`.

For upload-image runs, persist at least:

```json
{
  "fileUrl": "https://example.com/path/demo.docker.tar.gz",
  "triggerMode": "UPLOAD_IMAGE"
}
```

Execution should merge this context into `sharedState` before step variable resolution. Existing variable resolver already exposes both camelCase and upper underscore aliases, so `fileUrl` becomes available as `${fileUrl}` and `${FILE_URL}`.

Sensitive values must not be placed in this input context. Registry credentials remain in YAML and must be masked from logs and result JSON.

## Step Execution

Implementation should reuse the existing job-runtime command-execution pattern used by `DockerImageExportOss`:

- step runtime requirement: `JOB_RUNTIME`;
- command runs inside the job container;
- runtime image should reuse the shared `yudao-module-devops/docker/pipeline-builder` image. Add the image-transfer tools needed by both export and import flows there over time, and keep each step's behavior in separate scripts;
- add a companion script that:
  - downloads `FILE_URL` to a job artifact/temp path;
  - validates/decompresses according to `archiveFormat` and `compression`;
  - logs in via an auth file built from `certificate`;
  - uses `skopeo copy` from archive source to `docker://<image>`;
  - respects `registryTlsVerify`;
  - exits non-zero on download, format, decompression, or push failure.

No Docker daemon is required if `skopeo` can copy from the archive to the registry directly.

## Data And Status

Use normal pipeline run/job/log state:

- active-run conflict rule remains per `applicationEnvId`;
- `triggerType` should be distinct from `APPLICATION_RELEASE_TAB`, for example `APPLICATION_UPLOAD_IMAGE`;
- `changeSnapshotJson`, `changeId`, and `changeEnvId` can be empty for this trigger mode;
- `branchName` can keep the same generated deploy-branch convention or a neutral upload-image branch marker if needed for existing non-null constraints;
- step logs should record sanitized metadata such as `fileUrl`, `image`, archive format, compression, and exit code, but never credentials.

## Compatibility

- Do not use or revive `OfflineImagePackage`.
- Do not modify `DockerImageExportOss` to output a `fileUrl`; compatibility is file-format compatibility only.
- Deployment steps continue consuming their configured `with.image`. They do not depend on outputs from the import step.
- Existing `submit-branch` behavior must remain unchanged.

## Risks

- Allowing arbitrary `fileUrl` creates remote-download risk. The implementation needs bounded timeout, size limit, and clear failure messages.
- Credentials in YAML are existing product behavior for related steps, but handlers must keep masking and avoid logging raw YAML resolved values.
- A run input context column is a schema change and must be reflected in SQL, DO, run creation, execution, and tests.
