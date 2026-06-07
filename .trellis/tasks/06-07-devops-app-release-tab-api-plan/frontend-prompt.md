# 前端开发 Prompt：应用详情页新增“发布”Tab

请在 DevOps 应用详情页新增一个顶层 tab：“发布”。

## 页面结构

- 顶层位置：应用详情页现有 tabs 中新增“发布”。
- “发布”tab 内部再渲染环境子 tab。
- 环境子 tab 来源于后端返回的应用关联环境；关联几个环境就展示几个子 tab。
- 环境 tab label 优先展示 `envName`，可辅助展示 `envStage` 或 `envKey`。
- 环境 tab 排序使用后端返回顺序。

## 接口

### 1. 获取环境子 tab

`GET /devops/application/release/env-tabs?appId={appId}`

权限：`devops:application:query`

响应 `data` 为数组：

```json
[
  {
    "applicationEnvId": 1001,
    "appId": 1,
    "envId": 2,
    "envKey": "test",
    "envName": "测试环境",
    "envStage": "TEST",
    "infraType": "K8S",
    "displayOrder": 10,
    "deployBranchNamePattern": "feat/*",
    "pipelineDefinitionId": 2001,
    "hasPublishedPipeline": true,
    "status": 0
  }
]
```

### 2. 获取某个环境发布详情

`GET /devops/application/release/env-detail?applicationEnvId={applicationEnvId}`

权限：`devops:application:query`

响应结构：

```json
{
  "env": {
    "applicationEnvId": 1001,
    "appId": 1,
    "envId": 2,
    "envKey": "test",
    "envName": "测试环境",
    "envStage": "TEST",
    "infraType": "K8S",
    "displayOrder": 10,
    "deployBranchNamePattern": "feat/*",
    "pipelineDefinitionId": 2001,
    "hasPublishedPipeline": true,
    "status": 0
  },
  "pipeline": {
    "definitionId": 2001,
    "definitionName": "测试环境流水线",
    "definitionKey": "app-env-1001",
    "publishedVersionId": 3001,
    "versionNo": 1,
    "versionName": "v1",
    "publishedAt": "2026-06-07T10:00:00",
    "publishedBy": 1,
    "emptyReason": null,
    "nodes": [
      {
        "nodeId": "checkout",
        "type": "CHECKOUT",
        "name": "拉取代码",
        "enabled": true,
        "displayOrder": 1,
        "params": {},
        "timeoutSeconds": null,
        "retryTimes": null,
        "failStrategy": null
      }
    ],
    "edges": [
      {
        "source": "checkout",
        "target": "unit_test"
      }
    ]
  },
  "mountedBranches": [
    {
      "changeId": 11,
      "changeKey": "gone-cloud-1717651234567",
      "title": "登录页优化",
      "branchName": "feat/login-page-1717651234567",
      "sourceBaseBranchName": "master",
      "ownerUserId": 1,
      "latestCommitSha": "abc123",
      "latestCommitMessage": "fix login page",
      "latestCommitAt": "2026-06-07T10:10:00",
      "createTime": "2026-06-07T09:30:00",
      "changeEnvId": 9001,
      "mountStatus": 0,
      "mountedAt": "2026-06-07T10:20:00",
      "mountedBy": 1,
      "lastPipelineRunId": null,
      "lastMergeStatus": 0,
      "lastBuildStatus": 0,
      "lastTestStatus": 0,
      "lastDeployStatus": 0,
      "lastErrorMessage": null,
      "approvalStatus": 0,
      "includedInCurrentSnapshot": false
    }
  ],
  "unmountedBranches": []
}
```

## 流水线展示

- 使用 `pipeline.nodes` 自左向右展示。
- 不需要按画布坐标还原设计器画图效果。
- 节点顺序使用 `displayOrder`。
- 如果 `pipeline.emptyReason` 为 `NO_PIPELINE_DEFINITION` 或 `NO_PUBLISHED_VERSION`，展示空状态。
- `edges` 可以用于简单连线；如果当前组件只做线性展示，也可以暂不使用。

## 两个分支表格

### 已提交到当前环境的有效分支

数据源：`mountedBranches`

建议列：

- 变更标题 `title`
- 分支名 `branchName`
- 变更标识 `changeKey`
- 最新提交 `latestCommitSha`
- 构建状态 `lastBuildStatus`
- 测试状态 `lastTestStatus`
- 部署状态 `lastDeployStatus`
- 挂载时间 `mountedAt`
- 操作：移出环境

### 不在当前环境的有效分支

数据源：`unmountedBranches`

建议列：

- 变更标题 `title`
- 分支名 `branchName`
- 变更标识 `changeKey`
- 最新提交 `latestCommitSha`
- 创建时间 `createTime`
- 操作：提交到环境

## 操作接口

### 发布 tab 同步环境变更集合并触发流水线

`POST /devops/application/release/submit-branch`

权限：`devops:application:release-submit`

请求：

```json
{
  "applicationEnvId": 1001,
  "changeIds": [11, 12, 13]
}
```

响应：

```json
{
  "applicationEnvId": 1001,
  "mountedChangeIds": [11, 12, 13],
  "unmountedChangeIds": [14, 15],
  "pipelineRunId": 8001,
  "runStatus": 1
}
```

这个接口现在是“同步当前环境最终部署变更集合”的语义，不再是单个变更动作。

前端规则：

- 每次调用都传“当前环境最终要保留在已提交部署列表里的全部变更 id”。
- 新增部署、部分退出部署、全部退出部署都用这一个接口。
- 成功后刷新当前环境详情接口。

例子：

- 当前已提交 `A B C`，新增 `D E`：传 `A B C D E`
- 当前已提交 `A B C`，退出 `B C`：传 `A`
- 当前已提交 `A B C`，全部退出：传 `[]`

后端行为：

- `changeIds` 非空时：
  - 后端把它同步为当前环境最终部署集合
  - 自动创建一条新的 `pipelineRunId`
  - `runStatus` 返回运行中
- `changeIds` 为空数组时：
  - 表示当前环境全部退出部署
  - 后端不会触发新的流水线
  - `pipelineRunId` 和 `runStatus` 返回 `null`

### 普通提交到环境

`POST /devops/change/mount-env`

权限：`devops:change:mount-env`

请求：

```json
{
  "changeId": 11,
  "applicationEnvId": 1001
}
```

普通挂载接口不创建流水线运行记录。发布 tab 上不要再用普通挂载/移出接口维护列表状态，统一使用 `/devops/application/release/submit-branch` 传最终集合。

### 普通移出环境

`PUT /devops/change/unmount-env`

权限：`devops:change:unmount-env`

请求：

```json
{
  "changeId": 11,
  "applicationEnvId": 1001,
  "unmountedReason": "前端输入的移出原因，可选或按后端校验"
}
```

这是普通变更环境关系接口，发布 tab 上不要用它退出部署；发布 tab 退出部署同样调用 `/devops/application/release/submit-branch` 传最终集合，成功后刷新当前环境详情接口。

## 权限控制

- 只要用户有 `devops:application:query`，可以看到发布 tab 和查询内容。
- 发布 tab “提交到环境/发布”按钮受 `devops:application:release-submit` 控制。
- 发布 tab “移出环境”按钮也受 `devops:application:release-submit` 控制，因为它同样走 `/devops/application/release/submit-branch`。
- 流水线配置入口仍按现有 `devops:pipeline:*` 权限控制，不和发布 tab 的只读展示混用。

## 空状态

- 应用未关联环境：发布 tab 显示“暂无关联环境”。
- 当前环境无流水线定义：显示“暂无流水线配置”。
- 当前环境无已发布流水线版本：显示“暂无已发布流水线版本”。
- 两个分支列表分别显示空表格状态。
