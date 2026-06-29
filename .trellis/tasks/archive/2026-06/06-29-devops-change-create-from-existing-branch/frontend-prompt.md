# Frontend Prompt: Adapt create-from-application to existing branch mode

请修改 DevOps 应用详情页的新建变更弹窗，让 `POST /admin-api/devops/change/create-from-application` 支持两种创建模式：

1. 新建分支模式：沿用现有逻辑，用户输入分支中间名 `branchSlug`，后端生成 `feat/{branchSlug}-{openTimestamp}` 并创建远端分支。
2. 已有分支模式：用户输入一个完整已有分支名 `branchName`，后端只登记这个分支为变更，不创建远端分支，也不校验远端分支是否存在。

## Backend Contract

接口不变：

```http
POST /admin-api/devops/change/create-from-application
```

请求体现在支持：

```ts
type CreateChangeFromApplicationReq = {
  appId: number
  title: string
  openTimestamp: number
  branchSlug?: string
  branchName?: string
}
```

字段规则：

- `appId` 必填。
- `title` 必填，最长 200。
- `openTimestamp` 必填，正整数。建议继续在打开弹窗时生成一次，提交时复用同一个值。
- `branchName` 和 `branchSlug` 二选一。
- 填 `branchName` 时是已有分支模式；不要再提交 `branchSlug`。
- 不填 `branchName`、填 `branchSlug` 时是新建分支模式；保持现有行为。
- 后端不会在已有分支模式校验远端分支是否存在；前端也不要为了这个模式新增远端查询。

示例：新建分支模式

```json
{
  "appId": 1,
  "title": "登录页优化",
  "branchSlug": "login-page",
  "openTimestamp": 1717651234567
}
```

示例：已有分支模式

```json
{
  "appId": 1,
  "title": "登录页优化",
  "branchName": "feature/login-page",
  "openTimestamp": 1717651234567
}
```

## UI Requirements

- 在新建变更弹窗中增加一个模式选择控件，例如 Radio/Segmented：
  - `新建分支`
  - `已有分支`
- 默认保持 `新建分支`，避免改变现有用户习惯。
- `新建分支` 模式：
  - 展示现有分支中间名输入框。
  - 继续展示/预览后端将生成的分支名：`feat/{branchSlug}-{openTimestamp}`。
  - 提交 body 包含 `appId/title/branchSlug/openTimestamp`，不要包含 `branchName`。
- `已有分支` 模式：
  - 展示完整分支名输入框，字段名映射为 `branchName`。
  - 占位示例：`feature/login-page`、`hotfix/order-timeout`。
  - 不展示“将创建分支”的文案；应该表达为“登记已有分支”。
  - 提交 body 包含 `appId/title/branchName/openTimestamp`，不要包含 `branchSlug`。
- 切换模式时清理另一个模式的分支输入值，避免同时提交 `branchName` 和 `branchSlug`。
- 成功后沿用现有刷新列表/关闭弹窗/提示逻辑。

## Frontend Validation

- 两种模式都校验 `title` 非空。
- `新建分支` 模式校验 `branchSlug` 非空，保持现有长度/字符规则即可。
- `已有分支` 模式校验 `branchName` 非空，最长 128。
- 已有分支名不需要强制 `feat/` 前缀。
- 已有分支名建议禁止明显非法字符：空格、中文、控制字符、`~ ^ : ? * [ \`，以及 `..`、`//`、`@{`、开头/结尾 `/`、结尾 `.`。最终以后端错误为准。

## Acceptance Checks

- 默认打开弹窗仍是新建分支模式，原有创建分支流程可用。
- 选择已有分支后，输入 `feature/login-page` 可以提交，请求体包含 `branchName` 且不包含 `branchSlug`。
- 已有分支模式不会调用任何“查询远端分支是否存在”的接口。
- 后端返回分支名重复或分支名非法错误时，沿用现有错误提示机制展示给用户。
