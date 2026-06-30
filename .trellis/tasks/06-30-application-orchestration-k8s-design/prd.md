# 应用编排 K8s 部署配置设计

## Goal

为 DevOps 应用新增“应用编排”配置能力。第一阶段只交付 Kubernetes 部署编排配置：用户在应用维度进入编排页面，按组件模板编辑 Kubernetes YAML、镜像占位符和部署变量，发布后形成可追溯版本。

本阶段保持“主机部署”入口可见但不可用，只聚焦 K8s 配置编排。本阶段不接入流水线部署 StepHandler，现有 `K8sDeploy` 仍按原有 RAW_MANIFEST 方式执行。

## Confirmed Facts

- 现有应用表是 `dev_application`，应用环境关系表是 `dev_application_env`，应用发布页和流水线入口都以 `applicationEnvId` 为核心上下文。证据：`sql/mysql/devops.sql:159`、`sql/mysql/devops.sql:219`、`yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/service/application/ApplicationServiceImpl.java`。
- 现有流水线定义已经是“一个应用环境一条定义”，`dev_pipeline_definition` 对 `application_env_id` 有唯一约束。证据：`sql/mysql/devops.sql:219`。
- 流水线定义版本使用 definition 指针表 + version 表；本需求倾向应用编排采用单表版本模型，即同一业务 key 下最大发布版本号为当前版本。证据：用户要求“单表使用最新版本号为最新就可以实现，可以不用单独一个版本表”。
- 现有部署单 `dev_deployment_order` 已保存部署上下文快照 `config_json`，并记录 namespace、workload、container、image、replicas、revision 等审计字段。证据：`sql/mysql/devops.sql:421`、`DeploymentOrderDO`。
- 现有 `K8sDeploy` 支持 RAW_MANIFEST，当前从流水线步骤参数读取 `manifestYaml/containerName/image/replicas/rolloutTimeoutSeconds`；Service + 一个 Deployment 的多文档 YAML 已由 `KubernetesDeploymentManifestSupport` 支持。证据：`DeploymentOrderServiceImpl#buildRawManifestConfig`、`KubernetesDeploymentManifestSupport#parseSupportedResources`。
- 当前工作区没有前端源码目录，截图中的界面方案需要以接口契约和页面行为描述落地，具体前端文件路径待前端仓库确认。

## Requirements

- R1. 在应用页面提供“应用编排”入口，包含“Kubernetes 部署”和“主机部署”两个页签；第一阶段 Kubernetes 可用，主机部署展示占位态或禁用态。
- R2. Kubernetes 编排支持按顺序管理资源组件，MVP 组件模板支持 `Secret`、`ConfigMap`、`PersistentVolumeClaim`、`Service`、`Deployment`、`Ingress` 和空模板；一个编排版本中必须至少包含一个 Deployment，便于后续部署接入选择 rollout 观察对象。
- R3. 编排编辑页支持组件模板选择、资源卡片/顺序展示、组件 YAML 编辑、画布布局渲染、提取占位符、镜像占位符、部署变量、变量分组展示、校验、保存草稿、发布、查看版本记录。
- R4. 编排发布后生成不可变历史版本；同一编排业务 key 下最大 `version_no` 的已发布版本就是当前有效版本，不引入单独版本表。
- R5. 编排归属为应用全局：一个应用维护一套 Kubernetes 编排版本，每个发布版本声明可用环境标签，并包含按环境标签分组的变量值。
- R6. 环境标签来源固定为 `dev_environment.env_stage`，即 DEV/TEST/PRE/PROD 等环境阶段；第一阶段不新增自定义环境标签表或多标签绑定。
- R7. 编排配置支持按 `env_stage` 选择变量组并生成渲染后的多文档 Kubernetes YAML 预览/导出，供用户确认配置结果。
- R8. 本阶段不改现有流水线部署能力；现有 RAW_MANIFEST 部署继续可用，新编排配置暂不接入部署 StepHandler。
- R9. 权限独立于流水线配置，至少包含查询、保存草稿、发布/回滚或基于历史版本重新发布。
- R10. 编排存储必须区分语义组件数据和画布布局数据：后端校验、聚合 YAML 和版本记录按组件语义数据执行，前端画布按布局数据还原节点、连线和视口。
- R11. 校验必须覆盖组件名称、组件依赖环、组件顺序、Kubernetes YAML 解析、资源类型白名单、Deployment 存在性、metadata.name、容器名、变量引用、镜像占位符和 namespace 来源。
- R12. 编排页面必须展示当前版本绑定的环境标签，以及每个环境标签下的变量组；当某个绑定标签没有变量组时，配置发布需要明确失败。

## Technical Notes

- 方案已确定为“应用级编排 + 版本绑定环境标签 + 环境变量组”：一个应用一套编排模板，发布版本声明适用的环境标签；本阶段渲染预览按环境标签选择变量组。
- 组件数据和画布数据分离：`component_json` 保存组件模板、YAML 片段、顺序和依赖，`canvas_json` 保存节点坐标、连线和视口状态。
- 现有 `applicationEnvId` 不作为编排版本归属；本阶段仅在渲染预览时用于读取 `dev_environment.env_stage` 和选择变量组。
- YAML 模板建议先支持受控占位符替换，不引入 Helm/Go template 运行时。兼容截图中 `{{ .Values.xxx }}` 风格的同时，将其解析为平台变量表。

## Acceptance Criteria

- [ ] 能为某个应用创建 Kubernetes 编排草稿，包含 Secret、ConfigMap、PVC、Service、Deployment、Ingress 或空模板组件，支持组件顺序、依赖、YAML 片段、镜像占位符、变量定义、环境标签绑定和变量组。
- [ ] 校验通过后发布版本，版本记录能展示版本号、发布人、发布时间、是否当前版本、发布备注，并能查看历史版本内容。
- [ ] 当前有效版本按最大已发布 `version_no` 得出，不依赖单独 definition/version 双表。
- [ ] 编排详情能按指定 `env_stage` 变量组渲染出完整多文档 Kubernetes YAML，并按组件顺序展示资源。
- [ ] 修改并重新发布编排后，当前版本指向新发布版本，历史版本内容保持不可变。
- [ ] 组件依赖成环、绑定环境标签缺少变量组、YAML 无 Deployment、Deployment 容器名不存在等场景有明确错误。
- [ ] 现有 RAW_MANIFEST 流水线部署继续可用。

## Out Of Scope

- 主机部署编排实现。
- 应用编排接入流水线部署 StepHandler。
- 部署单字段扩展、部署单重试使用编排快照。
- Helm Chart、Kustomize、CRD 任意资源部署。
- 多 Deployment 同一编排的批量 rollout。
- 编排版本差异对比、审批流、灰度发布和回滚集群资源。

## Decisions

- D1. 环境标签来源使用 `dev_environment.env_stage`。第一阶段不新增自定义标签管理和环境多标签绑定能力。
