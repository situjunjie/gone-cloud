# Jenkins API pipeline integration research

## Question

Can the frontend visually orchestrate a Jenkinsfile, then the backend call Jenkins APIs to deploy?

## Official references

* Jenkinsfile docs: https://www.jenkins.io/doc/book/pipeline/jenkinsfile/
* Pipeline as Code: https://www.jenkins.io/doc/book/pipeline/pipeline-as-code/
* Remote Access API: https://www.jenkins.io/doc/book/using/remote-access-api/
* Shared Libraries: https://www.jenkins.io/doc/book/pipeline/shared-libraries/

## Findings

Jenkins supports this direction:

* Jenkins Pipeline definitions can be written as Jenkinsfile and stored in SCM.
* Jenkins supports Declarative and Scripted Pipeline syntax.
* Jenkins Remote Access API exposes JSON/XML endpoints and can trigger jobs, including parameterized builds.
* Jenkins Shared Libraries are intended for common pipeline logic shared by multiple projects.

## Recommended architecture

Use visual DSL first, Jenkinsfile second.

Frontend:

* Edits graph nodes and edges.
* Sends structured DSL to backend.
* Does not directly emit trusted Groovy/Jenkinsfile.

Backend:

* Validates graph topology, node parameters, permissions, and environment constraints.
* Generates Jenkinsfile from whitelisted node templates.
* Stores generated Jenkinsfile text and checksum for audit.
* Triggers Jenkins via Remote Access API.
* Polls Jenkins queue/build API or receives webhook callbacks.
* Maps Jenkins result and stages back to DevOps `PipelineRun` and `PipelineStageRun`.

Jenkins:

* Runs a parameterized Pipeline job or Multibranch Pipeline.
* Uses Shared Library functions for common steps such as merge, build image, unit test, deploy to Kubernetes.
* Receives runtime parameters from DevOps backend.

## Why not frontend-generated Jenkinsfile directly

Direct frontend generation creates risks:

* Groovy/script injection if user input is interpolated into Jenkinsfile.
* Hard to validate permissions and environment constraints.
* Hard to version and audit generated pipeline code consistently.
* Frequent frontend changes can accidentally alter deployment semantics.

The safer design is backend template generation from a typed node registry.

## MVP shape

* One Jenkins provider configuration table for URL, credential, crumb requirement, default folder/job.
* One pipeline definition version contains DSL plus generated Jenkinsfile.
* One application environment binds to a pipeline definition.
* Trigger endpoint creates a platform run, calls Jenkins `buildWithParameters`, records queue item/build number, and syncs status.
* Jenkins stages should use predictable names matching node ids/types, so platform can map external stages back to internal stage runs.

## Open decisions

* Jenkinsfile storage: commit to application repo, commit to a central pipeline repo, or store in Jenkins job config.
* Job strategy: one shared parameterized job, one job per application environment, or Multibranch Pipeline per repo.
* Status sync: polling Jenkins API vs webhook callback.
