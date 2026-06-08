# Jenkins Pipeline Stage Capabilities

## Sources

- Jenkins Pipeline Syntax: https://www.jenkins.io/doc/book/pipeline/syntax/
- Jenkins Docker Pipeline: https://www.jenkins.io/doc/book/pipeline/docker/
- Jenkins Core Pipeline Steps, `archiveArtifacts`: https://www.jenkins.io/doc/pipeline/steps/core/
- Jenkins JUnit Pipeline Step: https://www.jenkins.io/doc/pipeline/steps/junit/

## Relevant Findings

- Declarative Pipeline supports top-level and stage-level `agent`, `environment`, `tools`, `options`, `parameters`, `stages`, `stage`, `steps`, and `post`.
- Stage-level `options` can express `timeout`, `retry`, and `timestamps`; this maps well to the existing platform `timeoutSeconds` and `retryTimes` node fields.
- Jenkins `tools` requires tool names preconfigured under Jenkins global tool configuration. Platform should expose tool-name fields rather than hard-code local Maven/Node/JDK paths.
- Jenkins parameters are available through `params.*` and as environment variables. The existing runner already passes platform-controlled values such as `PIPELINE_RUN_ID`, `REPO_URL`, `BRANCH_NAME`, `COMMIT_SHA`, `APP_KEY`, `CALLBACK_URL`, `CALLBACK_TOKEN`, and `JENKINSFILE_TEXT`.
- `archiveArtifacts` archives build outputs and supports artifact glob patterns, fingerprinting, empty archives, and success-only behavior. This is the closest Jenkins-native baseline for "artifact upload" unless the product explicitly requires an external artifact repository such as Nexus or MinIO.
- Docker Pipeline supports `docker.build(...)`, image `push()`, and `docker.withRegistry(...)` for custom registries. Platform image-node parameters should map to image name/tag, Dockerfile path, build context/args, registry URL, credentials id, and push behavior.
- JUnit publishing is normally done through the `junit` step in a stage `post { always { ... } }` block. If unit-test reporting remains in scope, node params should keep `reportPattern` and `allowEmptyResults`.

## Design Implications

- Keep platform DSL declarative and typed; do not let the frontend submit arbitrary shell commands for the Jenkins stages.
- Node parameter schemas should describe Jenkins-native fields. Command templates can remain for compatibility, but real Jenkins-compatible nodes should generate explicit stage bodies.
- Generated Jenkinsfile should be the source of truth for Jenkins stage content. Validation must reject missing or incompatible fields before publishing.
- The runner job can stay parameterized and evaluate `JENKINSFILE_TEXT`; node implementation work should focus on generated stage bodies and callback lifecycle.
