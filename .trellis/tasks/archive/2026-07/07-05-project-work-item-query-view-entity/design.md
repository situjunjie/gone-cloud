# Design

## Scope

This task adds the persistence model for saved work-item query views in the project module. It does not add controllers, services, permissions, or frontend pages.

## Entity

Add `WorkItemQueryViewDO` under `dal/dataobject/workitem`.

View metadata:

- `id`: primary key.
- `name`: display name.
- `code`: stable route/query key.
- `scope`: `SYSTEM` or `USER`.
- `userId`: owner user id for user-level views; `0` for system-level views.
- `routePath`: optional frontend route path for system menu usage.
- `sort`: display order.
- `condition`: JSON object storing the saved work-item page query condition snapshot.
- `remark`: note.

`condition` is mapped to `condition_json` with `JacksonTypeHandler`. It should remain compatible with the current `ProjectWorkItemPageReqVO` shape, but the table will not need schema changes when later work-item filters are added.

## Persistence

Add table `proj_work_item_query_view` in `sql/mysql/project.sql`.

Constraints and indexes:

- Unique `tenant_id, scope, user_id, code` for user-level duplicate prevention.
- Unique `tenant_id, scope, code` for system-level duplicate prevention in MySQL where nullable `user_id` would not protect duplicates.
- Index by `tenant_id, scope, sort` for list ordering.
- Index by `tenant_id, user_id, sort` for personal view listing.
Do not index individual query-condition fields in this table because they live in JSON and are not the primary lookup path for saved views.

## Mapper

Add `WorkItemQueryViewMapper` under `dal/mysql/workitem` with:

- `selectByScopeAndCode(scope, code)`
- `selectSystemList()`
- `selectUserList(userId)`

Business validation and CRUD rules will belong to a future service layer.

## Compatibility

The new table is additive. Existing work-item APIs and queries are unchanged.
