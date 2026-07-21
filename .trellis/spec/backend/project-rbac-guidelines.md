# Project RBAC Guidelines

This document defines the executable authorization contract for tenant-global role templates and private projects in `yudao-module-project`.

## Scenario: Private Project Role And Permission Governance

### 1. Scope / Trigger

- Apply this contract when changing project creation, members, roles, work items, project fields, project-bound queries, or project permission caches.
- System permissions and project permissions are separate layers. Passing one layer never bypasses the other.
- Only private projects are supported. A system superadmin who is not a project member has no project-data bypass.

### 2. Signatures

The project permission catalog is the fixed `ProjectPermissionEnum` set:

```text
PROJECT_QUERY, PROJECT_UPDATE, PROJECT_ARCHIVE, MEMBER_QUERY,
WORK_ITEM_QUERY, WORK_ITEM_CREATE, WORK_ITEM_UPDATE, WORK_ITEM_DELETE,
WORK_ITEM_FIELD_QUERY, WORK_ITEM_FIELD_MANAGE
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
- `PARTICIPANT` initially has `PROJECT_QUERY`, `MEMBER_QUERY`, `WORK_ITEM_QUERY`, `WORK_ITEM_CREATE`, `WORK_ITEM_UPDATE`, and `WORK_ITEM_FIELD_QUERY`. It must always retain `PROJECT_QUERY` and `WORK_ITEM_QUERY`.
- Project creation copies all current tenant-global roles and permissions. The copied rows keep no source link and never receive later template updates.
- The creator receives `ADMIN + PARTICIPANT`. A different owner receives `ADMIN` only. A normal member with omitted `roleIds` receives `PARTICIPANT`.
- Effective permissions are the union of all assigned roles. There is no explicit deny, priority, or system-superadmin bypass.
- The current owner must remain a member with `ADMIN`. Owner transfer adds or restores the new owner and merges `ADMIN`; it does not modify the old owner's roles.
- Member and project-role governance requires the matching system permission and built-in project administrator status. Global template governance requires only its dedicated system permission.
- ID-only work-item, field, option, member, and role operations resolve the real owning project from the database before project authorization. A work-item move also requires `WORK_ITEM_CREATE` in the target project.
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
