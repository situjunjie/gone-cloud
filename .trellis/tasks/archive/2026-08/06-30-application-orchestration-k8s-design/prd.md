# 应用编排 K8s 部署配置设计

## Goal

为 DevOps 应用新增“应用编排”配置能力。第一阶段只交付 Kubernetes 资源顺序和 YAML 编辑：用户在应用维度添加、删除、复制、排序资源组件，编辑每个组件的 Kubernetes YAML，并保存为应用编排草稿。

主机部署入口本期保持可见但不可用。本期不做环境变量渲染、发布版本或回滚，也不接入流水线部署；现有 `K8sDeploy` 继续使用 RAW_MANIFEST。

## Confirmed Facts

- 编排归属实体为 `dev_application`，一个应用设置一份 Kubernetes 编排。
- `dev_application_env` 和 `dev_environment.env_stage` 不参与本期编排归属或配置。
- 当前 K8s 流水线仅支持 RAW_MANIFEST，并从流水线步骤读取 `manifestYaml` 等参数。
- 后端已有 Fabric8 Kubernetes 依赖和 `KubernetesDeploymentManifestSupport`，可复用 YAML 解析能力，但现有 helper 的资源白名单需要扩展或拆分后才能支持本期全部组件类型。
- 当前工作区没有业务前端源码，截图中的界面方案以 API 契约和页面行为描述落地，具体前端文件待前端仓库确认。

## Requirements

- R1. 应用页面提供“应用编排”入口，包含“Kubernetes 部署”和“主机部署”两个页签；Kubernetes 可用，主机部署禁用并展示“暂未开放”。
- R2. Kubernetes 编排支持 `Secret`、`ConfigMap`、`PersistentVolumeClaim`、`Service`、`Deployment`、`Ingress` 和空模板组件。
- R3. 用户可新增、删除、复制、重命名组件，并通过拖拽调整资源顺序；顺序保存后重新进入页面保持一致。
- R4. 用户可选中组件并独立编辑 YAML；切换组件或调整顺序不得改变 YAML 原文。
- R5. 一个应用只保存一份 `K8S_DEPLOYMENT` 草稿，本期不区分环境、不生成发布版本。
- R6. 组件语义和画布布局分开保存：组件 id、名称、类型、顺序、依赖和 YAML 属于语义数据；节点位置、连线和视口属于布局数据。
- R7. 保存草稿必须整体校验成功，失败时返回定位到组件的错误，不覆盖数据库中的上一份有效草稿。
- R8. 校验覆盖组件 id/名称唯一性、依赖引用、依赖环、资源顺序、YAML 语法、资源类型白名单、`kind` 一致性和 `metadata.name`。
- R9. 权限独立于流水线配置，包含查询和更新两个权限点。
- R10. 本期不修改现有流水线部署能力和部署单结构。

## Technical Notes

- 唯一业务键为 `tenant_id + app_id + orchestration_type`。
- `component_json` 保存组件语义，列表顺序是唯一的资源执行顺序来源；`applyOrder` 使用连续整数并由后端按列表顺序归一化。
- `dependsOn` 表达依赖校验和画布连线，不隐式改写用户排序。若依赖目标排在当前组件之后，校验失败并提示调整顺序。
- `canvas_json` 只用于恢复节点位置、连线展示和视口，不参与部署语义排序。
- YAML 按原文保存和解析校验，不执行 `{{ ... }}` 或 `${...}` 占位符替换，不引入 Helm/Go template。

## Acceptance Criteria

- [x] 能为应用创建 Kubernetes 编排草稿并添加七类组件模板。（后端：模板接口 + save-draft 已实现并测试）
- [x] 资源卡片按用户配置顺序展示，拖拽排序保存后重新进入页面顺序不变。（后端：component_json 列表顺序即权威顺序，applyOrder 归一化；前端 UI 待前端仓库接入）
- [x] 每个组件可以独立编辑 YAML，调整顺序和切换组件不改变 YAML 内容。（后端：YAML 按原文存取，不改写；前端编辑器待接入）
- [x] 组件新增、删除、复制、重命名及依赖调整后可保存并重新加载。（后端：save-draft/get 已支持；前端交互待接入）
- [x] YAML 语法错误、类型不支持、`kind` 不一致、缺少 `metadata.name`、依赖不存在、依赖逆序或依赖成环时，返回明确的组件级错误。（已实现并测试）
- [x] 校验失败不会覆盖上一份有效草稿。（已实现并测试）
- [x] 不同应用的编排相互隔离；同一应用重复保存覆盖同一份草稿，不产生历史版本。（唯一键 tenant_id+app_id+orchestration_type，已实现并测试）
- [x] 现有 RAW_MANIFEST 流水线部署继续可用。（未改动流水线链路，KubernetesDeploymentManifestSupport 回归测试通过）

## Out Of Scope

- 主机部署编排实现。
- 环境标签、变量组、镜像占位符、占位符提取和 YAML 渲染预览。
- 发布、版本历史、版本差异、审批和回滚。
- 应用编排接入流水线 StepHandler 或直接操作 Kubernetes 集群。
- 部署单字段扩展及部署快照关联。
- Helm Chart、Kustomize、CRD 和自定义资源。
- 多 Deployment rollout、灰度发布和集群资源回滚。

## Decisions

- D1. 本期只交付资源顺序、组件 YAML 编辑、校验和单草稿保存。
- D2. 编排属于应用，不属于应用环境。
- D3. 用户列表顺序是权威顺序，依赖关系只做约束校验，不自动拓扑重排。
- D4. 保存采用“校验通过才覆盖”的原子语义，不保存无效草稿。
