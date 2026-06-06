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
