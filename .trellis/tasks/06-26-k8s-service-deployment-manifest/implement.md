# Implementation Plan

## Checklist

1. [x] Read backend Trellis specs before editing.
2. [x] Extend `KubernetesDeploymentManifestSupport` with ordered manifest parsing for Service/Deployment resources.
3. [x] Add helpers to find and validate exactly one Deployment from a resource list.
4. [x] Update `DeploymentOrderServiceImpl` raw manifest config creation to use the new parser and unique Deployment lookup.
5. [x] Update raw manifest execution to apply Service and Deployment resources in YAML order while preserving image, replica, annotation, and rollout behavior for the unique Deployment.
6. [x] Update node registry label from `Deployment YAML` to a broader Kubernetes YAML label if tests and UI expectations permit.
7. [x] Add focused unit tests:
   - Ordered Service + Deployment apply.
   - Single Deployment compatibility.
   - Missing Deployment rejection.
   - Multiple Deployment rejection.
   - Unsupported kind rejection.
8. [x] Run focused Maven tests for affected devops module classes.

## Validation Commands

```bash
mvn -pl yudao-module-devops/yudao-module-devops-server -Dtest=KubernetesDeploymentManifestSupportTest,DeploymentOrderServiceImplTest,PipelineSpecValidationServiceImplTest,K8sDeployStepHandlerTest test
```

If the module test selector differs from local Maven configuration, run the closest focused test command and record the exact result.

## Risk Notes

- Fabric8 generic resource apply APIs differ from typed Deployment APIs; keep the implementation small and covered by tests.
- Existing order records store only one workload name, so the unique Deployment constraint is required.
- Rollout readiness remains Deployment-only by design.
