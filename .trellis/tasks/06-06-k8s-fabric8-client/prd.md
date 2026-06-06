# 接入 Fabric8 Kubernetes SDK

## Goal

为 DevOps 环境实体接入 Kubernetes 集群连接能力，先实现 K8s 类型环境的连接配置保存、连接检测和基础资源查询，并为后续 HOST / 主机组能力保留扩展空间。

## What I already know

* 环境实体已有 `infraType` 字段，字典值包括 `K8S` 和 `HOST`。
* 当前环境 CRUD 位于 `yudao-module-devops/yudao-module-devops-server`。
* 环境表当前没有连接配置字段，需要新增承载不同基础设施类型配置的数据字段。
* 用户希望先实现 K8s，HOST 后续可能通过 Apache SSH 库，并且可能演进为“环境关联主机组、主机组包含多台主机”。
* Fabric8 Kubernetes Client 当前 Maven Central 版本为 `io.fabric8:kubernetes-client:7.7.0`。

## Requirements

* 引入 Fabric8 Kubernetes Java SDK 到 DevOps server 模块。
* 环境新增基础设施连接配置字段，K8s 先支持保存 kubeconfig 文本。
* K8s kubeconfig 属于敏感连接材料，数据库需加密存储，不在响应 VO 中返回原文。
* 创建 / 更新 K8s 环境时校验 kubeconfig 不能为空；更新时不传 kubeconfig 则保留旧值。
* 非 K8s 类型暂不强制连接配置，避免阻断 HOST 后续建模。
* 新增 K8s 连接检测接口，能通过 Fabric8 客户端访问集群并返回当前 Namespace 数量等基本结果。
* 新增 Namespace 列表查询接口，作为后续工作负载操作的最小基础能力。
* 代码设计需按 `infraType` 分发，不能把 K8s SDK 逻辑硬编码进环境 CRUD。

## Acceptance Criteria

* [x] `dev_environment` 有可承载连接配置的字段，DO / VO / SQL 同步。
* [x] K8s 环境保存时使用加密 TypeHandler，响应只返回是否已配置的摘要信息。
* [x] `POST /devops/environment/check-connection?id=...` 支持 K8s 连接检测；同时保留 `/check` 别名以贴合现有 DevOps 代码源接口风格。
* [x] `GET /devops/environment/kubernetes/namespaces?id=...` 返回 Namespace 列表。
* [x] HOST 类型不会调用 Fabric8，也不会要求 kubeconfig。
* [x] DevOps server 模块能编译通过。

## Out of Scope

* HOST / SSH / 主机组实体建模。
* Deployment / Pod / Service 等资源创建、更新、删除。
* kubeconfig 以外的 K8s 认证模式表单化配置。
* 前端页面改造。

## Technical Notes

* 环境相关代码：
  * `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/dal/dataobject/environment/EnvironmentDO.java`
  * `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/service/environment/EnvironmentServiceImpl.java`
  * `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/controller/admin/environment/EnvironmentController.java`
* 代码源模块已有敏感 token 加密存储模式，可复用 `EncryptTypeHandler` + `autoResultMap = true` + 响应不返回原文的设计。
* Fabric8 官方 README 表明可通过 `KubernetesClientBuilder` 创建客户端，并可通过 `ConfigBuilder` 显式配置客户端。
* 验证命令：
  * `mvn -pl yudao-module-devops/yudao-module-devops-server -am -DskipTests compile`
  * `mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dtest=KubernetesEnvironmentConnectorTest -Dsurefire.failIfNoSpecifiedTests=false test`
  * `mvn -pl yudao-module-devops/yudao-module-devops-server dependency:tree -Dverbose -Dincludes=io.fabric8`

## Research References

* Maven Central: `io.fabric8:kubernetes-client:7.7.0`
* Fabric8 GitHub README: client supports fluent DSL and config via `KubernetesClientBuilder` / `ConfigBuilder`
