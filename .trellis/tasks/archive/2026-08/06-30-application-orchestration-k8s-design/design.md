# 应用编排 K8s 部署配置技术方案

## 1. Architecture

采用“应用级单草稿 + 组件语义与画布布局分离”的模型：

- 业务归属：`tenant_id + app_id + orchestration_type`。
- `component_json` 保存有序组件数组，数组顺序即资源顺序。
- `canvas_json` 保存节点坐标、连线和视口，只服务前端还原。
- 保存前后端统一校验，校验失败不落库。
- 本期只读写配置，不调用 Kubernetes API，不修改流水线部署链路。

## 2. Data Model

新增表 `dev_application_orchestration`：

```sql
CREATE TABLE `dev_application_orchestration` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '应用编排编号',
  `app_id` bigint NOT NULL COMMENT '应用编号',
  `orchestration_type` varchar(64) NOT NULL COMMENT '编排类型：K8S_DEPLOYMENT',
  `schema_version` varchar(32) NOT NULL DEFAULT '1.0' COMMENT '配置 Schema 版本',
  `component_json` mediumtext NOT NULL COMMENT '组件语义 JSON',
  `canvas_json` mediumtext COMMENT '画布布局 JSON',
  `validation_result_json` mediumtext COMMENT '最近保存的校验摘要 JSON',
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_app_type` (`tenant_id`, `app_id`, `orchestration_type`)
) COMMENT='DevOps 应用编排表';
```

不提前加入版本、环境、变量或部署快照字段；后续版本功能通过独立版本表演进，避免把当前草稿表同时承担历史数据职责。

## 3. Component Contract

```json
{
  "schemaVersion": "1.0",
  "components": [
    {
      "id": "demo-service",
      "name": "demo-service",
      "templateKey": "SERVICE",
      "kind": "Service",
      "applyOrder": 1,
      "dependsOn": [],
      "enabled": true,
      "yaml": "apiVersion: v1\nkind: Service\nmetadata:\n  name: demo-service\n"
    },
    {
      "id": "demo-deployment",
      "name": "demo-deployment",
      "templateKey": "DEPLOYMENT",
      "kind": "Deployment",
      "applyOrder": 2,
      "dependsOn": ["demo-service"],
      "enabled": true,
      "yaml": "apiVersion: apps/v1\nkind: Deployment\nmetadata:\n  name: demo-deployment\n"
    }
  ]
}
```

约束：

- `id` 是保存后稳定的前端组件标识，复制组件时必须生成新 id。
- `applyOrder` 按数组下标归一化为从 1 开始的连续整数。
- 一个组件 YAML 只允许一个 Kubernetes 文档和一个资源；多文档 YAML 需拆成多个组件。
- 模板组件的 `kind` 必须与 YAML 的 `kind` 一致；空模板在白名单内按 YAML `kind` 识别。
- `dependsOn` 只能引用当前列表中更早的组件，避免画布依赖与明确的用户顺序冲突。

`canvas_json` 示例：

```json
{
  "schemaVersion": "1.0",
  "nodes": [
    {"id": "demo-service", "position": {"x": 24, "y": 32}},
    {"id": "demo-deployment", "position": {"x": 328, "y": 32}}
  ],
  "viewport": {"x": 0, "y": 0, "zoom": 1}
}
```

连线由 `dependsOn` 派生，前端可缓存样式信息，但后端不接受与 `dependsOn` 冲突的独立语义连线。

## 4. APIs

控制器：`ApplicationOrchestrationController`，路径 `/devops/application-orchestration`。

- `GET /component-templates`
  - 权限：`devops:application-orchestration:query`
  - 返回 Secret、ConfigMap、PVC、Service、Deployment、Ingress、Blank 的模板元数据和默认 YAML。
- `GET /get?appId=&orchestrationType=K8S_DEPLOYMENT`
  - 权限：`devops:application-orchestration:query`
  - 不存在时返回应用摘要、空组件列表和默认画布，不以“编排不存在”作为业务错误。
- `POST /validate`
  - 权限：`devops:application-orchestration:query`
  - 校验请求内容，返回归一化顺序、资源摘要和组件级错误，不写数据库。
- `POST /save-draft`
  - 权限：`devops:application-orchestration:update`
  - 在事务内校验并按唯一业务键插入或更新；返回编排 id、更新时间和归一化后的配置。

建议写接口携带 `updateTime` 做乐观并发校验，防止两个页面后保存者静默覆盖先保存者；首次创建时为空。

## 5. Services

- `ApplicationOrchestrationService`：校验应用、查询和原子保存草稿。
- `ApplicationOrchestrationComponentTemplateService`：提供七类模板及默认 YAML。
- `ApplicationOrchestrationValidationService`：归一化顺序、校验组件和解析 YAML。

YAML 校验复用 Fabric8 parser。现有 `KubernetesDeploymentManifestSupport` 只面向部署执行且资源范围较窄，建议提取通用解析能力或新增编排校验器，不让配置模块依赖 rollout 语义。

## 6. Validation

- 应用存在且可访问，编排类型只能为 `K8S_DEPLOYMENT`。
- 组件 id 和名称非空且唯一；名称满足 Kubernetes DNS label 约束。
- `dependsOn` 引用存在、不能自引用、不能成环，且依赖目标必须排在当前组件之前。
- 每个 YAML 只能解析为一个资源。
- 支持类型：Secret、ConfigMap、PersistentVolumeClaim、Service、Deployment、Ingress。
- `apiVersion`、`kind`、`metadata.name` 必填；非 Blank 模板的 kind 与模板一致。
- 不解析模板占位符。若占位符使 YAML 不能被 Kubernetes parser 解析，本期即视为无效 YAML。

校验错误结构应至少包含 `componentId`、`field`、`code`、`message`，便于前端定位卡片和编辑器行域。

## 7. Frontend Contract

- 顶部为有序资源卡片画布，卡片显示名称和 kind，支持拖拽、复制、删除和新增顺序。
- 选中卡片后，下方 YAML 编辑器展示该组件原文；编辑器区域尺寸固定，切换卡片不引起布局跳动。
- 新增组件弹窗提供七类模板；默认 YAML 来自后端模板接口。
- 保存前调用 validate，也允许直接调用 save-draft，由保存接口执行同一套最终校验。
- 主机部署页签禁用并显示“暂未开放”。
- 本仓库没有业务前端源码，本期后端实现完成后由前端仓库按此契约接入。

## 8. Compatibility And Rollback

- 不迁移、不读取现有流水线 RAW_MANIFEST。
- 不修改 `PipelineNodeRegistryServiceImpl`、`PipelineSpecValidationServiceImpl`、`K8sDeployStepHandler`、`DeploymentOrderServiceImpl` 或部署单表。
- 数据库回滚只需删除新增菜单权限和新增表；现有部署数据不受影响。

## 9. Deferred Evolution

后续阶段再设计环境变量组、占位符渲染、发布版本、历史版本、回滚和流水线引用。版本发布建议新增不可变版本表并从当前草稿生成快照，而不是改变本期单草稿 API 的语义。
