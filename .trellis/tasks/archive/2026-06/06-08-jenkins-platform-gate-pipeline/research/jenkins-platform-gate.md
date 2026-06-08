# Jenkins Platform Gate Research

## Sources

- Jenkins Pipeline input step: https://www.jenkins.io/doc/pipeline/steps/pipeline-input-step/
- Jenkins Pipeline syntax and options: https://www.jenkins.io/doc/book/pipeline/syntax/

## Findings

- Jenkins Pipeline supports an `input` step that pauses Pipeline execution and waits for external input.
- `input` supports an `id` parameter. For platform integration this id should be deterministic, for example `gate-${PIPELINE_RUN_ID}-${nodeId}`, so the platform can address the correct pending input.
- Jenkins Pipeline supports wrapping steps with `timeout`, which should be used around platform gates to avoid indefinitely waiting builds.
- The platform should treat Jenkins as the build executor, not the source of business truth. Jenkins can pause and resume, but platform approval state, run state, artifact records, and deployment decision should remain in the platform database.

## Design Implications

- A platform node can be represented in Jenkinsfile as a generic gate:
  - notify platform;
  - pause with `input`;
  - resume or abort based on platform decision;
  - notify platform after resume.
- Jenkins cannot pause at arbitrary external positions unless the pause point is represented in Jenkinsfile. Therefore every platform node in the DSL must generate a corresponding gate stage.
- Approval rejection should call Jenkins input abort or stop the build. Approval success should call Jenkins input proceed.
- Jenkins input and platform gate status can drift, so platform APIs must be idempotent and must allow retrying proceed/abort operations.
