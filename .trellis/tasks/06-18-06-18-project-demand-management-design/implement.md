# Project Management V1 Implementation Plan

## Scope For First Pass

Implement the first backend slice for `yudao-module-project`: project basics and project member management.

This pass intentionally excludes work-item CRUD, work-item status transitions, work-item history, tags, and relations. Those depend on the project access boundary introduced here.

## First Pass Checklist

1. [x] Add project module enums and error codes for project visibility, project status, and project member role.
2. [x] Add `ProjectDO` and `ProjectMemberDO` as tenant-scoped persistence objects.
3. [x] Add `ProjectMapper` and `ProjectMemberMapper` with page, uniqueness, member, and owner lookup helpers.
4. [x] Add admin VOs and MapStruct converter for project create/update/page/detail/member payloads.
5. [x] Add `ProjectService` and `ProjectServiceImpl`:
   - create project and owner member in one transaction
   - validate project code uniqueness
   - validate owner/user ids through `AdminUserApi`
   - update project metadata and transfer owner when needed
   - archive projects without physical delete
   - add/update/remove members
   - expose `validateProjectUsable` and `hasProjectAccess` for future work-item services
6. [x] Add admin controller endpoints under `/project/project`.
7. [x] Add module RPC configuration for `AdminUserApi`.
8. [x] Add MySQL SQL draft for `proj_project` and `proj_project_member`.
9. [x] Add MySQL menu/permission bootstrap data for project basics and project members.
10. [x] Run focused compile for `yudao-module-project/yudao-module-project-server`.

## Scope For Second Pass

Implement the next backend slice: work item main CRUD.

This pass intentionally excludes status transition history, participants, CC users, tags, loose relations, comments, frontend routes, and menu permissions. It establishes the main `proj_work_item` aggregate and core validation surface.

## Second Pass Checklist

1. [x] Add work-item category, priority, and status enums.
2. [x] Add work-item error codes for not-found, invalid planned time, invalid parent, and child-blocked delete/move.
3. [x] Add `ProjectWorkItemDO` as a tenant-scoped persistence object with `assigneeUserId`.
4. [x] Add `ProjectWorkItemMapper` with page filtering, child count, and work item number update helpers.
5. [x] Add admin VOs and MapStruct converter for work item create/update/page/detail/assign payloads.
6. [x] Add `ProjectWorkItemService` and `ProjectWorkItemServiceImpl`:
   - create with default status `PENDING`
   - generate display number `WI-000001` after insert
   - validate project exists and is active
   - validate assignee through `AdminUserApi`
   - validate planned end time is not before planned start time
   - validate parent work item belongs to the same project
   - reject self-parent and ancestor loops
   - reject moving or deleting a work item that has children
7. [x] Add admin controller endpoints under `/project/work-item`.
8. [x] Add MySQL SQL draft for `proj_work_item`.
9. [x] Add dictionary bootstrap data for work-item category, priority, and status.
10. [x] Add MySQL menu/permission bootstrap data for work item CRUD and assignment.
11. [x] Run focused compile for `yudao-module-project/yudao-module-project-server`.

## Validation Commands

- `mvn -pl yudao-module-project/yudao-module-project-server -am -DskipTests compile`

## Rollback Points

- Before SQL changes: Java-only changes can be reverted without database impact.
- After SQL changes: revert both Java code and `sql/mysql/project.sql` together.

## Deferred Work

- Work item operation history.
- Work item status transition service.
- Work item participant, tag, and relation tables.
- Frontend routes and menus.
- Multi-engine SQL conversion beyond the MySQL implementation draft.
