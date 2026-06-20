# DevOps image export OSS pipeline step implementation plan

## Checklist

1. Add Docker image assets.
   - Create `yudao-module-devops/docker/image-export-oss/Dockerfile`.
   - Create `export-image-to-oss.sh`.
   - Create image README with build, verify, and pipeline YAML examples.
   - Install official `ossutil` with pinned version and checksum verification.
   - Include `gzip` and `zstd` for optional archive compression.
   - Verify `skopeo --version`, `ossutil --version`, `gzip --version`, and `zstd --version` in the image.

2. Add pipeline step registration.
   - Add `TYPE_DOCKER_IMAGE_EXPORT_OSS`.
   - Register default params and schema in `PipelineNodeRegistryServiceImpl`.
   - Keep password/access secret fields marked with `x-component=password`.

3. Add YAML validation.
   - Allow `DockerImageExportOss` in supported step types.
   - Validate required fields and supported enum values.
   - Validate `overwrite` and `registryTlsVerify` as booleans.
   - Validate `archiveFormat` as `docker-archive` or `oci-archive`.
   - Validate `compression` as `none`, `gzip`, or `zstd`.
   - Validate `oss.path` as a non-empty `oss://...` path.
   - Validate `outputFileName` cannot escape the workspace.

4. Add runtime handler.
   - Implement `DockerImageExportOssStepHandler`.
   - Return `StepRuntimeRequirement.JOB_RUNTIME`.
   - Fetch runtime from `CommandStepHandler.runtimeKey(ctx)`.
   - Call `PipelineCommandExecutor.exec` with environment variables and script path.
   - Append streamed logs through existing log line service.
   - Store only non-sensitive result JSON.
   - Return outputs with `image`, `archiveFormat`, `compression`, `outputFileName`, and `ossPath`.
   - Resolve simple `${VAR}` placeholders through the shared pipeline variable resolver before building command env.

5. Update docs.
   - Add YAML field rows to `PIPELINE_YAML_SPEC.md`.
   - Add execution semantics note that `DockerImageExportOss` is job-runtime and requires the exporter image.
   - Add minimal runnable YAML example.
   - Update current limits.

6. Add tests.
   - `PipelineSpecValidationServiceImplTest`: success case and missing/invalid params.
   - `DockerImageExportOssStepHandlerTest`: command env generation, success result, failure result, no secret in result JSON.
   - Optional script shellcheck-style verification if tooling exists locally.

## Validation Commands

```bash
mvn -pl yudao-module-devops/yudao-module-devops-server -am -DskipTests compile
mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dtest=PipelineSpecValidationServiceImplTest,PipelineVariableResolverTest,CommandStepHandlerTest,DockerImageExportOssStepHandlerTest,PrivateRegistryDockerBuildStepHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test
docker build -t gone-cloud/image-export-oss:skopeo-ossutil yudao-module-devops/docker/image-export-oss
docker run --rm gone-cloud/image-export-oss:skopeo-ossutil sh -lc 'skopeo --version && ossutil --version'
```

## Risk Points

- `ossutil` install URL, version, and checksum must be pinned before implementation is considered shippable.
- Large image archives can consume `/workspace` disk; first version should document this and use job artifact subdirectories.
- Compression can temporarily require additional disk space if implemented as archive-then-compress. Prefer streaming compression if practical, or document the disk requirement clearly.
- Registry TLS behavior must default to secure verification.
- Failure logs from `skopeo` or `ossutil` may include URLs but must not include secrets.

## Start Gate

Implementation is approved for the first version scope: simple `${VAR}` YAML placeholder rendering through the shared resolver, fixed `ossutil`, `docker-archive`/`oci-archive`, optional `none`/`gzip`/`zstd` compression, and `oss.path` target.
