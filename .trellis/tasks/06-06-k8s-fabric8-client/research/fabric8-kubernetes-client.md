# Fabric8 Kubernetes Client Research

## Sources

* Maven Central `io.fabric8:kubernetes-client`: latest resolved version `7.7.0`.
* Fabric8 GitHub README.

## Findings

* Dependency coordinates:
  * groupId: `io.fabric8`
  * artifactId: `kubernetes-client`
  * version: `7.7.0`
* The client exposes a fluent DSL for Kubernetes resources.
* Basic client construction uses `new KubernetesClientBuilder().build()`.
* Explicit configuration can be supplied with `new KubernetesClientBuilder().withConfig(config).build()`.
* `ConfigBuilder` supports direct settings such as master URL, token, certificate behavior, and related options.
* The README documents Namespace listing through `client.namespaces().list()`.

## Decision for this task

Use Fabric8 directly in a small DevOps-local adapter. Store a K8s environment's kubeconfig text as encrypted JSON under `EnvironmentDO.infraConfig`, parse it into Fabric8 `Config`, then open short-lived `KubernetesClient` instances with try-with-resources for each operation.

This keeps K8s operations behind a connector abstraction and leaves HOST / host-group support free to implement another connector later.
