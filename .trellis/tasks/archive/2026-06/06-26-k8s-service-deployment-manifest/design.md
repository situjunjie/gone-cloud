# Technical Design

## Scope

This change extends the existing `K8sDeploy` raw manifest path only. The handler remains a thin adapter; the deployment service and Kubernetes manifest helper own parsing, validation, mutation, apply, and rollout waiting.

## Current Constraints

- `K8sDeployStepHandler` delegates to `DeploymentOrderService.startContainerDeploy(...)`.
- `DeploymentOrderServiceImpl.buildRawManifestConfig(...)` renders `manifestYaml`, parses it as one `Deployment`, validates `containerName`, and stores the deployment name in the order snapshot.
- `KubernetesDeploymentManifestSupport.parseDeployment(...)` rejects multi-document YAML and non-Deployment resources.
- `executeDeployment(...)` applies exactly one Deployment and waits for that Deployment rollout.

## Proposed Behavior

For `RAW_MANIFEST`:

1. Render `manifestYaml` with the existing variable map.
2. Parse rendered YAML into an ordered list of Kubernetes resources.
3. Validate that every resource is either `Service` or `Deployment`.
4. Validate exactly one `Deployment` exists.
5. Validate the Deployment metadata name and requested container.
6. Store the Deployment name as the deployment order workload name.
7. During execution, re-parse the rendered YAML from the config snapshot.
8. Prepare resources in their original order:
   - Set namespace on every Service and Deployment.
   - For the unique Deployment, set annotations, target image, and optional replicas.
9. Apply each resource in order with Fabric8 `createOrReplace`.
10. Wait only for the unique Deployment rollout ready.

## Compatibility

- Existing single Deployment YAML continues to parse as a one-item list and follows the same deployment flow.
- Existing `manifestYaml`, `containerName`, `image`, `replicas`, and timeout fields are unchanged.
- `K8sImageUpgrade` continues using the existing single Deployment update path.

## Error Handling

Use existing deployment manifest error categories where possible:

- Empty or invalid YAML -> manifest required/invalid.
- Unsupported resource kind -> manifest kind not supported.
- Missing Deployment metadata name -> manifest name required.
- Missing requested container -> manifest container not exists.
- Missing or multiple Deployment resources -> deployment node param invalid with a concise message.

## Non-Goals

- No new database schema.
- No rollout tracking for Service or multiple workloads.
- No support for ConfigMap, Secret, Ingress, Job, StatefulSet, DaemonSet, or custom resources in this task.
