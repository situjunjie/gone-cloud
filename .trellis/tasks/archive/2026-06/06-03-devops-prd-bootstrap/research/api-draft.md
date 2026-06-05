# DevOps API Draft

## Scope

This document defines the first-pass API draft for the MVP DevOps domain.

It is organized around the agreed product workflow:

1. manage application and environments
2. create and maintain changes
3. load an environment tab
4. submit deployment
5. observe pipeline execution
6. approve protected environments
7. rollback to snapshot

All APIs below assume:

- tenant-scoped business data
- admin-side backend APIs
- response shape follows project `CommonResult`

## API Design Principles

1. Use environment-centric APIs for the main DevOps console.
2. Keep `change` and `environment state` separate in API payloads.
3. Model deployment submission as "submit change to environment", not "run arbitrary pipeline".
4. Expose snapshot-based rollback explicitly.

## 1. Application APIs

### 1.1 Create Application

- `POST /admin-api/dev/app/create`

#### Request

```json
{
  "appKey": "erp-core",
  "name": "ERP Core",
  "description": "ERP main service",
  "repoProviderType": "gitlab",
  "repoIdentifier": "group/erp-core",
  "repoUrl": "https://git.example.com/group/erp-core.git",
  "defaultBranchName": "master",
  "ownerUserId": 1001
}
```

#### Response

- `Long id`

### 1.2 Update Application

- `PUT /admin-api/dev/app/update`

### 1.3 Get Application

- `GET /admin-api/dev/app/get?id=...`

### 1.4 Page Applications

- `GET /admin-api/dev/app/page`

#### Query params

- `name`
- `appKey`
- `ownerUserId`
- `status`
- `pageNo`
- `pageSize`

## 2. Environment APIs

### 2.1 Create Environment

- `POST /admin-api/dev/env/create`

#### Request

```json
{
  "envKey": "test",
  "envName": "测试环境",
  "envStage": "TEST",
  "infraType": "K8S"
}
```

### 2.1.1 Bind Environment To Application

- `POST /admin-api/dev/app-env/create`

#### Request

```json
{
  "appId": 1,
  "envId": 11,
  "displayOrder": 10,
  "deployBranchNamePattern": "test-release",
  "approvalRequired": false,
  "approvalConfigJson": null
}
```

### 2.2 Update Environment

- `PUT /admin-api/dev/env/update`

### 2.3 List Environments By App

- `GET /admin-api/dev/env/list-by-app?appId=...`

#### Response

Returns ordered environments for the application.

### 2.4 Get Environment Detail

- `GET /admin-api/dev/env/get?id=...`

## 3. Change APIs

### 3.1 Create Change

- `POST /admin-api/dev/change/create`

#### Behavior

- create change record
- generate change key
- create branch from `master`

#### Request

```json
{
  "appId": 1,
  "title": "支持新的审批规则",
  "description": "预发审批增强"
}
```

#### Response

```json
{
  "id": 101,
  "changeKey": "DEV-20260604-001",
  "branchName": "feat/DEV-20260604-001"
}
```

### 3.2 Update Change

- `PUT /admin-api/dev/change/update`

### 3.3 Get Change

- `GET /admin-api/dev/change/get?id=...`

### 3.4 Page Changes

- `GET /admin-api/dev/change/page`

#### Query params

- `appId`
- `title`
- `branchName`
- `ownerUserId`
- `status`
- `pageNo`
- `pageSize`

### 3.5 Discard Change

- `POST /admin-api/dev/change/discard`

#### Request

```json
{
  "id": 101,
  "reason": "需求取消"
}
```

## 4. Environment Tab APIs

These are the core APIs for the environment-centric console.

### 4.1 Get Environment Console

- `GET /admin-api/dev/env/console?envId=...`

#### Response shape

```json
{
  "env": {
    "id": 11,
    "appId": 1,
    "envKey": "test",
    "envName": "测试环境",
    "approvalRequired": false,
    "currentSnapshotId": 9001
  },
  "pipeline": {
    "definitionId": 21,
    "name": "测试环境默认流水线",
    "stages": ["MERGE", "BUILD", "UNIT_TEST", "DEPLOY"]
  },
  "currentSnapshot": {
    "snapshotId": 9001,
    "baseCommitSha": "abc123",
    "snapshotCommitSha": "def456",
    "changeCount": 3,
    "deployedAt": "2026-06-04T12:30:00"
  },
  "mountedChanges": [
    {
      "changeId": 101,
      "changeKey": "DEV-20260604-001",
      "title": "支持新的审批规则",
      "branchName": "feat/DEV-20260604-001",
      "lastDeployStatus": "SUCCESS",
      "includedInCurrentSnapshot": true
    }
  ],
  "availableChanges": [
    {
      "changeId": 102,
      "changeKey": "DEV-20260604-002",
      "title": "优化流水线页面",
      "branchName": "feat/DEV-20260604-002",
      "status": "ACTIVE"
    }
  ],
  "latestRun": {
    "pipelineRunId": 3001,
    "status": "SUCCESS",
    "currentStageKey": "DEPLOY",
    "startedAt": "2026-06-04T12:20:00",
    "finishedAt": "2026-06-04T12:30:00"
  }
}
```

### 4.2 List Mounted Changes

- `GET /admin-api/dev/env/mounted-change-list?envId=...`

### 4.3 List Available Changes

- `GET /admin-api/dev/env/available-change-list?envId=...`

## 5. Deployment Submission APIs

### 5.1 Submit Change Deployment

- `POST /admin-api/dev/deploy/submit-change`

#### Behavior

- ensure change-env relation exists or becomes mounted
- create pipeline run
- execute the environment's full mounted set

#### Request

```json
{
  "envId": 11,
  "changeId": 102
}
```

#### Response

```json
{
  "pipelineRunId": 3002,
  "mountStatus": "MOUNTED"
}
```

### 5.2 Retry Pipeline Run

- `POST /admin-api/dev/deploy/retry-run`

#### Request

```json
{
  "pipelineRunId": 3002
}
```

## 6. Pipeline Run APIs

### 6.1 Get Pipeline Run Detail

- `GET /admin-api/dev/pipeline-run/get?id=...`

#### Response

```json
{
  "id": 3002,
  "appId": 1,
  "envId": 11,
  "status": "FAILED",
  "currentStageKey": "MERGE",
  "failureStageKey": "MERGE",
  "failureReason": "Merge conflict between DEV-001 and DEV-002",
  "triggerType": "SUBMIT_DEPLOY",
  "triggerChangeId": 102,
  "snapshotId": null,
  "stageRuns": [
    {
      "stageKey": "MERGE",
      "status": "FAILED",
      "startedAt": "2026-06-04T14:00:00",
      "finishedAt": "2026-06-04T14:01:00",
      "message": "conflict"
    }
  ]
}
```

### 6.2 Page Pipeline Runs

- `GET /admin-api/dev/pipeline-run/page`

#### Query params

- `appId`
- `envId`
- `status`
- `triggerType`
- `pageNo`
- `pageSize`

## 7. Approval APIs

### 7.1 Get Approval Detail

- `GET /admin-api/dev/approval/get-by-run?pipelineRunId=...`

### 7.2 Approve Pipeline Run

- `POST /admin-api/dev/approval/approve`

#### Request

```json
{
  "pipelineRunId": 4001,
  "comment": "允许正式发布"
}
```

### 7.3 Reject Pipeline Run

- `POST /admin-api/dev/approval/reject`

#### Request

```json
{
  "pipelineRunId": 4001,
  "comment": "测试结果不满足要求"
}
```

## 8. Rollback APIs

### 8.1 List Rollback Candidates

- `GET /admin-api/dev/rollback/candidate-list?envId=...`

#### Response

List successful historical snapshots for the environment.

### 8.2 Rollback To Snapshot

- `POST /admin-api/dev/rollback/to-snapshot`

#### Request

```json
{
  "envId": 13,
  "snapshotId": 9001,
  "reason": "生产回退到上一稳定版本"
}
```

#### Behavior

- create rollback pipeline run
- reuse artifact or deployment-ready snapshot target
- generate deployment record linked to rollback source

## 9. Audit / History APIs

### 9.1 Get Change History

- `GET /admin-api/dev/change/history?id=...`

#### Returns

- environments this change entered
- related pipeline runs
- approvals
- whether and when it was merged to master

### 9.2 Get Environment History

- `GET /admin-api/dev/env/history?envId=...`

#### Returns

- historical snapshots
- deployment records
- rollback records
- current snapshot

## 10. DTO Draft Suggestions

Recommended request/response object naming:

- `DevAppCreateReqVO`
- `DevEnvCreateReqVO`
- `DevChangeCreateReqVO`
- `DevEnvConsoleRespVO`
- `DevDeploySubmitReqVO`
- `DevPipelineRunRespVO`
- `DevApprovalReqVO`
- `DevRollbackReqVO`

## Suggested Next Step

Translate this API draft into:

1. controller group draft
2. service interface draft
3. permission and operation log points
