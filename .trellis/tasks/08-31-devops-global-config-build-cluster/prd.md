# DevOps 全局配置类

## Goal

为 DevOps 模块定义一个可扩展、可 JSON 序列化的全局配置类。该配置未来由调用方自行通过 Infra 配置管理能力存入 `ConfigDO#value`；本期仅定义类结构。

## Background

- DevOps 全局配置目前由整个平台共用一份。
- 首个配置项是构建集群，引用一个 DevOps Kubernetes 环境。
- 构建集群配置是平台全局配置，直接保存全局唯一的 DevOps 环境 ID。
- 配置持久化、初始化、查询和修改均由调用方使用 Infra 现有能力完成，不属于本任务。

## Requirements

- R1：新增 `DevopsConfig` 配置类，并按业务域预留可扩展的嵌套分组。
- R2：根配置包含 `build` 构建配置分组。
- R3：构建配置包含 `cluster` 构建集群配置。
- R4：构建集群配置只包含 `environmentId`，用于引用一个 DevOps K8S 环境。
- R5：各层嵌套对象提供非空默认值，空 JSON 或缺少新增字段时可得到可用对象结构。
- R6：JSON 反序列化忽略未知字段，支持未来新增配置项后的兼容读取。

## Acceptance Criteria

- [x] AC1：DevOps server 中存在 `DevopsConfig` 类，JSON 结构为 `build.cluster.environmentId`，不包含 `tenantId`。
- [x] AC2：新建配置对象时，`build` 和 `build.cluster` 均不为 `null`。
- [x] AC3：`{}` 可反序列化为默认配置，未知字段不会导致反序列化失败。
- [x] AC4：包含构建集群引用的配置可完成 JSON 序列化与反序列化往返，环境 ID 保持一致。
- [x] AC5：DevOps server 编译及配置类聚焦测试通过。

## Out Of Scope

- DevOps 或 Infra API 变更。
- 配置 Service、Controller、缓存、校验或环境解析。
- `ConfigDO` 记录的创建、查询、修改或初始化 SQL。
- K8S 环境候选列表、环境删除保护和前端页面。
