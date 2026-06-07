# Release branch merge and online conflict resolution

## Question

发布第一步需要把多个变更分支合并到一起。目标流程是从基准分支签出发布分支，循环合并变更列表分支；如果产生冲突，希望在平台里提供类似 VS Code 的页面完成冲突解决。

## Conclusion

可行，但复杂度中高。推荐分阶段实现：

* MVP：后端用真实 Git 工作区执行发布分支聚合；冲突时平台展示冲突文件列表，支持文本文件三方对比和手工编辑 result；解决后继续 merge。
* 增强版：提供 VS Code 风格的三方面板、accept ours/theirs/both、冲突块计数、逐块解决。
* 高级版：支持删除/重命名冲突、二进制冲突、文件模式冲突、AI 辅助建议、多人协同锁。

不建议平台自己在数据库里模拟 Git 合并。必须让 Git/JGit 产生标准 merge 状态，再把冲突内容投射到前端。

## Research references

* Git merge docs: https://git-scm.com/docs/git-merge
* VS Code merge conflict docs: https://code.visualstudio.com/docs/sourcecontrol/merge-conflicts
* Monaco Editor: https://microsoft.github.io/monaco-editor/
* JGit MergeCommand API: https://javadoc.io/static/org.eclipse.jgit/org.eclipse.jgit/7.4.0.202509020913-r/org.eclipse.jgit/org/eclipse/jgit/api/MergeCommand.html
* GitLab Commits API: https://docs.gitlab.com/api/commits/

## Key technical facts

Git merge behavior:

* When a merge conflict happens, Git pauses the merge.
* The index stores up to three versions for conflicting paths: stage 1 is common ancestor, stage 2 is current `HEAD`, stage 3 is `MERGE_HEAD`.
* The working tree contains conflict-marker content for textual conflicts.
* After resolving, files are staged with `git add`, then the merge is completed with `git merge --continue` or a commit.
* The merge can be abandoned with `git merge --abort`.

VS Code behavior:

* Simple conflicts can be resolved from inline conflict markers.
* Complex conflicts use a 3-way merge editor: incoming, current, and result.
* User can accept current, accept incoming, accept both, or manually edit result.

GitLab API behavior:

* GitLab Commit API can create a commit with multiple file actions.
* It is useful for committing resolved file contents to a branch, but it does not replace a real Git merge worktree when you need native conflict state.

## Recommended backend architecture

Create a release merge service around isolated Git workspaces.

Suggested components:

* `ReleaseMergeSessionService`
* `GitWorkspaceManager`
* `GitMergeRunner`
* `ConflictExtractionService`
* `ConflictResolutionService`
* `RepositoryProviderService` extension for GitLab/GitHub/Gitee providers

Workspace strategy:

* Use local temporary clones or `git worktree` under an isolated directory.
* One release merge session owns one workspace.
* Apply strict TTL cleanup and disk quotas.
* Never reuse a dirty workspace across sessions.

Recommended Git flow:

```bash
git clone --no-tags <repo-url> <workspace>
git fetch origin <base-branch> <change-branch-1> <change-branch-2>
git checkout -b release/<app>/<env>/<timestamp> origin/<base-branch>

git merge --no-ff --no-commit origin/<change-branch-1>
git commit -m "Merge change <change-key-1> into release branch"

git merge --no-ff --no-commit origin/<change-branch-2>
# conflict -> pause
git ls-files -u
git show :1:path/to/file
git show :2:path/to/file
git show :3:path/to/file
# write resolved result
git add path/to/file
git merge --continue
```

Important choices:

* Use `--no-ff` to preserve which change branches were merged.
* Use `--no-commit` to inspect and record each merge step before committing.
* Freeze each change branch to commit SHA at release start to avoid branch movement.
* Prefer merging SHAs or remote tracking refs resolved at release start.
* Store the final release commit SHA and push the release branch only after completion, or push an intermediate branch with clear status.

## Data model proposal

Suggested tables:

### `dev_release_merge_session`

* `id`
* `app_id`
* `application_env_id`
* `base_branch`
* `base_commit_sha`
* `release_branch`
* `release_commit_sha`
* `status`: PENDING, MERGING, CONFLICTING, MERGED, FAILED, ABORTED
* `current_change_id`
* `current_branch`
* `current_commit_sha`
* `workspace_key`
* `started_by`
* `started_at`
* `completed_at`
* `error_message`

### `dev_release_merge_item`

* `id`
* `session_id`
* `change_id`
* `branch_name`
* `commit_sha`
* `merge_order`
* `merge_status`: PENDING, MERGING, MERGED, CONFLICTING, SKIPPED, FAILED
* `merge_commit_sha`
* `started_at`
* `completed_at`

### `dev_release_merge_conflict`

* `id`
* `session_id`
* `merge_item_id`
* `file_path`
* `conflict_type`: TEXT, DELETE_MODIFY, ADD_ADD, RENAME, BINARY, SUBMODULE, FILEMODE
* `base_blob_sha`
* `ours_blob_sha`
* `theirs_blob_sha`
* `resolved_blob_sha`
* `status`: UNRESOLVED, RESOLVED, UNSUPPORTED
* `resolved_by`
* `resolved_at`

### `dev_release_merge_conflict_resolution`

* `id`
* `conflict_id`
* `resolution_type`: ACCEPT_OURS, ACCEPT_THEIRS, ACCEPT_BOTH, MANUAL
* `resolved_content`
* `comment`
* `creator`
* `create_time`

## API proposal

* `POST /admin-api/devops/release-merge-sessions`
  Create session from app/env/base branch/change ids.

* `POST /admin-api/devops/release-merge-sessions/{id}/start`
  Start or resume merge.

* `GET /admin-api/devops/release-merge-sessions/{id}`
  Get session status and merge item progress.

* `GET /admin-api/devops/release-merge-sessions/{id}/conflicts`
  List conflicted files.

* `GET /admin-api/devops/release-merge-conflicts/{conflictId}`
  Get base/ours/theirs/working/result content.

* `PUT /admin-api/devops/release-merge-conflicts/{conflictId}/resolution`
  Save resolved result.

* `POST /admin-api/devops/release-merge-sessions/{id}/continue`
  Validate all current conflicts resolved, stage files, continue merge.

* `POST /admin-api/devops/release-merge-sessions/{id}/abort`
  Abort current merge and cleanup workspace.

* `POST /admin-api/devops/release-merge-sessions/{id}/push`
  Push completed release branch.

## Frontend conflict editor options

### Option A: Monaco diff editor + result editor

Use Monaco Editor with:

* left diff: base vs ours
* right diff: base vs theirs
* bottom editor: result
* buttons: accept ours, accept theirs, accept both, mark resolved

Pros:

* Monaco is mature and familiar.
* Easy to integrate in Vue3.
* Good enough for MVP.

Cons:

* Monaco Editor alone does not provide the full VS Code merge editor product experience out of the box.
* Need custom conflict block parsing and accept buttons.

Recommendation: best MVP path.

### Option B: Browser VS Code / code-server style editor

Embed a web IDE or launch VS Code Web against a temporary repo.

Pros:

* Closest to real VS Code experience.
* Natural Git conflict workflow.

Cons:

* Heavy, high operational complexity.
* Harder tenant isolation and permission control.
* More difficult to integrate with platform workflow.

Recommendation: not for first version.

### Option C: Dedicated web merge editor component

Use or build a three-pane merge editor.

Pros:

* Better UX than a simple diff editor.

Cons:

* Need evaluate maintenance and licensing.
* May not handle Git edge cases as well as a custom backend extraction plus Monaco UI.

Recommendation: only after MVP if Monaco-based custom UI is insufficient.

## Conflict categories and handling

| Conflict type | MVP handling | Notes |
|---|---|---|
| Text same-line conflict | Online editor | Primary use case |
| Text add/add conflict | Online editor | Similar to same-line conflict |
| Delete/modify | Require choose keep/delete/manual | Show deleted side clearly |
| Rename/rename | Advanced handling or fallback to local resolution | Harder UX |
| Binary file conflict | Choose ours/theirs only | No text editing |
| File mode conflict | Choose mode | Lower priority |
| Submodule conflict | Choose commit or fallback | Low priority |
| Large file | Fallback local resolution or download/upload | Need size limit |

## Risks and mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Workspace disk growth | Storage pressure | TTL cleanup, per-session quota, shallow clone, single-branch fetch where possible |
| Concurrent release sessions mutate same branch | Wrong release result | Use unique release branch per session and app/env lock |
| Change branch moves during merge | Non-repeatable release | Resolve and store branch commit SHA at session creation |
| Complex Git conflicts exceed web UI | User blocked | Mark unsupported and allow abort/local resolution fallback |
| Binary/large files | UI cannot edit | Ours/theirs only, size limit |
| Incorrect manual resolution | Bad build/deploy | Require Jenkins build/test after merge before deploy |
| Credentials leakage | Security incident | Token encryption, no repo URLs with embedded tokens in UI/logs |
| Long-running merge session | Stale workspace | status timeout, cleanup job, resume/abort controls |

## Recommended MVP

1. Release session creation freezes base commit and change commit SHAs.
2. Backend creates release branch in isolated workspace.
3. Backend sequentially merges change SHAs.
4. On text conflict, expose conflict list and base/ours/theirs/result content.
5. Frontend uses Monaco-based diff/result editor.
6. User saves resolved result.
7. Backend writes file, stages it, continues merge.
8. After all merges complete, backend pushes release branch and records final SHA.
9. Jenkins builds the final release branch/commit.

## Implementation note for Java backend

Two backend implementation choices:

* Native Git CLI: closest to actual Git behavior, easiest for conflict extraction via commands, requires Git installed in runtime container.
* JGit: pure Java and easier dependency management, but Git CLI compatibility and edge-case behavior may differ.

Recommendation:

* For first production version, prefer Git CLI in a controlled workspace if the deployment image can include Git.
* Use JGit only if runtime cannot shell out or if the team needs pure Java. If using JGit, add tests for delete/modify, rename, binary, file mode, and submodule conflicts before relying on it broadly.
