# Design

## Scope

The implementation stays in `yudao-module-project/yudao-module-project-server` and extends the existing work item page endpoint. The saved query view table already stores arbitrary JSON in `condition_json`, so no database migration is needed.

## API Contract

`ProjectWorkItemPageReqVO` gains `conditions: List<Condition>`.

Each condition has:

- `field`: stable field key matching the work item API field name.
- `operator`: stable operator code.
- `value`: scalar or array JSON value.
- `valueEnd`: optional second value for range conditions.

Supported operator codes:

- Text/selectable: `eq`, `ne`, `in`, `not_in`, `contains`, `not_contains`.
- Comparable date/number: `eq`, `ne`, `gt`, `ge`, `lt`, `le`, `between`.

User field values may be numeric IDs or the string token `me` / `currentUser`. The service resolves those tokens to the current login user before the mapper builds SQL.

## Implementation Shape

- Add a query-field enum that maps allowed field keys to `ProjectWorkItemDO` columns, Java value types, and operator families.
- Add a query-operator enum for validation and switch-based SQL construction.
- Validate and normalize `conditions` in `ProjectWorkItemServiceImpl#getWorkItemPage`.
- Apply the normalized conditions in `ProjectWorkItemMapper.selectPage` after legacy fixed filters and before ordering.
- Keep SQL generation through `LambdaQueryWrapperX` / MyBatis-Plus lambda wrappers, not raw SQL strings.

## Compatibility

Existing simple parameters remain on the VO and retain their current behavior. When both legacy params and `conditions` are supplied, they are ANDed together, matching the visible filter-chip behavior in the UI.
