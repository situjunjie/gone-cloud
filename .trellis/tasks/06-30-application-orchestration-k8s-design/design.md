# 应用编排 K8s 部署配置技术方案

## 1. Problem Statement

DevOps 需要把“部署编排配置”从流水线 YAML 中抽离出来，成为应用可维护、可发布、可追溯的配置资产。第一阶段只建设配置能力：编辑组件、保存草稿、发布版本、按环境阶段变量组渲染 YAML。流水线部署接入后续单独实施。

## 2. Recommended Architecture

方案采用“应用全局编排 + 环境标签/变量组”：

- 归属 key：`tenant_id + app_id + orchestration_type`。
- 编排版本声明 `env_labels`，表示该版本适用的环境标签集合；标签值来自 `dev_environment.env_stage`，例如 DEV/TEST/PRE/PROD。
- 编排由组件组成。组件是后端语义，包含模板类型、Kubernetes kind、YAML 片段、apply 顺序和依赖；画布是前端展示语义，包含节点位置、连线和视口状态。
- 变量值按环境标签分组，例如 `DEV`、`TEST`、`PROD` 各有一组 `Values`。
- 版本模型：单表多版本。草稿 `version_status=DRAFT, version_no=0` 可反复覆盖；发布时复制草稿为 `PUBLISHED, version_no=max(published)+1`；当前有效版本为最大已发布 `version_no`。
- 配置渲染：用户选择环境阶段后，后端确认版本绑定该环境阶段，加载对应变量组，按组件顺序渲染每个组件 YAML，并聚合为多文档 YAML 供预览/复制。

本阶段不改流水线 StepHandler、不扩展部署单、不新增 deployMode。这样同一个应用的 Kubernetes YAML 能跨开发、测试、生产等环境复用，只通过变量组表达差异；后续接入部署时直接复用本阶段发布版本和渲染逻辑。

## 3. Data Model

新增表：`dev_application_orchestration`

关键字段：

```sql
CREATE TABLE `dev_application_orchestration` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '应用编排版本编号',
  `app_id` bigint NOT NULL COMMENT '应用编号',
  `orchestration_type` varchar(64) NOT NULL COMMENT '编排类型：K8S_DEPLOYMENT',
  `version_no` int NOT NULL COMMENT '版本号，草稿为0，发布版本从1递增',
  `version_name` varchar(64) DEFAULT NULL COMMENT '版本名称',
  `version_status` tinyint NOT NULL COMMENT '版本状态：0草稿 1已发布 2已归档',
  `schema_version` varchar(32) NOT NULL DEFAULT '1.0' COMMENT '配置 schema 版本',
  `env_labels_json` text COMMENT '版本绑定环境阶段标签 JSON，例如 ["DEV","TEST"]',
  `component_json` mediumtext NOT NULL COMMENT '组件语义 JSON，包含模板、YAML 片段、顺序和依赖',
  `canvas_json` mediumtext COMMENT '画布布局 JSON，包含节点坐标、连线和视口',
  `template_yaml` mediumtext NOT NULL COMMENT '聚合后的 Kubernetes YAML 模板快照',
  `values_json` mediumtext COMMENT '占位符与部署变量配置 JSON',
  `env_values_json` mediumtext COMMENT '按环境标签分组的变量值 JSON',
  `validation_result_json` mediumtext COMMENT '校验结果 JSON',
  `rollback_from_version_id` bigint DEFAULT NULL COMMENT '回退来源版本编号',
  `rollback_from_version_no` int DEFAULT NULL COMMENT '回退来源版本号',
  `based_on_current_version_id` bigint DEFAULT NULL COMMENT '回退发生时当前版本编号',
  `based_on_current_version_no` int DEFAULT NULL COMMENT '回退发生时当前版本号',
  `publish_remark` varchar(512) DEFAULT NULL COMMENT '发布说明',
  `published_at` datetime DEFAULT NULL COMMENT '发布时间',
  `published_by` bigint DEFAULT NULL COMMENT '发布人',
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_app_type_version` (`tenant_id`, `app_id`, `orchestration_type`, `version_no`),
  KEY `idx_tenant_app_type_status` (`tenant_id`, `app_id`, `orchestration_type`, `version_status`)
) COMMENT='DevOps 应用编排版本表';
```

本阶段不扩展 `dev_deployment_order`。部署单快照字段、编排版本关联字段留到后续部署接入阶段。

## 4. Config JSON Shape

`component_json` 保存组件语义，`canvas_json` 保存画布布局，`values_json` 保存变量定义，`env_values_json` 保存按环境阶段标签分组的变量值。

`component_json` 建议结构：

```json
{
  "schemaVersion": "1.0",
  "rolloutTargetComponentId": "deploy-backend",
  "components": [
    {
      "id": "secret-backend",
      "name": "backend-secret",
      "description": "后端密钥",
      "kind": "Secret",
      "templateKey": "SECRET",
      "applyOrder": 10,
      "dependsOn": [],
      "enabled": true,
      "yaml": "apiVersion: v1\nkind: Secret\nmetadata:\n  name: {{ .Values.secretName }}\n..."
    },
    {
      "id": "service-backend",
      "name": "backend-service",
      "description": "服务访问策略",
      "kind": "Service",
      "templateKey": "SERVICE",
      "applyOrder": 40,
      "dependsOn": [],
      "enabled": true,
      "yaml": "apiVersion: v1\nkind: Service\nmetadata:\n  name: {{ .Values.serviceName }}\n..."
    },
    {
      "id": "deploy-backend",
      "name": "backend-deployment",
      "description": "后端工作负载",
      "kind": "Deployment",
      "templateKey": "DEPLOYMENT",
      "applyOrder": 50,
      "dependsOn": ["secret-backend"],
      "enabled": true,
      "targetContainerName": "backend",
      "yaml": "apiVersion: apps/v1\nkind: Deployment\nmetadata:\n  name: {{ .Values.deploymentName }}\n..."
    }
  ]
}
```

`canvas_json` 建议结构：

```json
{
  "schemaVersion": "1.0",
  "nodes": [
    {
      "id": "service-backend",
      "componentId": "service-backend",
      "position": {"x": 24, "y": 32},
      "size": {"width": 272, "height": 96},
      "collapsed": false
    },
    {
      "id": "deploy-backend",
      "componentId": "deploy-backend",
      "position": {"x": 328, "y": 32},
      "size": {"width": 272, "height": 96},
      "collapsed": false
    }
  ],
  "edges": [
    {
      "id": "edge-service-deploy",
      "source": "service-backend",
      "target": "deploy-backend",
      "type": "ORDER"
    }
  ],
  "viewport": {"x": 0, "y": 0, "zoom": 1}
}
```

`template_yaml` 是后端根据 `component_json.components` 的拓扑顺序和 `applyOrder` 聚合出的多文档 YAML 快照。保存时由后端生成并覆盖，避免前端提交的聚合 YAML 和组件 YAML 不一致。

`values_json` 建议结构：

```json
{
  "images": [
    {
      "name": "backend",
      "expression": "${IMAGE}",
      "description": "后端服务镜像",
      "required": true
    }
  ],
  "variables": [
    {
      "name": "namespace",
      "type": "STRING",
      "source": "ENV_LABEL",
      "defaultValue": "default",
      "description": "命名空间",
      "deployEditable": true
    },
    {
      "name": "cpuLimit",
      "type": "STRING",
      "source": "CONSTANT",
      "defaultValue": "1",
      "description": "CPU限制"
    }
  ]
}
```

`env_values_json` 建议结构：

```json
{
  "DEV": {
    "namespace": "dev",
    "cpuLimit": "0.5",
    "memoryLimit": "512Mi"
  },
  "TEST": {
    "namespace": "test",
    "cpuLimit": "1",
    "memoryLimit": "1024Mi"
  },
  "PROD": {
    "namespace": "prod",
    "cpuLimit": "2",
    "memoryLimit": "2048Mi"
  }
}
```

`env_labels_json` 建议结构，取值来自 `dev_environment.env_stage`：

```json
["DEV", "TEST", "PROD"]
```

模板占位符规则：

- 平台内置：`APP_KEY`、`APP_NAME`、`ENV_KEY`、`ENV_NAME`、`NAMESPACE`、`BRANCH_NAME`、`COMMIT_SHA`、`PIPELINE_RUN_ID`。
- 镜像：`${IMAGE}` 和 `{{ .Images.backend }}` 均可映射到 `images.backend`。
- 变量：`${VALUES.namespace}` 和 `{{ .Values.namespace }}` 映射到变量表。
- 不执行任意模板逻辑，不支持循环、条件和函数。这样能满足截图交互，同时避免引入 Helm 运行时复杂度。

## 5. Backend APIs

新增控制器建议：`ApplicationOrchestrationController`，路径 `/devops/application-orchestration`。

- `GET /component-templates`
  - 权限：`devops:application-orchestration:query`
  - 返回 `Secret`、`ConfigMap`、`PVC`、`Service`、`Deployment`、`Ingress`、`Blank` 模板的名称、说明、默认 YAML、默认 `applyOrder`、是否可作为 rollout target。

- `GET /get?appId=&orchestrationType=K8S_DEPLOYMENT`
  - 权限：`devops:application-orchestration:query`
  - 返回草稿、当前发布版本、应用摘要、版本环境阶段标签和变量组。
- `POST /validate`
  - 权限：`devops:application-orchestration:query`
  - 入参：`appId`、`orchestrationType`、`components`、`canvasJson`、`values`、`envLabels`、`envValues`。
  - 返回校验错误列表、提取到的资源/占位符摘要、组件拓扑顺序、聚合 YAML、按环境阶段标签的变量完整性结果。
- `POST /render`
  - 权限：`devops:application-orchestration:query`
  - 入参：草稿内容或版本 id + `envStage`。
  - 返回按指定环境阶段变量组渲染后的多文档 YAML 和组件渲染摘要。
- `POST /save-draft`
  - 权限：`devops:application-orchestration:update`
  - 创建或覆盖草稿版本。
- `POST /publish`
  - 权限：`devops:application-orchestration:publish`
  - 校验草稿，复制为新发布版本，`version_no=max+1`。
- `GET /versions?appId=&orchestrationType=`
  - 权限：`query`
  - 返回历史发布版本，按版本号倒序。
- `GET /version/get?id=`
  - 权限：`query`
  - 查看某个版本内容。
- `POST /rollback`
  - 权限：`publish`
  - 推荐保留接口：复制目标发布版本为新的发布版本，不修改历史版本。

## 6. Services And Boundaries

新增服务：

- `ApplicationOrchestrationService`
  - 编排 CRUD、草稿、发布、版本列表、回滚。
  - 校验 `appId`、应用状态、环境阶段标签绑定、组件结构、组件顺序和变量组完整性。
- `ApplicationOrchestrationComponentTemplateService`
  - 提供组件模板库。模板包括 `Secret`、`ConfigMap`、`PVC`、`Service`、`Deployment`、`Ingress`、`Blank`。
  - `Blank` 可提交 Fabric8 可解析的 Kubernetes 内置资源 YAML；CRD 和自定义资源暂不承诺支持。
- `ApplicationOrchestrationRenderService`
  - 解析组件 YAML 占位符、按环境阶段标签合并内置变量/values/images、渲染每个组件 YAML。
  - 按 `dependsOn` 拓扑排序，再按 `applyOrder` 和原始声明顺序稳定排序，聚合为多文档 YAML。
  - 复用或扩展当前 `KubernetesDeploymentManifestSupport`。

现有部署边界保留：

- Kubernetes 客户端仍由现有部署服务在后续阶段执行 apply 和 rollout。
- 本阶段编排服务只负责配置保存、校验和渲染，不直接操作集群。

## 7. Pipeline Integration

本阶段不做流水线集成，保留现有 RAW_MANIFEST：

```yaml
step: K8sDeploy
with:
  deployMode: RAW_MANIFEST
  manifestYaml: ...
```

后续接入部署时再新增 deployMode、扩展 StepHandler、扩展部署单快照字段。本期只保证已发布编排可按环境阶段渲染出完整 YAML，人工复制到现有 RAW_MANIFEST 或供后续阶段复用。

## 8. Validation

保存草稿时允许保存无效内容，但返回校验结果；发布必须校验通过。

校验项：

- 保存/发布时 `appId` 存在且状态可用。
- `envLabels` 非空且每个标签必须是合法的 `env_stage` 值。
- 每个绑定环境阶段标签都必须存在变量组。
- 组件 id 在同一版本内唯一，名称符合 1 到 63 位小写字母、数字、中划线，字母开头，字母或数字结尾。
- 组件依赖 `dependsOn` 必须引用存在组件，不允许自依赖，不允许成环。
- 组件顺序由 `dependsOn`、`applyOrder` 和声明顺序共同决定；后端保存、校验和渲染都使用同一排序器。
- 资源类型支持 `Secret`、`ConfigMap`、`PersistentVolumeClaim`、`Service`、`Deployment`、`Ingress`；空模板允许 Fabric8 可解析的 Kubernetes 内置资源，CRD/custom resource 暂不支持。
- 至少一个 Deployment；第一阶段需要指定或自动推断一个 Deployment 作为未来 rollout target。
- 所有资源 `metadata.name` 非空。
- Deployment 中存在目标容器名；如果容器名未显式填写，可从唯一容器自动推断，多个容器必须显式选择。
- YAML 中引用的 `Values`、`Images` 都在 `values_json` 中定义；必填变量必须在每个绑定环境阶段标签的变量组、默认值或运行时来源中可解析。
- `namespace` 不允许由 YAML 硬编码覆盖环境配置，最终以环境配置或变量渲染结果为准。
- 渲染预览时目标 `envStage` 必须被当前版本绑定，且存在对应变量组。

## 9. Frontend Design Contract

应用详情/编排页：

- 顶部页签：`Kubernetes 部署` 可用，`主机部署` 禁用并展示“暂未开放”。
- 顶栏显示最近发布时间、当前版本号、环境可用状态、版本记录、查看、编辑、更多操作。
- 当前版本区域展示绑定的环境阶段标签；没有绑定环境标签时展示截图中的不可用提示。
- “添加组件”弹窗提供 `Secret`、`ConfigMap`、`PVC`、`Service`、`Deployment`、`Ingress`、`空模板`。选择模板后创建组件，默认 YAML 和默认 applyOrder 由后端模板元数据提供。
- 查看态展示资源顺序卡片和依赖线，卡片显示资源名、kind 和模板类型。
- 编辑态左侧为资源编排画布 + 当前组件 YAML 编辑器；右侧为镜像和占位符面板；底部展示按环境阶段标签切换的变量组。
- 用户调整画布位置只更新 `canvas_json`；用户调整组件顺序或依赖更新 `component_json`，后端校验通过后重新生成 `template_yaml`。
- “提取占位符”从 YAML 中扫描 `{{ .Values.* }}`、`${VALUES.*}`、`{{ .Images.* }}`、`${IMAGE}`，自动补充变量表，不删除用户已编辑的描述和默认值。
- “校验”调用后端 validate；“保存”只保存草稿；“发布”创建新发布版本。

## 10. Compatibility And Migration

- 不迁移现有流水线 `RAW_MANIFEST` 配置。
- 新增编排能力后，用户可以手动把现有 `manifestYaml` 拆分成组件，或从组件模板新增资源，为 DEV/TEST/PROD 等环境阶段标签补齐变量组。
- 部署单历史不受影响；本阶段不修改部署单。

## 11. Trade-Offs

- 单表版本模型简单，符合本需求；代价是定义元信息、环境标签和变量组会在每个版本重复，查询当前版本需要 `max(version_no)` 或按版本号倒序取第一条。
- 应用全局模型能复用 YAML；代价是需要在部署时引入环境阶段标签匹配和变量组完整性校验。
- 组件语义和画布布局分离增加了一个 JSON 字段，但能避免前端布局变更影响配置语义，也能让后端稳定复用同一排序和渲染逻辑。
- 不引入 Helm 能减少安全和调试复杂度；代价是不支持条件、循环、include 等高级模板能力。

## 12. Environment Label Decision

环境标签来源确定为现有 `dev_environment.env_stage`，标签值来自 `dev_env_stage` 字典，例如 DEV/TEST/PRE/PROD。第一阶段不新增环境标签表或多标签绑定 UI。一个环境只有一个编排标签，渲染预览和后续部署接入的匹配规则也只有一条：当前发布版本必须绑定该环境的 `env_stage`，并提供该标签对应变量组。
