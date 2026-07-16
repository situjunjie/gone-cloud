# Database Guidelines

This codebase uses MyBatis-Plus through Yudao framework helpers. The dominant pattern is `DO` entities in `dal/dataobject`, mapper interfaces in `dal/mysql`, and SQL-free query construction with `LambdaQueryWrapperX`.

## Persistence Model

Use the existing stack:

- Business data objects in tenant-scoped modules should extend `TenantBaseDO`, which already inherits `BaseDO` and adds `tenantId`.
- Only platform/core entities that are intentionally outside tenant isolation should extend `BaseDO` directly, typically together with explicit tenant-ignore semantics.
- Mappers extend `BaseMapperX<T>`.
- Query logic lives in mapper default methods, not scattered through services.
- Database scripts are maintained under `sql/<engine>/`.

Examples:

- [TenantBaseDO.java](/Users/situjunjie/projects/gone-cloud/yudao-framework/yudao-spring-boot-starter-biz-tenant/src/main/java/cn/iocoder/yudao/framework/tenant/core/db/TenantBaseDO.java:1) is the tenant-scoped base type and adds `tenantId` on top of `BaseDO`.
- [IotProductDO.java](/Users/situjunjie/projects/gone-cloud/yudao-module-iot/yudao-module-iot-server/src/main/java/cn/iocoder/yudao/module/iot/dal/dataobject/product/IotProductDO.java:1) extends `TenantBaseDO`.
- [AdminUserDO.java](/Users/situjunjie/projects/gone-cloud/yudao-module-system/yudao-module-system-server/src/main/java/cn/iocoder/yudao/module/system/dal/dataobject/user/AdminUserDO.java:1) extends `TenantBaseDO`.
- [TenantDO.java](/Users/situjunjie/projects/gone-cloud/yudao-module-system/yudao-module-system-server/src/main/java/cn/iocoder/yudao/module/system/dal/dataobject/tenant/TenantDO.java:1) extends `BaseDO` and is marked `@TenantIgnore` because it is part of the platform's core tenant system itself.
- [ImRtcCallMapper.java](/Users/situjunjie/projects/gone-cloud/yudao-module-im/yudao-module-im-server/src/main/java/cn/iocoder/yudao/module/im/dal/mysql/rtc/ImRtcCallMapper.java:1) extends `BaseMapperX<ImRtcCallDO>` and exposes domain-specific query/update helpers.
- Multi-engine bootstrap SQL lives under [sql/mysql](/Users/situjunjie/projects/gone-cloud/sql/mysql), [sql/postgresql](/Users/situjunjie/projects/gone-cloud/sql/postgresql), [sql/oracle](/Users/situjunjie/projects/gone-cloud/sql/oracle), and other sibling directories.

## Tenant Boundary Rule

For new business modules and business entities:

- Default to `TenantBaseDO`.
- Treat `tenantId` as part of the entity contract, not an optional add-on.
- Use direct `BaseDO` only when the data is platform-global, system-core, or explicitly excluded from tenant filtering by design.

This matters for new domain modeling work: if the feature serves tenant business data, the draft entity list should assume tenant scope from the beginning.

## Query Patterns

Preferred mapper style:

1. Define small, named default methods on the mapper.
2. Use `LambdaQueryWrapperX` and `eqIfPresent`, `betweenIfPresent`, `orderByDesc`, and similar helpers for conditional filters.
3. Use `selectPage(reqVO, wrapper)` for paginated admin listings.
4. Use targeted update conditions for optimistic state transitions.

Example patterns from [ImRtcCallMapper.java](/Users/situjunjie/projects/gone-cloud/yudao-module-im/yudao-module-im-server/src/main/java/cn/iocoder/yudao/module/im/dal/mysql/rtc/ImRtcCallMapper.java:1):

- `selectByRoom(String room)` for direct key lookup
- `selectPage(ImRtcCallManagerPageReqVO reqVO)` for page queries
- `updateByIdAndStatus(...)` for guarded state changes

## Transactions

Transactions are declared at service or controller orchestration boundaries when one operation spans multiple writes. When a module already uses `@Transactional(rollbackFor = Exception.class)`, follow that local pattern rather than moving transaction control into mapper code.

Examples:

- [MesWmStockTakingTaskResultController.java](/Users/situjunjie/projects/gone-cloud/yudao-module-mes/yudao-module-mes-server/src/main/java/cn/iocoder/yudao/module/mes/controller/admin/wm/stocktaking/task/MesWmStockTakingTaskResultController.java:83)
- [MesWmStockTakingTaskResultController.java](/Users/situjunjie/projects/gone-cloud/yudao-module-mes/yudao-module-mes-server/src/main/java/cn/iocoder/yudao/module/mes/controller/admin/wm/stocktaking/task/MesWmStockTakingTaskResultController.java:91)

## Naming Conventions

- Tables and columns follow the SQL scripts already shipped with the scaffold; do not invent a second naming scheme per module.
- DevOps business tables use the short `dev_` physical prefix, not `devops_`. For example, use `dev_artifact_registry` and `dev_artifact_repository` for artifact registry data while keeping permission strings under the existing `devops:*` namespace.
- Java persistence classes use `<DomainName>DO` based on the business aggregate inside the module package. Do not repeat the module name as a class prefix; for example use `ApplicationDO` under `cn.iocoder.yudao.module.devops...`, not `DevopsApplicationDO`.
- Mapper interfaces use `<DomainName>Mapper`.
- Request objects for list pages usually use `<DomainName>PageReqVO`.

## SQL Script Conventions

- Keep vendor-specific schema/bootstrap files under the matching `sql/<engine>/` directory.
- If a change is schema-affecting, update each supported engine only if the project already maintains that object there. Do not add one-off SQL only for MySQL if the same table is cross-engine in this repo.
- Reuse existing helper docs under `sql/tools/` when schema conversion matters.

## Wrong vs Correct

### Wrong

- Writing ad hoc query logic in services with raw mapper wrapper code repeated in multiple places
- Returning huge unfiltered lists when surrounding modules expose paged admin listings
- Using bare `updateById` for state transitions that should check current status

### Correct

- Add a named mapper default method with `LambdaQueryWrapperX`
- Use `selectPage(reqVO, wrapper)` for admin screens
- Guard updates with predicates like `eq(id)` and `eq(status, oldStatus)`

## Common Mistakes

- Using `BaseDO` directly for new tenant business entities and accidentally dropping `tenantId`.
- Skipping `BaseDO`/`TenantBaseDO` entirely and losing standard audit columns.
- Putting cross-record write orchestration in mapper methods instead of the service/controller layer.
- Forgetting that this repository carries multiple database engines, so schema changes need a broader scan than one SQL file.
- Replacing logical-delete relation rows with `delete + insert` when the business unique key does not include `deleted`.
  Use differential update instead: update active rows in place, restore logically deleted rows when the same business key is requested again, insert only truly new rows, and logically delete only rows removed from the submitted set. This preserves relation IDs referenced by downstream tables and avoids unique-key collisions.

## Scenario: Filterable Tenant-Global And Project Custom Fields

When tenant business entities need tenant-global or project-configurable fields that must participate in filtering:

- Keep stable workflow fields in the aggregate main table; do not add a JSON `Map` column to the main DO as the database source of truth.
- Store field definitions and select options in tenant tables with an explicit immutable scope. Use `GLOBAL` for all projects in the current tenant and `PROJECT` for one project; tenant-global never means cross-tenant.
- Keep a non-null project sentinel for global rows when the existing schema requires `project_id`; validate both `scope` and the sentinel instead of trusting either column alone.
- Query an effective field set as tenant-global definitions plus the current project's definitions, with deterministic ordering.
- Keep field codes and types immutable. A project field must not reuse a tenant-global code, and a new tenant-global field must be rejected when any project in the tenant already uses that code. Different projects may still reuse the same project-field code.
- Store values in typed columns (`text`, `number`, `datetime`, `boolean`, `user`) instead of one universal string column.
- Store single-select and multi-select option references in a relation table so option rename and disable operations do not rewrite historical values.
- Disable field definitions and options instead of deleting them when historical records or saved filters may still reference their ids.
- Build dynamic filters from server-resolved metadata. Only server enums may choose physical column names; client-provided field ids and values must remain bound parameters.
- When page queries and aggregate queries expose the same filter contract, extract one shared Wrapper builder and let both paths consume it. Do not reimplement condition groups or custom-field predicates in a separate statistics mapper.
- For dynamic single-field grouping, resolve the group source and physical value column from server enums plus validated field metadata. Keep constructors/factories type-safe so callers cannot pass arbitrary SQL identifiers into mapper XML.
- When grouping requires `LEFT JOIN` while existing filters use unqualified main-table columns or correlated references, filter the main aggregate in a derived subquery first, then join field-value tables outside it. This preserves filter semantics, avoids ambiguous columns, and keeps missing values available as a `null` group.
- Multi-select grouping should group by each option relation and use `COUNT(DISTINCT aggregate_id)` so one record may enter several option groups without duplicate rows inflating one group's count.
- When the product only needs groups of AND conditions joined by OR, model that bounded structure explicitly instead of accepting an arbitrary recursive boolean-expression tree. Generate one parenthesized block, use `AND` inside each non-empty group, and use `OR` only between groups.
- Saved query views should persist the same condition shape accepted by the page API and validate through the same service rules. Validate a temporary copy so execution tokens such as `me` remain symbolic in storage and resolve only when the query runs.
- Use correlated `EXISTS` / `NOT EXISTS` conditions for multi-value filters so pagination does not duplicate aggregate rows.
- Define `is_empty` / `is_not_empty` explicitly. Do not make `not_in` silently include missing values.
- Require a single project boundary for custom-field filtering. Global fields may be used within that project boundary, but cross-project filtering still needs a separate semantic mapping design.
- Batch-load custom values after fetching a page of aggregates to avoid N+1 mapper calls.
- If value rows are purely internal children with no downstream id references, transactionally physical-replacing the snapshot is acceptable and avoids logical-delete unique-key collisions. If downstream tables reference value ids, use the differential restore pattern instead.

## Scenario: Work Item Runtime Custom-Field Schema

### 1. Scope / Trigger

- Trigger: adding or changing the runtime field metadata used by project work-item create/edit forms.
- Scope: `ProjectWorkItemController`, `WorkItemFieldService`, effective field scope queries, field/option response VOs, and focused service tests.

### 2. Signatures

- API: `GET /project/work-item/schema?projectId={projectId}&category={category?}`.
- Response: `CommonResult<List<WorkItemFieldRespVO>>`.
- Permission: `project:work-item:query`.
- `projectId` is required; optional `category` must use `ProjectWorkItemCategoryEnum`.

### 3. Contracts

- Runtime Schema merges tenant-global and current-project field definitions through the same effective-scope query used by field value validation.
- Return enabled fields only. When `category` is present, return fields whose `applicableCategory` is blank or equals the requested category.
- Return enabled options only; disabled options remain available to management and historical-value response paths.
- Preserve deterministic field and option ordering by `sort ASC, id ASC`.
- Keep `/project/work-item-field/list` as the management contract that may include disabled fields/options. Do not change it into a runtime-writable Schema.
- Controller delegates filtering and response assembly to `WorkItemFieldService`; it must not implement scope/category/enablement business logic.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| `projectId` is missing | Bean validation/request binding rejects the request |
| `category` is outside `ProjectWorkItemCategoryEnum` | Bean validation rejects the request |
| Project does not exist or is unusable | Reuse `ProjectService#validateProjectUsable` business error |
| No enabled applicable fields exist | Return an empty list |
| Field or option becomes disabled after Schema retrieval | Final work-item write validation rejects the stale value |

### 5. Good / Base / Bad Cases

- Good: create form requests Schema after choosing project/category, then submits values by returned `fieldId`.
- Base: caller omits `category` and receives all enabled fields for the project, retaining `applicableCategory` for local filtering.
- Bad: frontend uses the management list as writable Schema and displays disabled fields/options for new values.
- Bad: Controller independently reimplements global/project merging or category filtering.

### 6. Tests Required

- Service test that global and project fields are returned together.
- Service test that disabled fields, mismatched categories, and disabled options are filtered.
- Regression test that the management list still returns disabled options.
- Affected project server compile and tests must pass.

### 7. Wrong vs Correct

#### Wrong

```java
return fieldService.getFieldList(projectId); // Management list includes disabled configuration.
```

#### Correct

```java
return fieldService.getFieldSchema(projectId, category); // Runtime-writable effective Schema.
```

## Scenario: Removing Unused Persisted API Fields

### 1. Scope / Trigger

- Trigger: removing a database-backed field that is also present in request/response VOs, DOs, mappers, SQL scripts, tests, or generated API contracts.
- Scope: backend `DO` classes, controller VOs, converter mappings, mapper SQL/update helpers, bootstrap SQL, tests, and downstream client adaptation notes.

### 2. Signatures

- Java field: remove the field from the `*DO` and every `*ReqVO` / `*RespVO` that exposes it.
- Mapper SQL: remove the column from handwritten `SELECT`, `UPDATE`, `INSERT`, and wrapper `.set(...)` calls.
- Database script: remove the column from every maintained `sql/<engine>/` schema that defines the table.

### 3. Contracts

- Requests must no longer require or document the removed field.
- Responses must no longer serialize the removed field.
- Existing service behavior must not depend on defaulting the removed field unless a compatibility requirement is explicitly documented.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| Old client sends the removed JSON field and no compatibility layer is required | Backend ignores the unknown JSON field according to normal Jackson behavior |
| Required validation annotation remains on the removed field | Fix the VO import/annotation removal before merge |
| Handwritten mapper SQL still selects or updates the removed column | Fix the mapper before tests are considered meaningful |
| Bootstrap SQL still defines the column | Fix the schema script or document why that engine/object is intentionally retained |

### 5. Good / Base / Bad Cases

- Good: field is removed from DO, VOs, handwritten mapper SQL, schema scripts, tests, and client adaptation notes.
- Base: field was only persisted/displayed and has no business behavior; tests update helper builders and assertions.
- Bad: field is removed from the VO but still exists in mapper SQL, causing runtime SQL errors after the database column is dropped.

### 6. Tests Required

- Run an affected-module compile/test command so MapStruct, Lombok accessors, handwritten mapper references, and service tests are checked.
- Run a residual search for both Java and SQL names, for example `rg "fieldName|field_name" --glob '!target/**'`.
- Update tests that asserted persistence or response mapping of the removed field.

### 7. Wrong vs Correct

#### Wrong

```java
// DO field removed, but mapper still writes the dropped column
.set(ApplicationEnvDO::getRemovedField, env.getRemovedField())
```

#### Correct

```java
// Remove the field across DO, VO, mapper SQL, schema, and tests in one change.
.set(ApplicationEnvDO::getDisplayOrder, env.getDisplayOrder())
```

## Scenario: Secret Fields Stored Through MyBatis

### 1. Scope / Trigger

- Trigger: adding database columns that store credentials, tokens, passwords, private keys, webhook secrets, or external-system access material.
- Scope: backend `DO` classes, MyBatis-Plus mappings, SQL comments, and runtime configuration for modules under `yudao-module-*`.

### 2. Signatures

- Java DO annotation:
  ```java
  @TableName(value = "xxx_table", autoResultMap = true)
  public class XxxDO extends TenantBaseDO {
      @TableField(typeHandler = EncryptTypeHandler.class)
      @ToString.Exclude
      private String accessToken;
  }
  ```
- Required import: `cn.iocoder.yudao.framework.mybatis.core.type.EncryptTypeHandler`.
- Required config key: `mybatis-plus.encryptor.password`.

### 3. Contracts

- Request VOs may accept the raw secret when creating or rotating the credential.
- Response VOs must not expose the raw secret; return a mask field such as `tokenMask` when UI display is needed.
- SQL column comments should state that the value is encrypted, for example `访问令牌，加密存储`.
- Runtime environments that read/write encrypted fields must define `mybatis-plus.encryptor.password`; missing values fail at persistence time.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| Create request omits a required token/password | Throw a module `ErrorCodeConstants` business error |
| Update request omits token/password intentionally | Preserve the existing encrypted value when local product semantics allow it |
| Response VO includes raw token/password | Reject in review; expose only a mask or derived metadata |
| `@TableField(typeHandler = EncryptTypeHandler.class)` is used without `autoResultMap = true` | Fix the DO mapping before merging |
| `mybatis-plus.encryptor.password` is missing in the active runtime config | Add the config key; do not bypass encryption |

### 5. Good / Base / Bad Cases

- Good: access token is encrypted by `EncryptTypeHandler`, excluded from `toString()`, hidden from response VOs, and paired with a `tokenMask`.
- Base: an existing module already has `mybatis-plus.encryptor.password` in shared application config; the new DO only needs the type handler annotations.
- Bad: raw access tokens are stored as plain `String` fields and returned by MapStruct into a response VO.

### 6. Tests Required

- Compile the affected module so MyBatis annotations and MapStruct mappings are checked.
- For service tests, assert create-time missing-secret validation, update-time preserve-secret behavior, and that response conversion has no raw secret field.
- For DB tests, assert inserting and selecting through the mapper returns the decrypted value while the raw DB value is encrypted when an embedded database is available.

### 7. Wrong vs Correct

#### Wrong

```java
@TableName("devops_repository_provider")
private String accessToken;
```

#### Correct

```java
@TableName(value = "devops_repository_provider", autoResultMap = true)
private String tokenMask;

@TableField(typeHandler = EncryptTypeHandler.class)
@ToString.Exclude
private String accessToken;
```

## Scenario: Sortable Admin Page Requests

### 1. Scope / Trigger

- Trigger: an admin page API must let callers choose one or more main-table sort fields.
- Scope: page request VOs and `BaseMapperX` mapper default methods; custom-field or joined-table sorting needs a separate validated design.

### 2. Signatures

```java
public class XxxPageReqVO extends SortablePageParam {
}

default PageResult<XxxDO> selectPage(XxxPageReqVO reqVO) {
    LambdaQueryWrapperX<XxxDO> query = buildQueryWrapper(reqVO);
    PageUtils.buildDefaultSortingField(reqVO, XxxDO::getId);
    return selectPage(reqVO, query);
}
```

Request payload:

```json
{
  "sortingFields": [
    { "field": "plannedEndTime", "order": "asc" },
    { "field": "priority", "order": "desc" }
  ]
}
```

### 3. Contracts

- `field` uses the Java camel-case DO property name; the shared MyBatis utility converts it to the physical underscore column name.
- `order` uses `asc` or `desc`, and list order determines SQL sort precedence.
- Use `PageUtils.buildDefaultSortingField` when an existing endpoint must preserve a default order; it only fills an absent or empty list.
- Pass the sortable request directly to `BaseMapperX.selectPage`; do not rebuild the same order clauses in Controller or Service code.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| `sortingFields` is null or empty | Apply the endpoint's documented default sorting |
| Multiple valid main-table fields are supplied | Apply them in request-list order |
| `field` contains unsafe SQL characters | Shared MyBatis sorting ignores the field |
| `field` is syntactically safe but is not a mapped main-table column | Treat as an unsupported API input; add endpoint validation when a strict field whitelist is required |
| `order` is `asc` | Sort ascending |
| `order` is absent or not `asc` | Shared MyBatis behavior sorts descending |

### 5. Good / Base / Bad Cases

- Good: request VO extends `SortablePageParam`, mapper supplies only the compatible default, and `BaseMapperX` applies caller sorting.
- Base: no sorting is supplied and the endpoint returns the same default order as before the API change.
- Bad: Controller concatenates request fields into SQL, or mapper always appends its old default after caller sorting and changes precedence.

### 6. Tests Required

- Capture the MyBatis `IPage` passed by `BaseMapperX` and assert converted column names, directions, and multi-field order.
- Add a no-parameter regression test asserting the endpoint's default sorting.
- Run the affected module tests so inherited request accessors and mapper overload resolution compile together.

### 7. Wrong vs Correct

#### Wrong

```java
query.last("ORDER BY " + reqVO.getSortField() + " " + reqVO.getSortOrder());
```

#### Correct

```java
PageUtils.buildDefaultSortingField(reqVO, XxxDO::getId);
return selectPage(reqVO, query);
```
