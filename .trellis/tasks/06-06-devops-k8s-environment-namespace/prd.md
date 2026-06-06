# devops k8s environment namespace

## Goal

Allow a Kubernetes-type DevOps environment to bind to a target Namespace, so multiple logical environments can share the same Kubernetes cluster but deploy applications into different namespaces.

## What I Already Know

* User wants one large Kubernetes cluster to be reused as multiple DevOps environments by assigning different namespaces.
* `dev_environment.infra_config` already stores Kubernetes-specific encrypted JSON and is built through `KubernetesEnvironmentConnector`.
* Existing Kubernetes config currently stores only `kubeconfig`.
* Existing application-environment relation stores `appId + envId`; no deployment code currently consumes a namespace.
* Existing API already lists Kubernetes namespaces through `/devops/environment/kubernetes/namespaces?id=...`.

## Assumptions

* Namespace is Kubernetes-specific infrastructure config, not a generic `dev_environment` table column.
* For K8S environments, namespace is required and stored in encrypted `infra_config` next to kubeconfig.
* On update, missing kubeconfig still preserves the old kubeconfig; namespace may be updated independently.
* Existing environments whose stored JSON lacks namespace should remain readable; they must provide namespace on the next update if the request changes Kubernetes config.

## Requirements

* Add `namespace` to Kubernetes environment create/update request payload under `kubernetesConfig`.
* Add `namespace` to persisted `KubernetesEnvironmentConfig`.
* Add `namespace` to environment response for K8S environments without exposing kubeconfig or raw `infraConfig`.
* Validate namespace as required for creating K8S environments.
* Preserve existing kubeconfig on K8S updates when kubeconfig is omitted.
* Allow namespace to be updated without resending kubeconfig.
* Keep K8S-specific logic inside the Kubernetes connector/conversion boundary.

## Acceptance Criteria

* [ ] Creating a K8S environment with kubeconfig and namespace stores both in `infra_config`.
* [ ] Creating a K8S environment without namespace fails with a business validation error.
* [ ] Updating an existing K8S environment with only namespace preserves old kubeconfig and updates namespace.
* [ ] Environment responses include Kubernetes namespace for configured K8S environments and never include kubeconfig.
* [ ] Existing focused Kubernetes connector tests are updated.
* [ ] DevOps server module compiles or targeted tests pass.

## Definition of Done

* Tests added/updated for connector namespace behavior.
* Maven targeted verification is run for the DevOps server module.
* No raw kubeconfig is returned in response VOs.
* Trellis spec update is considered after implementation.

## Out of Scope

* Building actual Kubernetes Deployment/Service deployment execution.
* Adding per-application namespace override.
* Adding or migrating database columns.
* Automatically creating missing namespaces in Kubernetes.

## Technical Notes

* Relevant specs:
  * `.trellis/spec/backend/index.md`
  * `.trellis/spec/backend/devops-infra-guidelines.md`
  * `.trellis/spec/backend/database-guidelines.md`
  * `.trellis/spec/guides/cross-layer-thinking-guide.md`
* Likely files:
  * `EnvironmentKubernetesConfigReqVO`
  * `KubernetesEnvironmentConfig`
  * `KubernetesEnvironmentConnector`
  * `EnvironmentRespVO`
  * `EnvironmentConvert`
  * `KubernetesEnvironmentConnectorTest`
