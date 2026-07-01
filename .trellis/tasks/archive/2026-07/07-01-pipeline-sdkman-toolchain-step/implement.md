# Implementation Plan

## Checklist

1. Update builder image
   - Install SDKMAN in `yudao-module-devops/docker/pipeline-builder/Dockerfile`.
   - Keep required base utilities and a compatibility baseline toolchain.
   - Update `README.md` with SDKMAN cache and example YAML.

2. Extend cache defaults
   - Add `/root/.sdkman` to `PipelineCacheConfigResolver.DEFAULT_DIRECTORIES`.
   - Update cache docs and tests that assert default directories.

3. Make `SetupJava` executable
   - Add or update `SetupJavaStepHandler` implementing `PipelineStepHandler`.
   - Use `StepRuntimeRequirement.JOB_RUNTIME`.
   - Install/reuse requested Java and Maven versions through SDKMAN.
   - Generate a job-scoped env file for later steps.

4. Wire toolchain env into command execution
   - Update `CommandStepHandler` to source the job toolchain env file before user scripts.
   - Preserve existing `MAVEN_CONFIG`, `NPM_CONFIG_CACHE`, `PNPM_STORE_PATH`, and `GRADLE_USER_HOME` defaults.

5. Update validation and registry
   - Add `SetupJava` to supported step types.
   - Validate `javaVersion`/`jdkVersion` and `mavenVersion`.
   - Update registry schema/defaults while retaining `jdkVersion` compatibility if needed.

6. Update docs/spec
   - Update `PIPELINE_YAML_SPEC.md`.
   - Update `.trellis/spec/backend/devops-pipeline-guidelines.md` if accepted as durable convention.

7. Tests
   - Add validation tests for accepted and rejected `SetupJava`.
   - Add handler tests for generated SDKMAN commands/env handoff.
   - Add cache resolver test for `/root/.sdkman`.
   - Add command handler test proving scripts source the toolchain env file.
   - Update Docker runtime cache mount test if defaults are asserted indirectly.

## Validation Commands

```bash
mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dtest='PipelineSpecValidationServiceImplTest,PipelineCacheConfigResolverTest,CommandStepHandlerTest,*SetupJava*Test,DockerPipelineJobRuntimeManagerTest,LocalPipelineWorkspaceServiceTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

Optional image verification after Dockerfile implementation:

```bash
docker build -t gone-cloud/pipeline-builder:sdkman yudao-module-devops/docker/pipeline-builder
docker run --rm gone-cloud/pipeline-builder:sdkman sh -lc 'test -s /root/.sdkman/bin/sdkman-init.sh && bash -lc "source /root/.sdkman/bin/sdkman-init.sh && sdk version"'
```

## Risk Points

- SDKMAN uses shell initialization; environment changes must survive across separate Docker exec calls.
- First-run installation depends on network access from the build container.
- Concurrent jobs sharing the same definition cache could install the same candidate/version simultaneously. Prefer SDKMAN's idempotency first; add per-cache lock only if tests or runtime evidence show corruption.
- SDKMAN does not manage Node.js; expanding scope to Node requires a separate design.

## Open Decision Before Implementation

- Decide whether Node.js version selection is in scope for this task. Recommended: defer Node.js to a separate `SetupNode` task.
