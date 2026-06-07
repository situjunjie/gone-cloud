# Prompt for frontend implementation

你是前端工程师，请基于 Vue3 管理后台实现 DevOps 流水线可视化编排第一期。

## 背景

我们平台的 DevOps 模块要实现“应用 + 环境”维度的流水线配置。第一期只做可视化编排、DSL 保存、后端校验、发布版本和 Jenkinsfile 预览。

Jenkins 在整体方案中定位为构建/测试/产物生成执行器，但第一期前端不需要触发 Jenkins 执行。

## 技术选型

使用 Vue Flow 做画布：

* 左侧节点面板
* 中间拖拽画布
* 右侧节点属性面板
* 底部抽屉显示校验结果和 Jenkinsfile 预览

UI 需要适配当前管理后台风格，避免做营销页。这个页面是配置工具，信息密度要适中，操作要明确。

## 页面入口

从 DevOps 应用详情页的某个环境行进入：

* 路由建议：`/devops/pipeline/designer?applicationEnvId=xxx`
* 页面标题显示：应用名 / 环境名 / 流水线配置

## Phase 1 范围

要做：

* 加载节点类型 `/devops/pipeline/node-types`
* 加载命令模板 `/devops/pipeline/command-templates`
* 加载应用环境当前流水线 `/devops/pipeline/get-by-application-env`
* Vue Flow 画布拖拽节点、连线、删除节点/边
* 右侧属性面板编辑节点名称、超时、重试、命令模板等参数
* 保存草稿 `/devops/pipeline/save-draft`
* 校验 `/devops/pipeline/validate`
* 发布 `/devops/pipeline/publish`
* Jenkinsfile 预览 `/devops/pipeline/version/jenkinsfile`
* 版本历史 `/devops/pipeline/version/list`

不做：

* 触发 Jenkins 构建
* 部署
* 审批
* 发布分支合并和冲突解决

## 节点

启用节点：

* Checkout
* Unit Test
* Build Artifact
* Build Image
* Report Artifacts

禁用但展示节点：

* Approval：展示 tooltip “后续阶段开放：平台审批”
* Deploy K8S：展示 tooltip “后续阶段开放：平台部署”

禁用节点不能拖到画布里，也不能发布。

## 命令模板

构建/测试命令必须从后端模板选择，不能提供任意命令输入框。

节点参数示例：

```json
{
  "commandTemplateKey": "maven_test",
  "reportPattern": "**/surefire-reports/*.xml"
}
```

## DSL

前端保存两份 JSON：

* `diagramJson`：Vue Flow 画布状态，包括 nodes、edges、position、viewport。
* `specJson`：后端可执行 DSL，只包含节点、边、参数、超时、重试、失败策略。

后端以 `specJson` 为准。前端可以做轻量校验，但后端校验才是权威。

## 交互要求

* 页面进入后，如果已有 draft，优先加载 draft；否则加载 published；否则创建空画布。
* 保存草稿不影响已发布版本。
* 发布前必须调用后端校验。
* 校验失败要在底部面板展示错误列表，并尽量高亮对应节点。
* Jenkinsfile 预览只读展示。
* 页面离开前如果有未保存变更，需要提示。
* 画布中 stage 节点显示 node name、node type、状态标识；Phase 1 状态可以只有配置态。

## 推荐布局

```text
Header:
  应用 / 环境 / 当前状态 / 保存草稿 / 校验 / 预览 Jenkinsfile / 发布

Left sidebar:
  节点分类和节点列表

Canvas:
  Vue Flow
  MiniMap
  Controls
  Background

Right panel:
  当前选中节点属性

Bottom drawer:
  校验结果
  Jenkinsfile 预览
  版本历史
```

## 接口契约

详见同目录 `frontend-api-contract.md`。请严格按该接口字段设计 API client 和类型。
