# DevOps Infrastructure Integration Guidelines

DevOps environments can represent different infrastructure backends. Keep backend-specific behavior behind a connector boundary so adding HOST, SSH, or host-group support does not require rewriting environment CRUD.

## Scenario: Kubernetes Environment Connector

### 1. Scope / Trigger

- Trigger: adding or changing DevOps environment infrastructure integrations, especially Kubernetes, host, SSH, or host-group connection behavior.
- Scope: `yudao-module-devops` API enums, server controllers, service orchestration, connector classes, dependency management, encrypted persistence, SQL bootstrap scripts, and focused tests.
- Use this scenario whenever a change adds a new `/devops/environment/**` operation or changes the shape of `dev_environment.infra_config`.

### 2. Signatures

- Maven dependency:
  - Manage Fabric8 through `yudao-dependencies/pom.xml` with `io.fabric8:kubernetes-client-bom`.
  - Add `io.fabric8:kubernetes-client` in `yudao-module-devops/yudao-module-devops-server/pom.xml` without a module-local version.
  - Keep the Fabric8 BOM before Spring Cloud BOM if both manage Fabric8 artifacts, otherwise Spring Cloud may downgrade transitive versions.
- DB signature:
  - `dev_environment.infra_config text COMMENT '基础设施连接配置 JSON，加密存储'`
  - K8S `infra_config` JSON shape: `{"kubeconfig":"...","namespace":"test"}`.
  - `EnvironmentDO` must use `@TableName(value = "dev_environment", autoResultMap = true)`.
  - `infraConfig` must use `@TableField(typeHandler = EncryptTypeHandler.class)` and `@ToString.Exclude`.
- API signatures:
  - `POST /devops/environment/create` accepts `EnvironmentSaveReqVO`.
  - `PUT /devops/environment/update` accepts `EnvironmentSaveReqVO`.
  - `POST /devops/environment/check?id={id}` and `POST /devops/environment/check-connection?id={id}` return `EnvironmentConnectionCheckRespVO`.
  - `GET /devops/environment/kubernetes/namespaces?id={id}` returns `List<EnvironmentKubernetesNamespaceRespVO>`.
- Java extension points:
  - `EnvironmentConnector#getInfraType()`
  - `EnvironmentConnector#buildInfraConfig(EnvironmentSaveReqVO reqVO, EnvironmentDO oldEnvironment)`
  - `EnvironmentConnector#checkConnection(EnvironmentDO environment)`
  - `EnvironmentConnectorFactory#getConnector(String infraType)`
  - `KubernetesEnvironmentConfig#namespace` is the deployment target namespace for applications associated with that environment.

### 3. Contracts

- `EnvironmentSaveReqVO.infraType` must be validated by `EnvironmentInfraTypeEnum`.
- `EnvironmentSaveReqVO.kubernetesConfig.kubeconfig` is the raw kubeconfig input. It is required when creating `infraType=K8S`; it may be omitted on update only when the existing environment is already K8S and has `infraConfig`.
- `EnvironmentSaveReqVO.kubernetesConfig.namespace` is the target Kubernetes Namespace. It is required for K8S environments, stored in encrypted `infra_config`, and may be updated without resending kubeconfig.
- Kubernetes namespace values must follow DNS label shape: lowercase letters, numbers, and `-`, with an alphanumeric first and last character, max 63 characters.
- Non-K8S environment creation and update must not require Kubernetes config and must not invoke Fabric8.
- `EnvironmentRespVO` must not return raw connection material. Return only derived fields such as `infraConfigConfigured` and `kubernetesNamespace`.
- `EnvironmentConnectionCheckRespVO` should include `infraType`, a human-readable `message`, and Kubernetes-specific derived data such as `namespaceCount`.
- `EnvironmentKubernetesNamespaceRespVO` should expose namespace metadata only: `name`, `status`, and `creationTimestamp`.
- Connector implementations should create short-lived SDK clients inside operation methods and close them with try-with-resources.
- Later deployment code for applications associated with a K8S environment should deploy into `KubernetesEnvironmentConfig.namespace`; do not add a per-application namespace override unless product requirements explicitly need it.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| Unknown `infraType` in request | Bean validation fails through `@InEnum(EnvironmentInfraTypeEnum.class)` |
| Connector factory receives unsupported type | Throw `ENVIRONMENT_INFRA_TYPE_NOT_SUPPORTED` |
| Create K8S environment without kubeconfig | Throw `ENVIRONMENT_KUBECONFIG_REQUIRED` |
| Create K8S environment without namespace | Throw `ENVIRONMENT_KUBERNETES_NAMESPACE_REQUIRED` |
| Update existing K8S environment with namespace only | Preserve existing kubeconfig and update `namespace` in `infra_config` |
| Update existing K8S environment without kubeconfig | Preserve existing encrypted `infraConfig` |
| Change HOST to K8S without kubeconfig | Throw `ENVIRONMENT_KUBECONFIG_REQUIRED` |
| Invalid kubeconfig parse/client construction | Throw `ENVIRONMENT_KUBERNETES_CONFIG_INVALID` or required-config error, depending on failure point |
| Fabric8 operation fails during check/list | Throw `ENVIRONMENT_KUBERNETES_CONNECTION_FAIL` with a truncated upstream message |
| Namespace API called for HOST | Throw `ENVIRONMENT_INFRA_TYPE_NOT_SUPPORTED` |
| Response VO contains raw kubeconfig or `infraConfig` | Reject in review; expose only `infraConfigConfigured` |

### 5. Good / Base / Bad Cases

- Good: K8S-specific config serialization, validation, connection checks, and namespace listing live in `KubernetesEnvironmentConnector`; `EnvironmentServiceImpl` only orchestrates and dispatches through `EnvironmentConnectorFactory`.
- Base: HOST remains a valid `infraType` but has no connector until SSH or host-group modeling is implemented; HOST saves no `infraConfig`.
- Bad: environment create/update methods directly instantiate Fabric8 clients or parse kubeconfig inline, because that couples CRUD to one infrastructure backend.

### 6. Tests Required

- Compile the DevOps server module with the reactor so local SNAPSHOT modules resolve: `mvn -pl yudao-module-devops/yudao-module-devops-server -am -DskipTests compile`.
- Add focused unit tests for the connector boundary:
  - K8S create builds encrypted JSON config from kubeconfig and namespace input.
  - K8S create without kubeconfig throws `ENVIRONMENT_KUBECONFIG_REQUIRED`.
  - K8S create without namespace throws `ENVIRONMENT_KUBERNETES_NAMESPACE_REQUIRED`.
  - K8S update without kubeconfig preserves old `infraConfig`.
  - K8S update with namespace only preserves old kubeconfig and updates namespace.
  - HOST-to-K8S update without kubeconfig throws `ENVIRONMENT_KUBECONFIG_REQUIRED`.
  - Response conversion exposes `kubernetesNamespace` but never exposes raw kubeconfig.
- Run targeted tests with the reactor, for example `mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dtest=KubernetesEnvironmentConnectorTest -Dsurefire.failIfNoSpecifiedTests=false test`.
- Verify Fabric8 version convergence with `mvn -pl yudao-module-devops/yudao-module-devops-server dependency:tree -Dverbose -Dincludes=io.fabric8` after changing BOM order or dependency versions.

### 7. Wrong vs Correct

#### Wrong

```java
public EnvironmentConnectionCheckRespVO checkEnvironmentConnection(Long id) {
    EnvironmentDO environment = validateEnvironmentExists(id);
    Config config = Config.fromKubeconfig(environment.getInfraConfig());
    try (KubernetesClient client = new KubernetesClientBuilder().withConfig(config).build()) {
        // K8S code is now hard-coded into generic environment service logic.
    }
}
```

#### Correct

```java
public EnvironmentConnectionCheckRespVO checkEnvironmentConnection(Long id) {
    EnvironmentDO environment = validateEnvironmentExists(id);
    EnvironmentConnector connector = environmentConnectorFactory.getConnector(environment.getInfraType());
    return connector.checkConnection(environment);
}
```

## Design Decision: Connector Boundary Per Infrastructure Type

**Context**: DevOps environments currently include `K8S` and `HOST`, and HOST may later become a host-group abstraction backed by SSH. A rigid K8S-only service design would make that migration expensive.

**Decision**: Store infrastructure-specific connection material as encrypted JSON in `dev_environment.infra_config`, dispatch behavior by `infraType` through `EnvironmentConnector`, and keep SDK-specific code in connector packages under `yudao-module-devops/.../framework/`.

**Extensibility**: Add HOST support by implementing another connector and, if needed, replacing the HOST config JSON with a host-group reference. Generic environment CRUD should continue to preserve the connector boundary and avoid importing SSH or Kubernetes SDK classes directly.

## Scenario: Docker Java Client Integration

### 1. Scope / Trigger

- Trigger: adding or changing Docker daemon operations in DevOps, including image build/pull/push, container lifecycle, or Docker host checks.
- Scope: `yudao-dependencies/pom.xml`, `yudao-module-devops/yudao-module-devops-server/pom.xml`, `framework/docker`, build/pipeline handlers that operate Docker, and focused Docker client configuration tests.

### 2. Signatures

- Maven dependency management:
  - Manage `com.github.docker-java:docker-java-core` and `com.github.docker-java:docker-java-transport-httpclient5` in `yudao-dependencies/pom.xml` with `${docker-java.version}`.
  - DevOps server declares both dependencies without module-local versions.
- Java beans:
  - `DockerClientProperties` uses prefix `yudao.devops.docker`.
  - `DockerClientConfiguration#dockerClientConfig(DockerClientProperties)` builds `DockerClientConfig`.
  - `DockerClientConfiguration#dockerHttpClient(DockerClientConfig, DockerClientProperties)` builds the Apache HttpClient 5 transport.
  - `DockerClientConfiguration#dockerClient(DockerClientConfig, DockerHttpClient)` exposes the default `DockerClient`.
  - `DockerClientFactory#getDefaultClient()` is the service-facing entry point.
- Config keys:
  - `yudao.devops.docker.host`
  - `yudao.devops.docker.tls-verify`
  - `yudao.devops.docker.cert-path`
  - `yudao.devops.docker.config-path`
  - `yudao.devops.docker.api-version`
  - `yudao.devops.docker.registry-url`
  - `yudao.devops.docker.registry-username`
  - `yudao.devops.docker.registry-password`
  - `yudao.devops.docker.registry-email`
  - `yudao.devops.docker.max-connections`
  - `yudao.devops.docker.connection-timeout`
  - `yudao.devops.docker.response-timeout`

### 3. Contracts

- Use `docker-java-core` plus exactly one transport implementation. Prefer `docker-java-transport-httpclient5` because it has long-term support and Unix socket / Windows npipe support.
- The default client must not ping Docker during Spring bean construction. Environments without Docker daemon should still start; Docker connectivity is validated only when a Docker operation is requested.
- If `yudao.devops.docker.host` is blank, allow docker-java to use its default discovery rules such as environment variables or local Docker config.
- Registry passwords and token-bearing values must never be logged or returned in API responses.
- Docker operations in services should inject `DockerClientFactory` or `DockerClient`, not instantiate docker-java builders directly.
- If future requirements need per-build-host or per-tenant Docker clients, extend `DockerClientFactory` with explicit create methods instead of duplicating initialization code in handlers.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| No Docker daemon available at app startup | Application still starts because no ping is performed in bean creation |
| Docker operation cannot connect to daemon | Operation-level service catches docker-java exception and maps it to a DevOps business error |
| Registry password configured | Password is only passed into `DockerClientConfig`; logs and responses must omit it |
| Unsupported or malformed Docker host | Fail at operation/config use boundary with a sanitized message |

### 5. Good / Base / Bad Cases

- Good: pipeline/build code calls `DockerClientFactory#getDefaultClient()` and keeps Docker SDK types behind framework or handler boundaries.
- Base: one default Docker client is enough while the platform operates a single local or configured Docker daemon.
- Bad: service methods call `DefaultDockerClientConfig.createDefaultConfigBuilder()` directly or log registry credentials for troubleshooting.

### 6. Tests Required

- Add focused configuration tests that instantiate `DockerClientConfig` without contacting Docker.
- Run:
  `mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dtest='DockerClientConfigurationTest' -Dsurefire.failIfNoSpecifiedTests=false test`
- When changing dependency versions, verify dependency resolution with a reactor compile or focused test command.

### 7. Wrong vs Correct

#### Wrong

```java
DockerClient client = DockerClientImpl.getInstance(DefaultDockerClientConfig.createDefaultConfigBuilder().build());
client.pingCmd().exec();
```

This duplicates client setup and fails startup or construction paths when Docker is unavailable.

#### Correct

```java
DockerClient client = dockerClientFactory.getDefaultClient();
client.pingCmd().exec();
```

The shared factory owns initialization; operation code owns connectivity error handling.

## Scenario: Kubernetes Environment Dashboard

### 1. Scope / Trigger

- Trigger: adding or changing read-only Kubernetes resource visibility for a DevOps environment.
- Scope: `/devops/environment/kubernetes/**` admin APIs, response VO contracts, `EnvironmentService` orchestration, `KubernetesEnvironmentConnector` Fabric8 resource queries, menu bootstrap, and focused tests.
- Use this scenario for environment dashboard data such as Services, Deployments, Pods, and future read-only resource tabs.

### 2. Signatures

- API signatures:
  - `GET /devops/environment/kubernetes/dashboard?id={environmentId}` returns `EnvironmentKubernetesDashboardRespVO`.
  - `GET /devops/environment/kubernetes/pods?id={environmentId}` returns `List<EnvironmentKubernetesPodRespVO>`.
  - `GET /devops/environment/kubernetes/deployments?id={environmentId}` returns `List<EnvironmentKubernetesDeploymentRespVO>`.
  - `GET /devops/environment/kubernetes/services?id={environmentId}` returns `List<EnvironmentKubernetesServiceRespVO>`.
- Menu signature:
  - Hidden route menu path `/devops/environment/dashboard`, component `devops/environment/dashboard`, component name `DevopsEnvironmentDashboard`, permission `devops:environment:query`.

### 3. Contracts

- Dashboard queries are read-only and must not mutate Kubernetes resources.
- Resource queries must be limited to `KubernetesEnvironmentConfig.namespace`; do not add free-form namespace switching unless product requirements explicitly need cross-namespace access.
- All APIs require an existing environment with `infraType=K8S`.
- Response payloads must expose display-safe derived resource fields only. They must not expose kubeconfig, tokens, certificates, raw Secret values, or full YAML manifests.
- Pod responses should include enough data for terminal entry gating:
  - `phase`
  - `containerNames`
  - `terminalEnabled`
  - ready/total container counts and restart count.
- `terminalEnabled` is true only for `phase=Running`; multi-container Pod selection is a frontend concern using `containerNames`.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| Environment id does not exist | Throw `ENVIRONMENT_NOT_EXISTS` |
| Environment `infraType` is not `K8S` | Throw `ENVIRONMENT_INFRA_TYPE_NOT_SUPPORTED` |
| K8S config has no kubeconfig | Throw `ENVIRONMENT_KUBECONFIG_REQUIRED` |
| K8S config has no namespace | Throw `ENVIRONMENT_KUBERNETES_NAMESPACE_REQUIRED` |
| Fabric8 list operation fails | Throw `ENVIRONMENT_KUBERNETES_CONNECTION_FAIL` with truncated upstream message |
| Pod is not Running | Return `terminalEnabled=false`; terminal open still validates and throws `KUBERNETES_POD_NOT_RUNNING` |

### 5. Good / Base / Bad Cases

- Good: `EnvironmentServiceImpl` validates the environment and dispatches to `KubernetesEnvironmentConnector`; the connector creates short-lived Fabric8 clients and converts Kubernetes objects to response VOs.
- Base: dashboard exposes Services, Deployments, and Pods in the environment namespace, plus counts for Service, Deployment, Pod, Running Pod, and abnormal Pod.
- Bad: controller or generic environment service imports Fabric8 types directly, accepts arbitrary namespace parameters, or returns raw Kubernetes manifests containing sensitive fields.

### 6. Tests Required

- Add focused connector tests for resource conversion:
  - Pod conversion returns phase, ready/total container counts, restart count, node, Pod IP, container names, and `terminalEnabled`.
  - Deployment conversion returns replica counts and images.
  - Service conversion returns type, ClusterIP, external IPs, selector, and clean port/targetPort fields.
- Run targeted tests with the reactor, for example:
  - `mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dtest=KubernetesEnvironmentConnectorTest -Dsurefire.failIfNoSpecifiedTests=false test`
- Run module compile:
  - `mvn -pl yudao-module-devops/yudao-module-devops-server -am -DskipTests compile`

### 7. Wrong vs Correct

#### Wrong

```java
@GetMapping("/kubernetes/pods")
public CommonResult<List<Pod>> getPods(@RequestParam Long id, @RequestParam String namespace) {
    // Leaks Fabric8 objects and lets callers jump namespaces.
}
```

#### Correct

```java
@GetMapping("/kubernetes/pods")
public CommonResult<List<EnvironmentKubernetesPodRespVO>> getKubernetesPods(@RequestParam("id") Long id) {
    return success(environmentService.getKubernetesPods(id));
}
```
