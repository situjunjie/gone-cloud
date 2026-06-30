# 应用编排 K8s 部署配置实施计划

## Phase 1: Backend Model

- 新增枚举/常量：编排类型 `K8S_DEPLOYMENT`、版本状态 `DRAFT/PUBLISHED/ARCHIVED`、组件模板类型 `SECRET/CONFIG_MAP/PVC/SERVICE/DEPLOYMENT/INGRESS/BLANK`。
- 新增 SQL：`dev_application_orchestration`，归属为 `app_id + orchestration_type + version_no`，字段包含 `env_labels_json`、`component_json`、`canvas_json`、`template_yaml`、`values_json`、`env_values_json`。
- 新增 DO/Mapper/Convert/VO。
- 新增错误码：编排不存在、无已发布版本、发布校验失败、变量缺失、模板非法、组件依赖成环、组件类型不支持等。

## Phase 2: Backend Service And APIs

- 实现 `ApplicationOrchestrationService`：
  - `get`
  - `componentTemplates`
  - `validate`
  - `render`
  - `saveDraft`
  - `publish`
  - `versions`
  - `getVersion`
  - `rollback`
- 实现 `ApplicationOrchestrationComponentTemplateService`：
  - 返回 `Secret`、`ConfigMap`、`PVC`、`Service`、`Deployment`、`Ingress`、`Blank` 组件模板元数据和默认 YAML。
  - 为每种模板提供默认 `applyOrder`，例如 Secret/ConfigMap/PVC 10、Service 40、Deployment 50、Ingress 60。
- 实现 `ApplicationOrchestrationRenderService`：
  - 提取占位符。
  - 校验组件 id、kind、dependsOn 和依赖环。
  - 按 `dependsOn + applyOrder + 声明顺序` 生成稳定组件顺序。
  - 按环境阶段标签合并内置变量、镜像、变量组和运行时覆盖值。
  - 渲染每个组件 YAML 并聚合为多文档 YAML。
  - 调用 `KubernetesDeploymentManifestSupport` 校验资源。
- 新增 `ApplicationOrchestrationController`，权限前缀 `devops:application-orchestration:*`。
- 更新 `sql/mysql/devops-menu.sql` 增加应用编排菜单/按钮权限。

## Phase 3: Frontend

- 在应用详情/配置区域新增“应用编排”入口。
- 实现 Kubernetes 部署查看态、编辑态、版本记录弹窗。
- 实现“添加组件”弹窗和组件模板卡片：Secret、ConfigMap、PVC、Service、Deployment、Ingress、空模板。
- 画布状态保存到 `canvas_json`，组件顺序、依赖、YAML 片段保存到 `component_json`。
- 编辑态支持环境阶段标签选择和变量组切换；每个标签维护一组变量值。
- 对接 `get/validate/save-draft/publish/versions/getVersion/rollback` API。
- 主机部署页签置灰，避免用户误以为已支持。

## Phase 4: Tests

- Service 单测：
  - 首次保存草稿、覆盖草稿、发布 v1/v2、最大发布版本读取。
  - 发布版本携带环境标签和变量组。
  - 保存和发布版本携带组件顺序、依赖和画布布局。
  - 发布无效 YAML 失败。
  - 组件依赖成环时发布失败。
  - 绑定环境阶段标签缺少变量组时发布失败。
  - 历史版本不可变。
  - rollback 创建新发布版本。
- Renderer 单测：
  - `${VALUES.x}`、`{{ .Values.x }}`、`${IMAGE}`、`{{ .Images.backend }}` 渲染。
  - 缺失变量报错。
  - Secret + ConfigMap + PVC + Service + Deployment + Ingress 多文档解析。
  - 组件拓扑排序和 applyOrder 稳定排序。
- 回归测试：
  - 现有 `RAW_MANIFEST` K8sDeploy 不受影响。

建议后端验证命令：

```bash
mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dtest='*ApplicationOrchestration*Test,*KubernetesDeploymentManifestSupport*Test' -Dsurefire.failIfNoSpecifiedTests=false test
```

## Deferred: Deployment Integration

- 不在本阶段修改 `PipelineNodeRegistryServiceImpl`、`PipelineSpecValidationServiceImpl`、`K8sDeployStepHandler`、`DeploymentOrderServiceImpl` 或 `ContainerDeployConfigContext`。
- 后续接入时再新增 deployMode、解析当前环境 `envStage`、读取已发布编排、写入部署单快照。

## Risk Points

- 环境标签来源已确定为 `EnvironmentDO.envStage`；第一阶段不要引入自定义多标签能力，避免扩大标签管理和部署匹配范围。
- 模板语法必须保持受控替换，不能把用户 YAML 当通用脚本执行。
- 组件语义必须和画布布局分离，避免拖动画布节点导致后端部署语义变化。
- 当前阶段不改部署链路；不要为了“预留”提前改 handler 或部署单表。
