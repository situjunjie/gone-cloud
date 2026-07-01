# Design

## Recommendation

Implement `SetupJava` as the MVP toolchain step backed by SDKMAN, and use the existing pipeline cache mount system for `/root/.sdkman`.

Do not try to force Node.js through SDKMAN. Keep Node.js as preinstalled in the builder image for this task, or create a follow-up `SetupNode` design using a Node-specific manager.

## Architecture

### Builder Image

Update `yudao-module-devops/docker/pipeline-builder/Dockerfile` so the image contains SDKMAN and the OS utilities needed to run it. The image can keep a baseline JDK/Maven/Node for bootstrapping and backward compatibility, but the selected pipeline versions should come from `SetupJava`.

Recommended container paths:

- SDKMAN root: `/root/.sdkman`
- Maven local repository: `/root/.m2`
- Gradle cache if later enabled: `/root/.gradle/caches`

The Dockerfile should avoid baking every supported JDK/Maven combination into the image. It should install SDKMAN itself and let runtime setup fill the cache.

### Cache Contract

Extend `PipelineCacheConfigResolver.DEFAULT_DIRECTORIES` with:

- id: `sdkman`
- path: `/root/.sdkman`
- description: `SDKMAN candidates and archives`

This makes the SDKMAN cache definition-scoped by the existing path hash mechanism:

`{cacheWorkspace}/by-path/{sha256("/root/.sdkman")} -> /root/.sdkman`

The existing cache clear API can then clear SDKMAN data by receiving `/root/.sdkman`.

### Step Contract

Use existing step type:

```yaml
steps:
  setup_java:
    step: SetupJava
    with:
      javaVersion: "17.0.12-tem"
      mavenVersion: "3.9.9"
  build:
    step: Command
    with:
      run: |
        java -version
        mvn -version
        mvn -B clean package
```

Compatibility aliases:

- Accept `jdkVersion` as an alias of `javaVersion` because the registry already exposes `jdkVersion`.
- Keep `mavenVersion` optional so Java-only projects can use the step.

### Runtime Behavior

`SetupJavaStepHandler` should implement `PipelineStepHandler` with `StepRuntimeRequirement.JOB_RUNTIME`.

The handler runs inside the same Docker job runtime used by later `Command` steps. It should:

1. Ensure SDKMAN init script exists.
2. Set non-interactive SDKMAN behavior.
3. Install requested Java and Maven versions when absent.
4. Select the requested versions for the current container.
5. Persist environment changes for later commands in the same job.

Because Docker exec does not keep shell state between steps, the handler cannot rely on `sdk use` alone. It needs a durable job-level environment handoff. Recommended approach:

- Write a generated environment file under `/workspace/jobs/{jobId}/tmp/toolchain-env.sh`.
- Add a small `PipelineStepContext` shared-state entry such as `toolchainEnvFile:<jobId>`.
- Update `CommandStepHandler` to source that file before user scripts, or add exported values into `PipelineCommandContext.env`.

Sourcing is more robust because SDKMAN sets several shell functions and env variables through init scripts. The wrapper should source the generated file before the user script:

```sh
if [ -f /workspace/jobs/<jobId>/tmp/toolchain-env.sh ]; then
  . /workspace/jobs/<jobId>/tmp/toolchain-env.sh
fi
```

The generated file should export stable paths rather than invoking `sdk use` on every command. Example:

```sh
export SDKMAN_DIR=/root/.sdkman
export JAVA_HOME=/root/.sdkman/candidates/java/current
export MAVEN_HOME=/root/.sdkman/candidates/maven/current
export M2_HOME="$MAVEN_HOME"
export PATH="$MAVEN_HOME/bin:$JAVA_HOME/bin:$PATH"
```

### Validation

Update `PipelineSpecValidationServiceImpl`:

- Add `SetupJava` to supported steps.
- Validate at least one of `javaVersion`/`jdkVersion` or `mavenVersion` is present.
- Reject both `javaVersion` and `jdkVersion` when they disagree.
- Keep placeholder values allowed where the existing variable resolver supports runtime substitution.

Update `PipelineNodeRegistryServiceImpl` schema:

- Prefer `javaVersion`.
- Retain `jdkVersion` as an accepted alias if frontend compatibility needs it.
- Keep `mavenVersion`.

### Documentation

Update:

- `yudao-module-devops/docker/pipeline-builder/README.md`
- `yudao-module-devops/PIPELINE_YAML_SPEC.md`
- `.trellis/spec/backend/devops-pipeline-guidelines.md` if the new cache/step contract should guide future sessions

## Trade-Offs

- Runtime installation increases first-run latency, but solves per-pipeline version selection and becomes fast after cache warm-up.
- Definition-scoped cache prevents accidental sharing across apps/environments, but the same tool versions may be downloaded once per pipeline definition.
- SDKMAN is appropriate for Java/Maven/Gradle. Node.js should not be included in this step unless the task scope expands to a Node-specific manager.

## Rollback

- Revert `SetupJava` from `isSupportedStep` to make validation reject it again.
- Keep `/root/.sdkman` cache directory harmless if present; it only mounts an unused cache path.
- Builder image can keep old fixed tools during rollout so existing `Command`-only pipelines continue to build.
