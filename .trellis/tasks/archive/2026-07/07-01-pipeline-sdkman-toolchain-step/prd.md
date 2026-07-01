# SDKMAN pipeline toolchain step

## Goal

Allow pipeline jobs to select Java build tool versions at runtime instead of baking one JDK/Maven version set into `yudao-module-devops/docker/pipeline-builder`.

The immediate user value is that different applications can build with different JDK and Maven versions while reusing the same executor image. Tool downloads must be cached in the pipeline cache mount so repeated runs with the same versions do not reinstall them.

## Confirmed Facts

- `yudao-module-devops/docker/pipeline-builder/Dockerfile` currently installs fixed JDK 17, Maven 3.9.9, Node.js 24.16.0, pnpm 10.33.2, and exports fixed `JAVA_HOME`, `MAVEN_HOME`, `M2_HOME`, and `PATH`.
- `PipelineNodeRegistryServiceImpl` already registers `SetupJava` with `jdkVersion` and `mavenVersion`, but `PipelineSpecValidationServiceImpl#isSupportedStep` rejects it as `STEP_TYPE_UNSUPPORTED`.
- `PipelineBuiltinStepHandler` only handles built-in steps through the old node handler path, while current execution uses `PipelineStepHandler`; therefore `SetupJava` has no real executable step handler today.
- Job runtime is lazy-created in `PipelineExecutionEngine`: a `JOB_RUNTIME` step creates one Docker runtime for the job and later steps reuse it.
- Docker runtimes mount every enabled cache directory from `PipelineCacheConfig` into the container.
- Default cache directories currently include `/root/.m2`, `/root/.gradle/caches`, `/root/.npm`, `/root/.pnpm-store`, `/root/.yarn`, `/go/pkg/mod`, and `/root/.cache`, but not `/root/.sdkman`.
- SDKMAN is suitable for Java ecosystem candidates such as Java, Maven, and Gradle. It is not a Node.js version manager, so Node.js needs separate handling if runtime Node version selection is required.

## Requirements

- R1: The builder image must include SDKMAN bootstrap support and required OS tools, but should stop being the only place where JDK and Maven versions are fixed for all pipelines.
- R2: Add a real executable step for Java toolchain setup. Prefer extending the existing `SetupJava` type rather than introducing a second overlapping Java setup step.
- R3: `SetupJava` must support selecting at least `javaVersion` or the existing `jdkVersion` alias, plus `mavenVersion`. Gradle support may be included if it is cheap because SDKMAN supports it, but it is not required for the MVP.
- R4: Toolchain installation must be cached under a stable mounted directory, recommended `/root/.sdkman`, so a later run with the same SDKMAN candidate version reuses the installed bits.
- R5: The cache must use the existing `cacheConfig` mechanism, cache clear API, and definition-scoped cache root. Avoid a second ad hoc host-path cache system.
- R6: After `SetupJava` runs, later `Command` steps in the same job must be able to use `java`, `javac`, and `mvn` without repeating setup commands.
- R7: Static validation must accept the supported `SetupJava` shape and reject blank or malformed tool requests clearly enough for users to fix the YAML.
- R8: Documentation must explain the cache directory, example YAML, supported tools, and the Node.js limitation.

## Acceptance Criteria

- [ ] A pipeline YAML containing `SetupJava` followed by `Command` passes validation.
- [ ] `SetupJava` is handled by a `PipelineStepHandler` and executes inside the job runtime.
- [ ] Running `SetupJava` with a Java version and Maven version installs or reuses those versions through SDKMAN.
- [ ] `/root/.sdkman` can be mounted through the existing cache config and is included in default cache directories unless explicitly disabled.
- [ ] A second run with the same definition cache reuses the same SDKMAN installation directory and does not redownload already installed versions.
- [ ] Later `Command` steps in the same job resolve `java`, `javac`, and `mvn` to the selected versions.
- [ ] Tests cover validation, cache defaults, handler command generation or execution behavior, and runtime cache mount wiring.
- [ ] `PIPELINE_YAML_SPEC.md` and `docker/pipeline-builder/README.md` describe the new usage.

## Out Of Scope

- Cross-run global tool cache shared across all pipeline definitions.
- Full frontend visual editor work beyond exposing the already registered `SetupJava` schema changes.
- Node.js runtime version management through SDKMAN, because SDKMAN does not manage Node.js. A later task can add `SetupNode` using Volta, fnm, or nvm.
- Supporting arbitrary SDKMAN candidates without backend validation.

## Open Question

OQ1: Should the MVP include Node.js version selection in the same task using a separate manager, or explicitly defer Node.js and keep this task focused on SDKMAN-managed Java tooling?
