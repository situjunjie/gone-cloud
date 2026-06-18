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

## Scenario: HOST Host-Group Environment

### 1. Scope / Trigger

- Trigger: adding or changing DevOps HOST environment behavior, SSH host CRUD, host-group connection checks, or future host terminal/detail features.
- Scope: `yudao-module-devops` API enums, environment connector dispatch, `dev_environment_host` persistence, host request/response VOs, SSH client wiring, SQL bootstrap scripts, and focused tests.
- Use this scenario whenever a change adds a new `/devops/environment/host/**` API or changes host credential persistence.

### 2. Signatures

- DB signature:
  - `EnvironmentInfraTypeEnum.HOST` maps to `dev_infra_type=HOST`.
  - HOST environments are host groups. Store group metadata in `dev_environment`; keep `dev_environment.infra_config` empty unless a future group-level config is explicitly required.
  - Store individual hosts in `dev_environment_host`.
  - `dev_environment_host.password`, `private_key`, and `passphrase` are encrypted fields using `EncryptTypeHandler` and must be excluded from `toString()`.
  - Unique host key: `(tenant_id, env_id, host_key)`.
- API signatures:
  - `POST /devops/environment/host/create` accepts `EnvironmentHostSaveReqVO`.
  - `PUT /devops/environment/host/update` accepts `EnvironmentHostSaveReqVO`.
  - `DELETE /devops/environment/host/delete?id={hostId}` deletes a host.
  - `GET /devops/environment/host/get?id={hostId}` returns `EnvironmentHostRespVO`.
  - `GET /devops/environment/host/page?envId={environmentId}` returns `PageResult<EnvironmentHostRespVO>`.
  - `POST /devops/environment/host/check?id={hostId}` checks one host over SSH.
  - `POST /devops/environment/check?id={environmentId}` on HOST returns host-group check counts in `EnvironmentConnectionCheckRespVO`.
- Java signatures:
  - `HostEnvironmentConnector` implements `EnvironmentConnector` for `HOST`.
  - `HostSshClient#checkConnection(EnvironmentHostDO host)` performs SSH auth/connectivity validation.

### 3. Contracts

- Host CRUD must validate that `envId` exists and the environment has `infraType=HOST`.
- Host responses must never return `password`, `privateKey`, or `passphrase`; expose only safe fields such as `authType` and `credentialConfigured`.
- Create requires a credential matching `authType`:
  - `PASSWORD` requires `password`.
  - `PRIVATE_KEY` requires `privateKey`; `passphrase` is optional.
- Update may omit secret fields to preserve the old encrypted value.
- Changing auth type clears secrets for the other auth type.
- Single-host check updates `lastCheckStatus`, `lastCheckTime`, and a truncated, sanitized `lastCheckMessage`.
- Host-group check should aggregate host total/success/failure counts and must not fail the whole environment check just because one host fails.
- SSH implementation should use the shared managed JSch dependency if already available in the repository instead of introducing a second SSH library without a reason.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| Host API called with a missing environment id | Throw `ENVIRONMENT_NOT_EXISTS` |
| Host API called for non-HOST environment | Throw `ENVIRONMENT_INFRA_TYPE_NOT_SUPPORTED` |
| Host id does not exist | Throw `ENVIRONMENT_HOST_NOT_EXISTS` |
| Duplicate `hostKey` in the same HOST environment | Throw `ENVIRONMENT_HOST_KEY_DUPLICATE` |
| Create/update password auth without preserved or new password | Throw `ENVIRONMENT_HOST_PASSWORD_REQUIRED` |
| Create/update private-key auth without preserved or new private key | Throw `ENVIRONMENT_HOST_PRIVATE_KEY_REQUIRED` |
| SSH connection/auth fails | Throw `ENVIRONMENT_HOST_CONNECTION_FAIL` with a truncated, display-safe message |
| Response VO contains raw SSH secret material | Reject in review |

### 5. Good / Base / Bad Cases

- Good: `EnvironmentServiceImpl` dispatches HOST through `EnvironmentConnectorFactory`; host CRUD lives in `EnvironmentHostService`; SSH-specific code stays under `framework/host`.
- Base: first-phase HOST support is CRUD plus SSH connection check. It does not expose a terminal, arbitrary command execution, resource metrics, or active process lists.
- Bad: storing all hosts as a JSON array in `dev_environment.infra_config`, returning raw credentials in response VOs, or accepting arbitrary shell commands in a connection-check API.

### 6. Tests Required

- Add focused unit tests for:
  - HOST connector aggregates host-group success/failure counts.
  - Host create validates HOST environment and required credentials.
  - Host update preserves omitted secret fields.
  - Non-HOST environment rejects host CRUD.
  - Failed SSH check records failed status and throws the HOST connection error.
- Run:
  `mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dtest='HostEnvironmentConnectorTest,EnvironmentHostServiceImplTest' -Dsurefire.failIfNoSpecifiedTests=false test`
- Run module compile:
  `mvn -pl yudao-module-devops/yudao-module-devops-server -am -DskipTests compile`

### 7. Wrong vs Correct

#### Wrong

```java
if (EnvironmentInfraTypeEnum.HOST.getInfraType().equals(reqVO.getInfraType())) {
    return null;
}
```

This leaves HOST outside the connector boundary, so later host-group behavior gets scattered through generic environment CRUD.

#### Correct

```java
EnvironmentConnector connector = environmentConnectorFactory.getConnector(reqVO.getInfraType());
return connector.buildInfraConfig(reqVO, oldEnvironment);
```

`HostEnvironmentConnector` owns HOST-specific behavior while generic environment CRUD remains backend-neutral.

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

## Scenario: Docker Environment Dashboard and Container Operations

### 1. Scope / Trigger

- Trigger: adding or changing Docker-backed DevOps environment operations, including environment connection config, daemon dashboard, container list, rolling logs, or interactive terminal.
- Scope: `yudao-module-devops` API enums, environment request/response VOs, `EnvironmentService` orchestration, `framework/docker`, Docker log/terminal services, WebSocket configuration, SQL dictionary bootstrap, and focused tests.
- Use this scenario whenever a change adds a new `/devops/environment/docker/**` API, changes the Docker shape of `dev_environment.infra_config`, or changes `/devops/docker/containers/terminal`.

### 2. Signatures

- DB/config signature:
  - `EnvironmentInfraTypeEnum.DOCKER` maps to `dev_infra_type=DOCKER`.
  - Docker `infra_config` JSON shape: `{"host":"tcp://192.168.1.10:2376","tlsVerify":true,"apiVersion":"1.45","caCert":"...","clientCert":"...","clientKey":"..."}`.
  - `host` is required for Docker environments in this project; certificate fields are encrypted as part of `dev_environment.infra_config`.
- API signatures:
  - `EnvironmentSaveReqVO.dockerConfig` accepts `host`, `tlsVerify`, `apiVersion`, `caCert`, `clientCert`, and `clientKey`.
  - `EnvironmentRespVO` returns only `infraConfigConfigured`, `dockerHost`, and `dockerTlsEnabled` for Docker config.
  - `GET /devops/environment/docker/dashboard?id={environmentId}` returns `EnvironmentDockerDashboardRespVO`.
  - `GET /devops/environment/docker/containers?id={environmentId}&all=false` returns `List<EnvironmentDockerContainerRespVO>`.
  - `GET /devops/environment/docker/container-logs/stream?id={environmentId}&containerId={containerId}&tailLines=200` returns SSE events.
  - `WS /devops/docker/containers/terminal?environmentId={environmentId}&containerId={containerId}` opens an interactive Docker exec terminal.
- Runtime signatures:
  - Docker logs use SSE events named `log`, `error`, and `complete`.
  - Docker terminal reuses `KubernetesTerminalMessage` protocol: `input`, `resize`, `close`, `output`, `error`, and `closed`.

### 3. Contracts

- Docker-specific CRUD config building belongs in `DockerEnvironmentConnector#buildInfraConfig`; generic environment CRUD should only dispatch through `EnvironmentConnectorFactory`.
- Creating a Docker environment requires `dockerConfig.host`. Updating an existing Docker environment may omit fields to preserve the old encrypted config, including TLS certificate material.
- `DockerClientFactory#createClient(DockerEnvironmentConfig)` is the only place that should assemble per-environment docker-java clients; callers should close short-lived clients.
- Dashboard and list operations are read-only. They must validate the environment exists and `infraType=DOCKER` before calling Docker.
- Container list responses expose display fields only: ID, short ID, names, image, command, state/status, created time, ports, labels, and `terminalEnabled`.
- `terminalEnabled` is true only when Docker reports container `state=running`; terminal open must re-check the container inspect status before exec.
- Rolling logs must close the Docker callback and client when the SSE completes, times out, errors, or the client disconnects.
- Terminal sessions must close docker-java callback, stdin pipe, input pipe, Docker client, and WebSocket state when either side closes.
- Responses, logs, and exception messages must not expose `caCert`, `clientCert`, or `clientKey`.
- Docker environment mutation APIs are sensitive operations. Add `@LogRecord` on the Service-layer method for container lifecycle and Compose project lifecycle actions, with a DevOps-specific log type, stable subtype, `bizNo` based on environment id, and success text that includes only display-safe target identifiers.
- Docker Compose visibility should be derived from Docker labels such as `com.docker.compose.project` and `com.docker.compose.service`; do not shell out to `docker compose` for remote Docker environments.
- Compose project start/stop in the first phase means batch start/stop of existing containers in that project. Do not implement `docker compose up/down` semantics unless the requirement explicitly supplies trusted Compose files and working directories.
- Docker image list APIs are read-only in the first phase. Do not add pull, push, build, prune, or delete operations without a separate risk review and audit requirement.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| Create Docker environment without `dockerConfig.host` | Throw `ENVIRONMENT_DOCKER_HOST_REQUIRED` |
| Update existing Docker environment with omitted cert fields | Preserve old `caCert`, `clientCert`, and `clientKey` |
| Docker dashboard/list/log/terminal called for non-Docker environment | Throw `ENVIRONMENT_INFRA_TYPE_NOT_SUPPORTED` |
| Docker environment has blank/missing host in `infra_config` | Throw `ENVIRONMENT_DOCKER_HOST_REQUIRED` |
| Docker daemon cannot connect or operation fails before a container-specific boundary | Throw `ENVIRONMENT_DOCKER_CONNECTION_FAIL` with a truncated upstream message |
| Log or terminal container ID does not exist | Throw `DOCKER_CONTAINER_NOT_EXISTS` |
| Terminal container is not running | Throw `DOCKER_CONTAINER_NOT_RUNNING` |
| Log stream cannot be opened after container inspect | Throw `DOCKER_CONTAINER_LOG_STREAM_FAIL` |
| Exec cannot be created or started | Throw `DOCKER_TERMINAL_EXEC_FAIL` |

### 5. Good / Base / Bad Cases

- Good: environment CRUD stores encrypted Docker config via the connector, read APIs dispatch through `EnvironmentService`, log/terminal services validate the environment independently, and all docker-java clients are short-lived and closed.
- Base: first-phase Docker support is operational visibility and access only: daemon summary, container list, rolling logs, and terminal. It does not mutate container lifecycle.
- Bad: returning raw `infraConfig` or TLS PEM fields, instantiating `DefaultDockerClientConfig` inside controllers/services outside `DockerClientFactory`, or using Kubernetes "Pod" Java names for Docker container DTOs.

### 6. Tests Required

- Add focused connector/converter tests for:
  - Docker create builds config with host/TLS/cert fields.
  - Docker create without host throws `ENVIRONMENT_DOCKER_HOST_REQUIRED`.
  - Docker update preserves omitted secret fields from the old Docker config.
  - Docker container conversion sets names, short ID, ports, labels, and `terminalEnabled`.
  - Environment response conversion returns Docker display-safe fields and does not expose cert/private-key material.
- Run:
  `mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dtest='DockerEnvironmentConnectorTest,EnvironmentConvertTest' -Dsurefire.failIfNoSpecifiedTests=false test`
- Run module compile:
  `mvn -pl yudao-module-devops/yudao-module-devops-server -am -DskipTests compile`

### 7. Wrong vs Correct

#### Wrong

```java
@GetMapping("/docker/containers")
public List<Container> containers(Long id) {
    DockerClient client = DockerClientImpl.getInstance(DefaultDockerClientConfig.createDefaultConfigBuilder().build());
    return client.listContainersCmd().exec();
}
```

This bypasses environment validation, leaks SDK objects into the API, and leaves Docker client lifecycle ambiguous.

#### Correct

```java
public List<EnvironmentDockerContainerRespVO> getDockerContainers(Long id, Boolean all) {
    EnvironmentDO environment = validateEnvironmentExists(id);
    validateDockerEnvironment(environment);
    return dockerEnvironmentConnector.listContainers(environment, all);
}
```

The environment service owns existence/type validation, while the connector owns Docker SDK calls and display-safe conversion.

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

## Scenario: Kubernetes Pod Log Stream

### 1. Scope / Trigger

- Trigger: adding or changing read-only Kubernetes Pod log viewing for a DevOps environment.
- Scope: `/devops/environment/kubernetes/pod-logs/**` admin APIs, SSE event payloads, `KubernetesPodLogService`, Fabric8 `LogWatch` lifecycle handling, error codes, and focused Mockito tests.
- Use this scenario when implementing live Pod logs. Use the existing WebSocket terminal path only for interactive exec sessions.

### 2. Signatures

- SSE API:
  - `GET /devops/environment/kubernetes/pod-logs/stream?id={environmentId}&podName={podName}&namespace={namespace}&containerName={containerName}&tailLines={tailLines}`
  - Produces `text/event-stream`.
  - Requires `devops:environment:query`.
- Request fields:
  - `id` required `Long`: DevOps environment id.
  - `podName` required `String`: Kubernetes Pod name.
  - `namespace` optional `String`: must be blank or equal to the environment configured namespace.
  - `containerName` optional `String`: required only for multi-container Pods.
  - `tailLines` optional `Integer`: bounded initial tail line count; default should be conservative.
- SSE events:
  - `log`: data is a display-safe log line VO with line number, Pod name, container name, and content.
  - `error`: data is a sanitized message for stream-time failures after the SSE response has started.
  - `complete`: stream ended normally.

### 3. Contracts

- Pod logs are read-only and must not mutate Kubernetes resources.
- Log APIs must use `KubernetesEnvironmentConfig.namespace`; do not allow arbitrary namespace switching from the frontend.
- The service must validate the environment exists and has `infraType=K8S`.
- The service must create short-lived Fabric8 clients and close both `LogWatch` and `KubernetesClient` on normal completion, client disconnect, timeout, and error.
- Single-container Pods may omit `containerName`; multi-container Pods must explicitly choose one container.
- Pod logs can be read for non-running Pods when Kubernetes allows it. Do not reuse terminal's `phase=Running` requirement for log viewing.
- Do not persist Pod log lines in this MVP. Persisted command/build logs belong to the pipeline run log-line path.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| Environment id does not exist | Throw `ENVIRONMENT_NOT_EXISTS` |
| Environment `infraType` is not `K8S` | Throw `ENVIRONMENT_INFRA_TYPE_NOT_SUPPORTED` |
| K8S config has no kubeconfig | Throw `ENVIRONMENT_KUBECONFIG_REQUIRED` |
| K8S config has no namespace | Throw `ENVIRONMENT_KUBERNETES_NAMESPACE_REQUIRED` |
| Request namespace differs from environment namespace | Throw `ENVIRONMENT_KUBERNETES_NAMESPACE_NOT_MATCH` |
| Pod does not exist | Throw `KUBERNETES_POD_NOT_EXISTS` |
| Pod has multiple containers and `containerName` is blank | Throw `KUBERNETES_POD_CONTAINER_REQUIRED` |
| Target container does not exist | Throw `KUBERNETES_POD_CONTAINER_NOT_EXISTS` |
| Fabric8 watch/log operation fails before response starts | Throw `KUBERNETES_POD_LOG_STREAM_FAIL` with truncated upstream message |
| Stream fails after response starts | Send SSE `error` when possible, then complete with error and close resources |

### 5. Good / Base / Bad Cases

- Good: controller exposes the SSE endpoint and delegates all Kubernetes work to a service; service validates environment, resolves namespace/container, opens `LogWatch`, streams line VOs, and always closes resources.
- Base: frontend opens one `EventSource` per selected Pod/container and closes it when the log panel closes or selection changes.
- Bad: controller imports Fabric8 types, returns raw `Pod` or raw Kubernetes manifests, accepts cross-namespace log reads, or leaves `LogWatch` open after client disconnect.

### 6. Tests Required

- Service test: single-container Pod without `containerName` opens the stream and tails the requested line count.
- Service test: multi-container Pod without `containerName` throws `KUBERNETES_POD_CONTAINER_REQUIRED`.
- Service test: missing Pod throws `KUBERNETES_POD_NOT_EXISTS`.
- Service test: namespace mismatch throws `ENVIRONMENT_KUBERNETES_NAMESPACE_NOT_MATCH`.
- Service test: `LogWatch` and `KubernetesClient` close when the stream finishes.
- Focused command:
  `mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dtest=KubernetesPodLogServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test`

### 7. Wrong vs Correct

#### Wrong

```java
@GetMapping("/kubernetes/pod-logs/stream")
public SseEmitter stream(@RequestParam Long id, @RequestParam String namespace, @RequestParam String podName) {
    KubernetesClient client = kubernetesClientFactory.create(loadKubeconfig(id));
    return streamAnyNamespace(client.pods().inNamespace(namespace).withName(podName).watchLog());
}
```

#### Correct

```java
@GetMapping(value = "/kubernetes/pod-logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter stream(@RequestParam("id") Long id,
                         @RequestParam(value = "namespace", required = false) String namespace,
                         @RequestParam("podName") String podName,
                         @RequestParam(value = "containerName", required = false) String containerName,
                         @RequestParam(value = "tailLines", required = false) Integer tailLines) {
    return kubernetesPodLogService.streamPodLogs(id, namespace, podName, containerName, tailLines);
}
```
