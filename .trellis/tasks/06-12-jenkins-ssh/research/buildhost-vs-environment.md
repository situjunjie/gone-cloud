# 核对结论:BuildHost vs Environment(info.md 开放点 1 / 3)

> ST-3 实现前对 `framework/infra/EnvironmentConnector` 体系与 `dev_environment` 模型的核对结论。
> 闭环 info.md 开放点 1(BuildHost vs Environment)与开放点 3(凭据存储载体)。

## 核对的现有资产

| 资产 | 路径 | 语义 |
|---|---|---|
| `EnvironmentConnector` / `Factory` | `framework/infra/` | 按 `infraType` 分发的连接器边界,当前仅 `K8S` 有实现 |
| `EnvironmentInfraTypeEnum` | `*-api/enums/` | `K8S` / `HOST` 两值,`HOST` 暂无连接器 |
| `EnvironmentDO` + `dev_environment` | `dal/dataobject/environment/` | 租户级环境,`infra_config` 加密 JSON(K8S 为 kubeconfig+namespace) |
| `KubernetesEnvironmentConfig` | `framework/kubernetes/` | K8S 连接配置(kubeconfig / namespace) |

## 关键判断:Environment 是「部署目标」语义,不是「构建运行机」语义

`dev_environment` 与 `EnvironmentConnector` 体系服务的是**部署目标(deploy target)**:

- spec `devops-infra-guidelines.md` 明确 connector 的职责是 `buildInfraConfig` /
  `checkConnection` /(K8S)namespace 列举、Pod/Deployment 仪表盘 —— 全是「往这个环境里部署、并观测其中运行的工作负载」。
- `dev_environment` 通过 `dev_application_env` 与应用、流水线定义绑定,是**发布维度**的实体。
- `KubernetesEnvironmentConfig.namespace` 注释直接写「部署目标 Namespace」。
- spec 里 "Kubernetes now and HOST/host-group later" 指的是**部署目标**从 K8S 扩展到主机/主机组
  (即"把应用部署到一台主机"),与「在哪台机器上跑构建 shell」是两件事。

`dev_build_host` 服务的是**构建运行机(CI runner)**:

- 它是流水线 BUILD 类节点 SSH 进去跑 `mvn` / `docker build` 的执行机,不接收应用部署。
- 调度维度不同:需要 `labels` / `max_concurrency` 做容量调度,Environment 没有这些概念也不该有。
- 凭据维度不同:构建机要 SSH 登录凭据(password / private key),用于「登录这台机器」;
  Environment 的 `infra_config` 是「访问这个集群/主机做部署」的凭据,语义不重叠。
- 生命周期不同:一台构建机可服务任意应用/任意环境的构建,与具体 application_env 无绑定关系。

## 决策

**新建 `dev_build_host` 表 + `BuildHostDO`,不复用 / 不扩展 Environment。**

理由:
1. 复用会污染 `EnvironmentConnector` 边界 —— connector 当前契约围绕「部署+观测」,
   塞进「SSH 跑 shell」会让 `getInfraType` 维度同时承担两种不相干语义。
2. Environment 的 `HOST` 类型未来要落地的是**部署到主机**的 connector,与构建机抢占同一枚举值会冲突。
3. 字段不重叠:构建机的 `labels` / `max_concurrency` / SSH 登录凭据在 Environment 模型里无处安放。

保持 `EnvironmentConnector` 的 HOST/host-group 演进通道**不动**,留给未来的「主机部署目标」。

## 开放点 3:凭据存储载体

**复用项目现成的 `EncryptTypeHandler`(AES),凭据加密内联存储在 `dev_build_host` 行上,不新建独立凭据表。**

核对到的现成能力:

- `cn.iocoder.yudao.framework.mybatis.core.type.EncryptTypeHandler`:基于 Hutool AES 的字段级 TypeHandler,
  密钥取 `mybatis-plus.encryptor.password`。
- devops 模块 `application.yaml` 已配置 `mybatis-plus.encryptor.password`(L74-75),开箱可用。
- 既有范本 `RepositoryProviderDO.accessToken`:`@TableField(typeHandler = EncryptTypeHandler.class)` +
  `@ToString.Exclude`,DO 字段加密落库、`toString` 排除、响应 VO 不回显(只给 `tokenMask`)。

落地方式:

- `BuildHostDO` 的 SSH 登录敏感字段(`password` / `privateKey` / `passphrase`)用
  `@TableField(typeHandler = EncryptTypeHandler.class)` + `@ToString.Exclude` 加密落库。
- `@TableName(value = "dev_build_host", autoResultMap = true)`(spec 要求加密字段必须 `autoResultMap=true`)。
- `credentialRef` 字段保留为「引用平台凭据存储 key」的占位:registry / git token 已分别由
  `RepositoryProviderDO`(git token)等承载,后续若需独立凭据中心再扩展;本期不造独立凭据表,避免过度设计。
- 凭据值**绝不进日志/异常**:`@ToString.Exclude` + 不放入任何响应 VO,异常信息只引用 host/name 不引用凭据。

## 后续(超出 ST-3 范围,仅记录)

- `BuildHost.credential_ref` 指向的「平台凭据中心」若未来落地,可把 SSH key / registry / git token 统一收进去,
  届时 `dev_build_host` 内联敏感字段可迁移为引用。本期不做。
