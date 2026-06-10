# implement: manifest container deploy v1

## Goal

把 DevOps 容器部署节点改造成 V1 原始 Deployment YAML 部署模式：在节点参数中存原始 YAML，发布时渲染变量、校验单文档 Deployment、替换目标容器镜像并 apply 到环境绑定 namespace，同时把原始与渲染后 YAML 快照写入部署单。

## What I already know

* 用户确认 V1 只支持单文档 Deployment YAML。
* 用户确认 `replicas` 以 node 参数优先覆盖 YAML。
* 用户确认直接废弃旧 patch 模式，不保留兼容逻辑。
* 当前容器部署节点参数仍是 `deploymentName/workloadKind/containerName/image/...`。
* 当前 `DeploymentOrderServiceImpl` 执行逻辑基于读取现有 Deployment 再 patch。
* 当前 `ContainerDeployConfigContext` 未包含 manifest 相关字段。

## Requirements

* 容器部署节点参数改为 `RAW_MANIFEST` 模式。
* 后端校验原始 YAML 必须为单文档 Deployment，且包含目标容器。
* 发布执行按 YAML createOrReplace Deployment，而不是 patch 既有 Deployment。
* namespace 强制以环境绑定 namespace 为准。
* DeploymentOrder 记录原始 YAML 与渲染后 YAML 快照。

## Acceptance Criteria

* [ ] 容器部署节点 schema 改为 `deployMode + manifestYaml + containerName + image + replicas + rolloutTimeoutSeconds`
* [ ] 保存流水线时非法 YAML 被拒绝
* [ ] 部署执行从 manifest 解析 workloadName，并 apply Deployment
* [ ] node `replicas` 非空时覆盖 YAML 中 replicas
* [ ] DeploymentOrder `configJson` 含原始 YAML 和渲染后 YAML
* [ ] 单元测试覆盖 manifest 校验和部署执行核心分支

## Out of Scope

* Service / Ingress / ConfigMap / Secret
* 多文档 YAML
* Helm / Kustomize
* 旧 patch 模式兼容
* 流水线定义迁移脚本

## Technical Notes

* Node registry: `PipelineNodeRegistryServiceImpl`
* Node validation: `PipelineSpecValidationServiceImpl`
* Deploy execution: `DeploymentOrderServiceImpl`
* Snapshot context: `ContainerDeployConfigContext`
* Need a dedicated Kubernetes manifest support helper to avoid bloating deployment service logic

## Frontend Contract

* 容器部署节点表单字段：
  * 基础设施类型：固定 `K8S`
  * 部署模式：固定 `RAW_MANIFEST`
  * `Deployment YAML`
  * `目标容器名称`
  * `镜像地址或表达式`
  * `副本数（可空）`
  * `Rollout 超时秒数`
* 默认 YAML 模板包含 `${APP_KEY}` `${NAMESPACE}` `${IMAGE}` 变量。
