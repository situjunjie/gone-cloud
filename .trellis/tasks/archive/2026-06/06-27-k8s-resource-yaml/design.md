# Design

## Scope

在 `yudao-module-devops` 的环境大盘链路中补一个 K8s 资源 YAML 查询接口：

- `EnvironmentController`
- `EnvironmentService`
- `KubernetesEnvironmentConnector`
- request/response VO
- K8s 资源类型枚举

## API Shape

新增接口：

- `GET /devops/environment/kubernetes/resource-yaml`

请求参数：

- `id`：环境编号
- `kind`：资源类型，限定为 `NAMESPACE` / `POD` / `DEPLOYMENT` / `SERVICE`
- `name`：资源名称
- `namespace`：可选，未传时默认使用环境配置 namespace

返回：

- `kind`
- `name`
- `namespace`
- `yaml`

## Implementation Strategy

1. 在 API 模块增加 K8s 资源类型枚举。
2. 在 controller 增加一个查询 VO 和新接口。
3. 在 service 层做环境存在性校验和 K8s 类型校验。
4. 在 K8s connector 内按资源类型调用 Fabric8 client 获取资源，再用 YAML 序列化输出。
5. 对 namespaced 资源沿用环境默认 namespace；显式 namespace 与环境 namespace 不一致时拒绝。
6. 补单测覆盖：
   - 成功返回 YAML
   - 非 K8s 环境拒绝
   - 资源不存在拒绝

## Tradeoffs

- 直接返回 YAML 字符串最适合前端展示，但返回 VO 更便于未来补充资源元信息。
- 仅支持当前大盘已暴露的资源类型，避免把“任意 K8s 资源”做成过宽的通用能力。

