# K8s Pipeline Platform Nodes Technical Assessment

## Goal

Evaluate how to add two platform-executed Pipeline YAML steps for Kubernetes cluster deployment and Kubernetes image version upgrade. Both steps must create deployment orders and reuse the DevOps module's existing Kubernetes integration.

## What I Already Know

* The DevOps pipeline is being refactored around YAML `stages.jobs.steps` and `PipelineStepHandler`.
* The requested nodes are platform nodes, not job-runtime/container command nodes.
* The DevOps module already includes Kubernetes environment connection, dashboard, terminal support, and deployment order persistence/execution.
* Both requested nodes need deployment order records.

## Current Repo Findings

* Pipeline ownership is platform-side. Jenkins/Docker runtime is only an executor path; new platform control steps should use `StepRuntimeRequirement.PLATFORM`.
* The current executable step extension point is `PipelineStepHandler`, returning `StepResult.CONTINUE/SUSPEND/FAIL`.
* `PipelineNodeRegistryServiceImpl` currently registers `CodeMerge`, `APPROVAL`, `Command`, and several build/report placeholders. It does not yet register K8s deploy or image upgrade step types.
* `PipelineSpecValidationServiceImpl` currently allows only `Command`, `CodeMerge`, and `APPROVAL` as executable step types. New K8s steps must be added to both node registry and validation.
* `DeploymentOrderServiceImpl#startContainerDeploy` already creates `dev_deployment_order`, renders a Kubernetes Deployment manifest, applies it with Fabric8, waits for rollout, captures previous/target image/revision, and exposes live pod/deployment status.
* Existing deployment execution updates `PipelineRunLogDO` and directly mutates `PipelineRunDO` terminal status. This conflicts with the refactored DAG engine, where job/run status should be aggregated by `PipelineExecutionEngine`.
* `dev_pipeline_run_log` has migrated to `step_id/step_type/step_name`, but `dev_deployment_order` still stores `node_id/node_type`; deployment order can keep those physical columns while new code uses step terminology at service/API boundaries.
* Kubernetes environment namespace is stored in encrypted `dev_environment.infra_config` as `KubernetesEnvironmentConfig#namespace`; deployment should continue using the environment namespace rather than adding per-application namespace overrides.

## Recommended Technical Approach

Use two first-class platform step types:

* `K8sDeploy`: initial or full Deployment apply from a manifest.
* `K8sImageUpgrade`: patch/update the image of an existing Kubernetes Deployment container.

Both should have dedicated `PipelineStepHandler` classes or a shared abstract base plus two thin handlers. They should:

* Return `StepRuntimeRequirement.PLATFORM`.
* Create or reuse a deployment order keyed by `(pipelineRunId, stepId)`.
* Execute through a deployment service method that returns a deployment execution result instead of directly marking the whole pipeline run success/failed.
* Write `PipelineRunLogDO` with `runtime_type=PLATFORM`, `step_id`, `step_type`, `step_name`, and sanitized `contextJson/resultJson`.
* Return `StepResult.continueWith(...)` on successful rollout and `StepResult.fail(...)` on validation/apply/rollout failure, leaving job/run aggregation to `PipelineExecutionEngine`.

## Node Shape Proposal

`K8sDeploy`:

```yaml
steps:
  deploy:
    name: "K8s 集群部署"
    step: K8sDeploy
    with:
      deployMode: RAW_MANIFEST
      manifestYaml: |
        apiVersion: apps/v1
        kind: Deployment
        metadata:
          name: gone-api
        spec:
          template:
            spec:
              containers:
                - name: app
                  image: ${IMAGE}
      containerName: app
      image: registry.example.com/gone-api:${COMMIT_SHA}
      replicas: 2
      rolloutTimeoutSeconds: 300
```

`K8sImageUpgrade`:

```yaml
steps:
  upgrade:
    name: "K8s 镜像版本升级"
    step: K8sImageUpgrade
    with:
      workloadKind: Deployment
      workloadName: gone-api
      containerName: app
      image: registry.example.com/gone-api:${COMMIT_SHA}
      replicas: 2
      rolloutTimeoutSeconds: 300
```

## Deployment Order Model Fit

The existing `dev_deployment_order` table can support both nodes with minor enum/contract cleanup:

* `deploy_type=K8S_DEPLOYMENT` for full manifest apply.
* Add `deploy_type=K8S_IMAGE_UPGRADE` for image-only update.
* Reuse `namespace`, `workload_kind`, `workload_name`, `container_name`, `image`, `replicas`, previous snapshot fields, target revision, config/result JSON.
* Keep DB columns `node_id/node_type` for now, mapping them from `stepId/stepType`. Do not add node aliases back to new pipeline run APIs.

## Main Implementation Risks

* Status ownership: current deployment service directly updates `PipelineRunDO` terminal status. New handlers must avoid that path or refactor it, otherwise one deploy step can incorrectly finish a run while other DAG jobs are pending/running.
* Sync blocking: deployment execution waits for rollout in the pipeline engine thread. This is acceptable for MVP if rollout timeout is bounded, but long rollouts consume the scheduler thread. A later async deployment order runner could convert the step into `SUSPEND` and resume on completion.
* Idempotency: retry/resume must handle an existing deployment order. Existing logic skips successful orders and retries failed/canceled orders; new service methods should preserve this but return a clear result to the handler.
* K8s operation difference: full deploy can use `createOrReplace` with manifest; image upgrade should load an existing Deployment and mutate only the target container image/optional replicas. It should fail clearly when the workload/container does not exist.
* Validation: pipeline validation must add required params and type checks for both new step types. Platform-only jobs containing only these steps must not require `runsOn`.
* Variable resolution: existing deployment service manually replaces `${APP_KEY}`, `${COMMIT_SHA}`, `${BRANCH_NAME}`, `${PIPELINE_RUN_ID}`, `${ENV_KEY}`, `${NAMESPACE}`. Pipeline specs prefer shared variable resolution. Implementation should either reuse/centralize that resolver or at least align supported variables with `sharedState`.
* Cancellation: `PipelineExecutionEngine#cancelPlatformSteps` currently only cancels `APPROVAL`. It should dispatch cancel hooks for all non-terminal platform steps, including both K8s deployment step types.

## Suggested Implementation Slices

1. Registry and validation:
   Add `K8sDeploy` and `K8sImageUpgrade` to `PipelineNodeRegistryServiceImpl`, mark them as `PLATFORM`, include schemas/default params, update `isPlatformNode`, and extend `PipelineSpecValidationServiceImpl`.

2. Deployment service refactor:
   Introduce an execution method such as `executePipelineDeploymentStep(PipelineStepContext ctx)` returning a result DTO. Reuse existing manifest deployment internals, add image-only upgrade internals, and prevent service-level run aggregation in the handler path.

3. Step handlers:
   Add `K8sDeployStepHandler` and `K8sImageUpgradeStepHandler` that create/update deployment orders, call the service, and convert the result into `StepResult`.

4. Cancellation and retry:
   Extend platform-step cancellation dispatch beyond `APPROVAL`; keep deployment order cancel/retry APIs compatible with the existing deployment order page.

5. Docs/tests:
   Update `PIPELINE_YAML_SPEC.md`, focused pipeline validation tests, step handler tests, and deployment service tests for full deploy and image upgrade.

## Acceptance Criteria

* [ ] Pipeline YAML validation accepts `K8sDeploy` and `K8sImageUpgrade` with required params and rejects invalid/missing params.
* [ ] Jobs containing only these K8s platform steps do not require `runsOn`.
* [ ] Executing either step creates one deployment order tied to the pipeline run and step id.
* [ ] Successful rollout returns `StepResult.CONTINUE` and lets the pipeline engine aggregate job/run status.
* [ ] Failed K8s validation/apply/rollout returns `StepResult.FAIL` without leaking kubeconfig or sensitive values.
* [ ] Canceling a run cancels active deployment orders for K8s platform steps.
* [ ] Deployment order detail still exposes live Kubernetes status and previous/target image/revision.

## Out Of Scope For MVP

* Multi-resource manifest apply beyond a single Kubernetes Deployment.
* Cross-namespace deployment selection per application or per node.
* Async deployment worker/resume model, unless rollout blocking becomes unacceptable.
* StatefulSet/DaemonSet support. The current fit is Kubernetes Deployment only.
* Registry credential/image existence validation before Kubernetes rollout.

## Decision

* Existing `startContainerDeploy` behavior that directly marks the whole `PipelineRun` success/failed is not acceptable for the refactored DAG pipeline. Deployment execution must report step-level results and let `PipelineExecutionEngine` aggregate job/run state.
* `K8sImageUpgrade` is strictly image-only against an existing Kubernetes Deployment. If the workload or target container does not exist, it must fail clearly. Initial creation belongs to `K8sDeploy`.

## Technical References

* `.trellis/spec/backend/devops-pipeline-guidelines.md`
* `.trellis/spec/backend/devops-infra-guidelines.md`
* `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/service/pipeline/execution/PipelineExecutionEngine.java`
* `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/service/pipeline/PipelineNodeRegistryServiceImpl.java`
* `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/service/pipeline/PipelineSpecValidationServiceImpl.java`
* `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/service/deployment/DeploymentOrderServiceImpl.java`
* `sql/mysql/devops.sql`
