# DevOps Change Guidelines

DevOps 变更能力围绕应用创建、发布、废弃和环境挂载。应用详情页里的轻量新建变更入口必须由后端生成业务默认字段，避免前端复制服务端规则。

## Scenario: Application Detail Lightweight Change Creation

### 1. Scope / Trigger

- Trigger: adding or changing the application-detail create-change API.
- Scope: `ChangeController`, change request VOs, `ChangeService`, `ChangeServiceImpl`, `ErrorCodeConstants`, and service tests.

### 2. Signatures

- API: `POST /devops/change/create-from-application`
- Request:
  - `appId: Long` required
  - `title: String` required, max 200
  - `branchSlug: String` required, max 100
  - `openTimestamp: Long` required, positive
- Response: `CommonResult<Long>` containing the created change id.

### 3. Contracts

- Backend generates `branchName` as `feat/{branchSlug}-{openTimestamp}`.
- Backend generates `changeKey` from the application key and `openTimestamp`, truncated to the existing 64-character limit.
- Backend sets `sourceBaseBranchName` from `ApplicationDO.defaultBranchName`.
- Backend sets `ownerUserId` from the current login user id.
- The API only creates the change record; it does not create a remote Git branch.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| `appId` does not exist | Throw `APPLICATION_NOT_EXISTS` |
| generated branch name has Chinese, spaces, control chars, or Git-ref special chars | Throw `CHANGE_BRANCH_NAME_INVALID` |
| generated branch name duplicates another change in the same app | Throw `CHANGE_BRANCH_NAME_DUPLICATE` |
| generated change key duplicates another change in the same app | Throw `CHANGE_KEY_DUPLICATE` |

### 5. Good / Base / Bad Cases

- Good: frontend sends only `appId`, `title`, `branchSlug`, and the modal-open timestamp; backend fills all derived fields.
- Base: existing full create API `/devops/change/create` remains available for callers that already own all fields.
- Bad: frontend submits `sourceBaseBranchName`, `ownerUserId`, or handcrafted `changeKey` for the application-detail flow.

### 6. Tests Required

- Service test that success inserts `branchName`, `changeKey`, `sourceBaseBranchName`, `ownerUserId`, and active status.
- Service test that invalid branch input throws `CHANGE_BRANCH_NAME_INVALID`.
- Service test that duplicate branch input throws `CHANGE_BRANCH_NAME_DUPLICATE`.

### 7. Wrong vs Correct

#### Wrong

```json
{
  "appId": 1,
  "title": "登录页优化",
  "branchName": "feat/login-page-1717651234567",
  "sourceBaseBranchName": "master",
  "ownerUserId": 7,
  "changeKey": "GONE-1"
}
```

#### Correct

```json
{
  "appId": 1,
  "title": "登录页优化",
  "branchSlug": "login-page",
  "openTimestamp": 1717651234567
}
```
