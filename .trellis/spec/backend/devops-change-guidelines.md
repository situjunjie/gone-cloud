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
- Backend creates the remote GitLab branch before inserting the change record.
- Remote branch creation uses `ApplicationDO.repositoryProviderId`, `ApplicationDO.repoIdentifier`, generated `branchName`, and `ApplicationDO.defaultBranchName` as the source ref.
- If remote branch creation fails, the API returns a business error and must not insert `dev_change`.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| `appId` does not exist | Throw `APPLICATION_NOT_EXISTS` |
| generated branch name has Chinese, spaces, control chars, or Git-ref special chars | Throw `CHANGE_BRANCH_NAME_INVALID` |
| generated branch name duplicates another change in the same app | Throw `CHANGE_BRANCH_NAME_DUPLICATE` |
| generated change key duplicates another change in the same app | Throw `CHANGE_KEY_DUPLICATE` |
| linked repository provider is not GitLab or not access-token based | Throw repository provider type/auth business error |
| GitLab branch creation fails | Throw `REPOSITORY_PROVIDER_GITLAB_BRANCH_CREATE_FAIL` |

### 5. Good / Base / Bad Cases

- Good: frontend sends only `appId`, `title`, `branchSlug`, and the modal-open timestamp; backend fills all derived fields and creates the GitLab branch.
- Base: existing full create API `/devops/change/create` remains available for callers that already own all fields.
- Bad: frontend submits `sourceBaseBranchName`, `ownerUserId`, or handcrafted `changeKey` for the application-detail flow.
- Bad: inserting a change record before the remote branch is created.

### 6. Tests Required

- Service test that success inserts `branchName`, `changeKey`, `sourceBaseBranchName`, `ownerUserId`, and active status.
- Service test that success calls code-source branch creation with repository provider id, repo identifier, generated branch name, and default branch.
- Service test that invalid branch input throws `CHANGE_BRANCH_NAME_INVALID`.
- Service test that duplicate branch input throws `CHANGE_BRANCH_NAME_DUPLICATE`.
- Service test that GitLab branch creation failure does not insert the change record.
- Repository provider service test that wraps GitLab branch creation errors as `REPOSITORY_PROVIDER_GITLAB_BRANCH_CREATE_FAIL`.

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

## Scenario: Change Test And Code Review State

### 1. Scope / Trigger

- Trigger: adding or changing test state, code review state, or latest commit synchronization for `dev_change`.
- Scope: `ChangeDO`, `ChangeController`, change VOs, `ChangeService`, `ChangeMapper`, GitLab push hook handling, application release branch response, MySQL SQL, and service tests.

### 2. Signatures

- API: `PUT /devops/change/set-tester`
- Request: `ChangeSetTesterReqVO`
  - `id: Long` required
  - `testerUserId: Long` required
- API: `PUT /devops/change/set-code-reviewer`
- Request: `ChangeSetCodeReviewerReqVO`
  - `id: Long` required
  - `codeReviewerUserId: Long` required
- DB columns on `dev_change`:
  - `tester_user_id bigint null`
  - `test_passed tinyint not null default 0`
  - `test_passed_commit_sha varchar(64) null`
  - `code_reviewer_user_id bigint null`
  - `code_review_status tinyint not null default 0`
  - `code_review_passed_commit_sha varchar(64) null`
- Dict type: `dev_change_code_review_status`, values `0` open, `1` in progress, `2` approved.

### 3. Contracts

- `test_passed` is a numeric flag: `0` means untested/not passed, `1` means passed.
- `code_review_status` uses `ChangeCodeReviewStatusEnum`: `OPEN(0)`, `IN_PROGRESS(1)`, `APPROVED(2)`.
- Create flows must default `testPassed` to `0` and `codeReviewStatus` to `OPEN` when callers omit them.
- GitLab push hook must compare incoming checkout/after SHA with `ChangeDO.latestCommitSha`.
- When the SHA changes, update latest commit metadata and reset conclusion fields: `testPassed=0`, `testPassedCommitSha=null`, `codeReviewStatus=OPEN`, `codeReviewPassedCommitSha=null`.
- Tester and code reviewer assignments are people fields; branch updates must preserve them.
- Release branch response must expose both current latest commit SHA and passed commit SHA fields so the frontend can diff current branch head against the last approved/tested commit.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| Set tester for a missing change | Throw `CHANGE_NOT_EXISTS` |
| Set tester for released/discarded change | Throw `CHANGE_STATUS_NOT_ACTIVE` |
| Set code reviewer for a missing change | Throw `CHANGE_NOT_EXISTS` |
| Set code reviewer for released/discarded change | Throw `CHANGE_STATUS_NOT_ACTIVE` |
| `testPassed` outside `0..1` in save/page request | Reject by Bean Validation |
| `codeReviewStatus` outside enum values | Reject by `@InEnum(ChangeCodeReviewStatusEnum.class)` |
| GitLab hook has blank branch/repo/SHA or zero delete SHA | Return `false` without updating |
| GitLab hook targets a non-GitLab provider | Throw `REPOSITORY_PROVIDER_TYPE_NOT_SUPPORTED` |
| GitLab hook SHA equals stored latest SHA | Update latest metadata only; do not reset test/review conclusions |
| GitLab hook SHA differs from stored latest SHA | Reset test/review conclusions in the same update as latest commit metadata |

### 5. Good / Base / Bad Cases

- Good: a new push moves `latest_commit_sha`; backend clears passed commit SHAs and reopens review while keeping tester/reviewer user ids.
- Base: repeated webhook delivery for the same SHA refreshes message/time but keeps existing test/review conclusions.
- Bad: clearing tester/reviewer assignments on every push.
- Bad: using `updateById` plus default field strategies when an update must explicitly set nullable passed commit SHA fields to `null`.

### 6. Tests Required

- Service test that create-from-application sets `testPassed=0` and `codeReviewStatus=OPEN`.
- Service test that setting tester updates only the tester for an active change.
- Service test that setting tester on non-active change throws `CHANGE_STATUS_NOT_ACTIVE`.
- Service test that setting code reviewer updates only the reviewer for an active change.
- Service test that setting code reviewer on non-active change throws `CHANGE_STATUS_NOT_ACTIVE`.
- Service test that a new GitLab commit calls the mapper reset update and does not use regular `updateById`.
- Service test that same-commit webhook delivery does not reset test/review fields.

### 7. Wrong vs Correct

#### Wrong

```java
change.setLatestCommitSha(commitSha);
change.setTestPassedCommitSha(null);
changeMapper.updateById(change);
```

#### Correct

```java
changeMapper.updateLatestCommitAndResetReviewTest(changeId, commitSha, commitMessage, commitAt, now);
```

## Scenario: Change Code Review Diff And Approval

### 1. Scope / Trigger

- Trigger: adding or changing the independent code-review page backend contract for `dev_change`.
- Scope: `ChangeController`, code-review VOs, `ChangeService`, `ChangeMapper`, `RepositoryProviderService`, GitLab compare integration, error codes, and focused tests.

### 2. Signatures

- API: `GET /devops/change/code-review-diff?id={changeId}`
- Response: `ChangeCodeReviewDiffRespVO`
  - `changeId: Long`
  - `appId: Long`
  - `branchName: String`
  - `sourceBaseBranchName: String`
  - `compareBaseRef: String`
  - `compareTargetRef: String`
  - `codeReviewPassedCommitSha: String`
  - `latestCommitSha: String`
  - `files: List<FileDiff>`
- `FileDiff`
  - `path: String`
  - `oldPath: String`
  - `newPath: String`
  - `changeType: String`, one of `ADDED`, `DELETED`, `RENAMED`, `MODIFIED`
  - `newFile/deletedFile/renamedFile: Boolean`
  - `diff: String`, unified diff content from GitLab compare
- API: `PUT /devops/change/code-review-start`
- Request: `ChangeCodeReviewOperateReqVO`
  - `id: Long` required
- API: `PUT /devops/change/code-review-approve`
- Request: `ChangeCodeReviewOperateReqVO`
  - `id: Long` required

### 3. Contracts

- The diff API is read-only and must not mutate code-review status.
- The start API moves an active, non-approved change to `IN_PROGRESS`; if `codeReviewerUserId` is blank, set it to the current login user.
- The start API must not downgrade an already `APPROVED` change back to `IN_PROGRESS`.
- The approve API requires the change to be active and `latestCommitSha` to be non-blank.
- The approve API sets `codeReviewStatus=APPROVED` and `codeReviewPassedCommitSha=latestCommitSha`; if `codeReviewerUserId` is blank, set it to the current login user.
- Compare base ref selection:
  - first: `ChangeDO.codeReviewPassedCommitSha`
  - second: `ChangeDO.sourceBaseBranchName`
  - fallback: `ApplicationDO.defaultBranchName`
- Compare target ref selection:
  - first: `ChangeDO.latestCommitSha`
  - fallback for viewing only: `ChangeDO.branchName`
- Repository compare must go through the linked code source: `ApplicationDO.repositoryProviderId + repoIdentifier`.
- Do not return GitLab4J model classes directly from controller responses; convert them to internal DTOs/VOs.
- Frontend per-file viewed state is client-only. Backend must not persist file-level review progress unless a future PRD explicitly adds it.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| Diff/start/approve for a missing change | Throw `CHANGE_NOT_EXISTS` |
| Diff/start/approve for released/discarded change | Throw `CHANGE_STATUS_NOT_ACTIVE` |
| Approve when `latestCommitSha` is blank | Throw `CHANGE_LATEST_COMMIT_NOT_EXISTS` |
| Linked application does not exist | Throw `APPLICATION_NOT_EXISTS` |
| Linked code source is not GitLab/access-token | Throw repository provider type/auth business error |
| GitLab compare fails | Throw `REPOSITORY_PROVIDER_GITLAB_COMPARE_FAIL` |

### 5. Good / Base / Bad Cases

- Good: first review compares `source_base_branch_name` to current branch head/latest SHA.
- Good: later review compares last approved SHA to current branch head/latest SHA.
- Good: opening the page calls `code-review-start`; re-opening an already approved review does not change status.
- Bad: making `GET /code-review-diff` change the review state.
- Bad: storing per-file viewed state in `dev_change`.
- Bad: approving a change while `latest_commit_sha` is blank, because the approved commit would be ambiguous.

### 6. Tests Required

- Service test that diff uses source base branch before any approval.
- Service test that diff uses `codeReviewPassedCommitSha` after a prior approval.
- Service test that start review sets `IN_PROGRESS` and fills reviewer only when missing.
- Service test that start review does not downgrade `APPROVED`.
- Service test that approve stores `latestCommitSha` as the approved commit SHA.
- Service test that approve without `latestCommitSha` throws `CHANGE_LATEST_COMMIT_NOT_EXISTS`.
- Repository provider service test that GitLab compare results are converted to internal DTOs.
- Repository provider service test that GitLab compare failures become `REPOSITORY_PROVIDER_GITLAB_COMPARE_FAIL`.

## Scenario: Change Publish Finalization

### 1. Scope / Trigger

- Trigger: adding or changing the post-deploy finalization flow that merges a published deploy branch back to the application baseline branch and closes the participating changes.
- Scope: `ChangeService`, `ChangeServiceImpl`, `ChangeMapper`, Git workspace integration, repository-provider branch cleanup, pipeline finalizer handlers, error codes, and focused tests.

### 2. Signatures

- Service API:
  - `void ChangeService.finalizePublishedChanges(List<Long> changeIds, String deployBranchName)`
- Mapper helper:
  - `updateReleasedById(id, releasedAt, mergedToMasterAt, updateTime)`
- Pipeline step:
  - `step: ChangePublishFinalize`
  - platform step, no required `with.*` fields in the current version

### 3. Contracts

- Finalization merges `deployBranchName` into the baseline branch, not the submit snapshot commit SHA and not each `ChangeDO.branchName` individually.
- Baseline branch resolution order:
  - first: `ChangeDO.sourceBaseBranchName`
  - fallback: `ApplicationDO.defaultBranchName`
- Merge/push must reuse the local Git workspace behavior so the release finalizer follows the same Git semantics as existing code-merge operations.
- After merge succeeds, backend sets:
-  - every participating active change `status=RELEASED`
  - every participating active change `releasedAt=now`
  - every participating active change `mergedToMasterAt=now`
- After the release-state update succeeds, backend deletes:
  - each participating remote change branch
  - the remote deploy branch used by this publish run
- Remote branch deletion treats GitLab 404 as success to support retries after a partial cleanup failure.
- If all participating changes are already `RELEASED`, finalization should skip merge/status mutation and only retry remote branch cleanup.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| Finalize with any missing participating change | Throw `CHANGE_NOT_EXISTS` |
| Finalize with any discarded participating change | Throw `CHANGE_STATUS_NOT_ACTIVE` |
| Baseline branch cannot be resolved | Throw `CHANGE_BASELINE_BRANCH_REQUIRED` |
| Deploy branch name is blank | Throw `CHANGE_BRANCH_REQUIRED` |
| Application repo URL missing or Git merge/push fails | Throw `CHANGE_BRANCH_MERGE_FAIL` |
| Repository provider is not GitLab/access-token based | Throw repository provider type/auth business error |
| Remote branch delete returns 404 | Treat as success |
| Remote branch delete returns other GitLab errors | Throw `REPOSITORY_PROVIDER_GITLAB_BRANCH_DELETE_FAIL` |

### 5. Good / Base / Bad Cases

- Good: deployment succeeds, finalizer merges `release/prod/20260622153000` into `master`, marks all participating changes released, deletes each `feat/*` branch, and deletes `release/prod/20260622153000`.
- Base: all participating changes are already released but remote branches still exist; rerunning finalization only retries branch deletion.
- Bad: finalizer uses `PipelineRun.changeSnapshotJson.commitSha` as the merge target instead of merging the deploy branch.
- Bad: finalizer marks the change released while swallowing a Git merge conflict.

### 6. Tests Required

- Service test that active participating changes merge the deploy branch, push baseline, update release fields, evict caches, and delete both change branches and the deploy branch.
- Service test that all-released participating changes skip merge and only delete branches.
- Service tests for blank baseline branch and blank deploy branch.
- Service test that Git merge failure becomes `CHANGE_BRANCH_MERGE_FAIL` and does not update release state.
- Repository provider service test that branch deletion ignores 404 and wraps non-404 GitLab failures.
- Pipeline step handler test that snapshot change ids plus `PipelineRun.branchName` are finalized in one service call and service failures return `StepResult.FAIL`.

### 7. Wrong vs Correct

#### Wrong

```java
changeService.finalizePublishedChanges(List.of(changeId), snapshotCommitSha);
// Wrong: snapshot commit SHA is not the deploy branch name.
```

#### Correct

```java
changeService.finalizePublishedChanges(changeIds, run.getBranchName());
// The service merges the deploy branch into the resolved baseline branch once.
```
