# 工作项查询支持字段逻辑条件

## Goal

Extend the project work item page query contract so the query view UI can submit field-specific logical conditions like the provided design: date fields support range and comparison operators, enum/user/project fields support equality and multi-value inclusion/exclusion, text fields support fuzzy matching, and "me" can resolve to the current login user.

## Requirements

- Keep the existing `/project/work-item/page` query parameters backward compatible for current callers.
- Add a structured condition list to `ProjectWorkItemPageReqVO` so saved work item query views can store and replay different operators per field.
- Support date/time operators for planned and audit time fields: equal, not equal, greater than, greater than or equal, less than, less than or equal, and between.
- Support selectable fields such as owner, creator, category, status, priority, project, iteration, and parent work item with equal, not equal, contains/in, and not contains/not in semantics.
- Support text fields such as title, work item number, description, and remark with equal, not equal, contains, and not contains semantics.
- Resolve current-user tokens such as "me" for user id fields, especially assignee/owner filtering.
- Reject unsupported field/operator/value combinations before building SQL.
- Do not change the work item query view table schema.

## Acceptance Criteria

- [ ] Existing fixed filters (`keyword`, `category`, `status`, `priority`, `assigneeUserId`, `creator`, `projectId`, `iterationId`, `plannedEndTime`, `createTime`) still work.
- [ ] New `conditions` filters apply to the generated page query using the requested field/operator/value.
- [ ] `assigneeUserId = me` filters by the current login user.
- [ ] Invalid fields or operators fail fast with a project module business error.
- [ ] The project module compiles after the change.

## Notes

- Current repository contains backend code only; frontend page changes are outside this worktree.
