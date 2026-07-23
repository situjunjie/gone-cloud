# Project RBAC Guidelines

This document defines the executable authorization contract for tenant-global role templates and private projects in `yudao-module-project`.

## Scenario: Private Project Role And Permission Governance

### 1. Scope / Trigger

- Apply this contract when changing project creation, members, roles, work items, project fields, project-bound queries, or project permission caches.
- System permissions and project permissions are separate layers for project management operations. Comments are an explicit exception: because they are nested under a concrete project work item, comment endpoints require only project `COMMENT_*` permissions.
- Only private projects are supported. A system superadmin who is not a project member has no project-data bypass.

### 2. Signatures

The project permission catalog is the fixed `ProjectPermissionEnum` set:

```text
PROJECT_QUERY, PROJECT_UPDATE, PROJECT_ARCHIVE, MEMBER_QUERY,
WORK_ITEM_QUERY, WORK_ITEM_CREATE, WORK_ITEM_UPDATE, WORK_ITEM_DELETE,
WORK_ITEM_FIELD_QUERY, WORK_ITEM_FIELD_MANAGE,
COMMENT_QUERY, COMMENT_CREATE, COMMENT_UPDATE, COMMENT_DELETE
```

Role and relation tables:

```text
proj_project_role(scope, project_id, code, builtin_type, tenant_id)
proj_project_role_permission(role_id, permission_code, tenant_id)
proj_project_member_role(project_id, member_id, role_id, tenant_id)
```

Global templates use `scope = GLOBAL` and `project_id = 0`. Project snapshots use `scope = PROJECT` and the real project id. Member requests use `roleIds: Set<Long>`; an add request may omit it, while an update request must contain at least one role.

Project-bound method security uses the named bean:

```java
@PreAuthorize("@ss.hasPermission('project:work-item:update') and "
        + "@projectSecurity.hasWorkItemPermission(#id, 'WORK_ITEM_UPDATE')")
```

Redis cache names and key payloads are:

```text
project:member_auth#30m      tenantId:projectId:userId
project:role_permissions#30m tenantId:roleId
```

### 3. Contracts

- Each tenant template and every project has immutable built-in `ADMIN` and `PARTICIPANT` roles.
- `ADMIN` stores no permission rows and always has every current and future fixed project permission. Its permissions cannot be assigned or modified.
- `PARTICIPANT` initially has `PROJECT_QUERY`, `MEMBER_QUERY`, `WORK_ITEM_QUERY`, `WORK_ITEM_CREATE`, `WORK_ITEM_UPDATE`, `WORK_ITEM_FIELD_QUERY`, and all four `COMMENT_*` permissions. It must always retain `PROJECT_QUERY` and `WORK_ITEM_QUERY`.
- Project creation copies all current tenant-global roles and permissions. The copied rows keep no source link and never receive later template updates.
- The creator receives `ADMIN + PARTICIPANT`. A different owner receives `ADMIN` only. A normal member with omitted `roleIds` receives `PARTICIPANT`.
- Effective permissions are the union of all assigned roles. There is no explicit deny, priority, or system-superadmin bypass.
- The current owner must remain a member with `ADMIN`. Owner transfer adds or restores the new owner and merges `ADMIN`; it does not modify the old owner's roles.
- Member and project-role governance requires the matching system permission and built-in project administrator status. Global template governance requires only its dedicated system permission.
- ID-only work-item, comment, field, option, member, and role operations resolve the real owning project from the database before project authorization. A work-item move also requires `WORK_ITEM_CREATE` in the target project, and is rejected when the work item already has comments because comment rows retain their original `project_id` authorization boundary.
- Project pages filter by current-user membership in SQL. Cross-project work-item pages and group counts filter by `WORK_ITEM_QUERY` through SQL `EXISTS`, not per-project cache calls.
- Cache values include an explicit non-member snapshot. Member writes evict the exact member key; permission writes evict the exact role key. The transaction-aware cache manager defers eviction until commit.

### 4. Validation & Error Matrix

| Condition | Required result |
|---|---|
| Unknown project permission code | `PROJECT_PERMISSION_CODE_INVALID` or authorization `false` |
| Assign permissions to `ADMIN` | `PROJECT_ADMIN_PERMISSION_IMMUTABLE` |
| Remove a required participant permission | `PROJECT_PARTICIPANT_PERMISSION_REQUIRED` |
| Rename or delete a built-in role | `PROJECT_BUILTIN_ROLE_IMMUTABLE` |
| Delete a project role assigned to a member | `PROJECT_ROLE_IN_USE` |
| Submit an empty member role set | `PROJECT_MEMBER_ROLE_REQUIRED` |
| Assign a role from another project | `PROJECT_ROLE_SCOPE_MISMATCH` |
| Remove the owner or remove owner `ADMIN` | `PROJECT_OWNER_CANNOT_REMOVE` or `PROJECT_OWNER_ADMIN_REQUIRED` |
| Create or update a non-private project | `PROJECT_ONLY_PRIVATE_SUPPORTED` |
| System permission passes but project permission fails | Spring Security access denied |
| Project permission passes but system permission fails | Spring Security access denied without project lookup |
| Cache or database loading fails | Propagate failure; never grant access |

### 5. Good / Base / Bad Cases

- Good: a controller combines `@ss` and `@projectSecurity` with `and`, while its service validates cross-resource moves and owner transitions against database state.
- Good: a role-permission update evicts only `tenantId:roleId`; member snapshots remain reusable and resolve the updated permission set on their next role-cache read.
- Base: a member with two custom roles receives the union of both allow sets and no additional permissions.
- Bad: checking only the system permission because the caller is a superadmin; private project membership still applies.
- Bad: storing a flattened effective-permission cache per user and scanning every member when one shared role changes.
- Bad: trusting a request `projectId` for an ID-only resource without resolving its actual project.

### 6. Tests Required

- Role service tests: fixed catalog, admin immutability, participant minimum permissions, snapshot independence, and in-use deletion rejection.
- Member service tests: creator/owner initialization, default participant, non-empty multi-role assignment, cross-project rejection, owner removal protection, and owner transfer role merge.
- Permission service tests: admin shortcut without role lookup, multi-role union, unknown-code rejection, non-member denial, and no superadmin bypass.
- Cache tests: explicit negative snapshot, tenant-qualified exact keys, role/member eviction, commit-time eviction, and rollback behavior.
- Method Security tests: run a real `@EnableMethodSecurity` context and assert system-permission short-circuit, project denial, and dual-layer success.
- Mapper tests: assert project membership `EXISTS` and cross-project work-item permission `EXISTS`, including bound user id and fixed permission code.
- Comment tests: assert target/comment ownership resolution, author-only edit, author-or-admin delete, archived-project read-only behavior, and participant default comment permissions.
- Run `mvn -pl yudao-module-project/yudao-module-project-server -am test` and `git diff --check`.

### 7. Wrong vs Correct

#### Wrong

```java
@PreAuthorize("@ss.hasPermission('project:work-item:delete')")
public void deleteWorkItem(Long id) {
    workItemMapper.deleteById(id);
}
```

This allows a system-level permission to cross private project boundaries.

#### Correct

```java
@PreAuthorize("@ss.hasPermission('project:work-item:delete') and "
        + "@projectSecurity.hasWorkItemPermission(#id, 'WORK_ITEM_DELETE')")
public void deleteWorkItem(Long id) {
    workItemService.deleteWorkItem(id);
}
```

The resource resolver loads the work item and authorizes against its persisted project id before the service mutation runs.

## Scenario: Project Comment Authorization And Target Boundary

### 1. Scope / Trigger

- Apply when changing the project comment HTTP API, `comment` package, comment tables, mention notifications, comment permissions, or work-item project moves.
- Comments use a controlled target resolver. V1 supports only `WORK_ITEM`; never turn a client `targetType` into a table name or arbitrary SQL identifier.

### 2. Signatures

HTTP contracts:

```text
POST   /project/comment/create  -> targetType,targetId,content,replyToCommentId?,mentionUserIds?
PUT    /project/comment/update  -> id,content,mentionUserIds?
DELETE /project/comment/delete  -> id
GET    /project/comment/page    -> targetType,targetId,pageNo,pageSize
```

Comment operations map directly to project permissions and do not define system menu permissions:

```text
query  -> COMMENT_QUERY
create -> COMMENT_CREATE
update -> COMMENT_UPDATE
delete -> COMMENT_DELETE
```

Persistence and Java names:

```text
proj_comment          -> comment/CommentDO.java, CommentMapper.java
proj_comment_mention  -> comment/CommentMentionDO.java, CommentMentionMapper.java
```

The `comment` package already supplies the project-module boundary, so comment-domain classes use concise `Comment*` names rather than redundant `ProjectComment*` names.

### 3. Contracts

- Resolve the real `projectId` from the target for create/query and from the persisted comment for update/delete. The client never submits an authoritative project id.
- Keep comments tenant-scoped. The main row stores `project_id`, `target_type`, `target_id`, optional `reply_to_comment_id`, `author_user_id`, Markdown `content`, and `NORMAL`/`DELETED` status.
- A reply must reference a `NORMAL` comment under the same project and target. Timeline pages sort by `create_time DESC, id DESC` and batch-load authors, replies, and mentions.
- Content is nonblank, at most 10,000 characters, and contains no raw HTML. Mention ids are explicit, deduplicated, exclude the author, contain at most 20 current project members, and are not inferred from text.
- Only the author may edit. The author or a project `ADMIN` may delete. Business deletion changes status to `DELETED`, clears content, and logically deletes mentions while retaining the main row as a reply placeholder.
- Archived projects remain readable but reject create, update, and delete. A work item with any comment cannot move to another project because persisted comment ownership would otherwise diverge from target ownership.
- Publish mention events in the comment transaction and handle them with `AFTER_COMMIT` best-effort delivery. Notification failure is logged without full content and never rolls back the comment.

### 4. Validation & Error Matrix

| Condition | Required result |
|---|---|
| Unsupported target type or missing target | `PROJECT_COMMENT_TARGET_TYPE_INVALID` or `PROJECT_COMMENT_TARGET_NOT_EXISTS` |
| Blank/oversized content or raw HTML | `PROJECT_COMMENT_CONTENT_INVALID` or `PROJECT_COMMENT_HTML_NOT_ALLOWED` |
| Cross-target, missing, or deleted reply | `PROJECT_COMMENT_REPLY_INVALID` |
| Mention is not a project member or exceeds 20 recipients | `PROJECT_COMMENT_MENTION_INVALID` or `PROJECT_COMMENT_MENTION_LIMIT_EXCEEDED` |
| Non-author edits a comment | `PROJECT_COMMENT_UPDATE_DENIED` |
| Non-author and non-admin deletes a comment | `PROJECT_COMMENT_DELETE_DENIED` |
| Edit/delete sees a deleted or concurrently changed row | `PROJECT_COMMENT_STATUS_INVALID` |
| Move a work item that has comments | `PROJECT_WORK_ITEM_HAS_COMMENTS` |
| Required project comment permission is absent | Spring Security access denied |

### 5. Good / Base / Bad Cases

- Good: the controller checks only the matching project `COMMENT_*` permission; the service independently resolves the target and validates ownership before writing.
- Good: editing mention recipients restores existing logical rows, inserts only new relations, deletes removed relations, and notifies only newly added users.
- Base: a deleted reply target is still returned as a deleted placeholder without content.
- Bad: trust a request `projectId`, scan `@nickname` from Markdown, or let a system superadmin bypass private-project membership.
- Bad: call `deleteById` on the main comment row or move its work item to another project, which breaks historical reply and authorization consistency.

### 6. Tests Required

- Service: create/update/delete/query, reply boundary, author/admin ownership, archived project, raw HTML, mention normalization/member validation, and batch response assembly.
- Mapper: stable timeline order, guarded `NORMAL` updates, content clearing on deletion, and mention differential restore/delete behavior.
- Method security: project denial and success for both target-based and comment-id-based methods, including the exact `COMMENT_*` operation mapping.
- Notification: AFTER_COMMIT listener, author exclusion, newly added mentions only, tenant context, and per-recipient failure isolation.
- Work item: reject full and quick project moves when `selectCountByTarget(WORK_ITEM, id) > 0`.
- Run a clean focused test after renaming or editing generated-source-adjacent code; this workspace may otherwise retain externally generated unresolved-compilation bytecode.

### 7. Wrong vs Correct

#### Wrong

```java
@PreAuthorize("@projectSecurity.hasPermission(#projectId, 'COMMENT_DELETE')")
public void deleteComment(Long projectId, Long id) {
    commentMapper.deleteById(id);
}
```

This trusts a caller-provided project id and bypasses both persisted comment ownership resolution and business deletion.

#### Correct

```java
@PreAuthorize("@projectSecurity.hasCommentPermission(#id, 'COMMENT_DELETE')")
public void deleteComment(Long id) {
    commentService.deleteComment(id); // Author/admin rule and business deletion live in the service.
}
```
