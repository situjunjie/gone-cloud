# Research: work-item DTO and worklog aggregation contract

- Query: Audit Project Work Item write/read DTOs, aggregate fields, worklog CRUD/read paths, hierarchy support, persistence schema, and focused tests for the scoped worklog contract.
- Scope: internal
- Date: 2026-07-28

## Findings

### Current DTO contract and deprecated field status

- `ProjectWorkItemSaveReqVO` is the shared create/full-update body (`ProjectWorkItemController.java:65-102`) and has no `estimatedWorkload`, `estimatedHours`, or `actualHours` property (`ProjectWorkItemSaveReqVO.java:15-69`).
- Quick update accepts a generic `{ field, value }` list, but its server-side allowlist contains only mutable persisted work-item fields (`ProjectWorkItemUpdateFieldEnum.java:15-26`). `ProjectWorkItemServiceImpl#parseUpdateFieldValues` rejects any unknown built-in name before a write (`ProjectWorkItemServiceImpl.java:348-367`), so `estimatedWorkload`, `estimatedHours`, and `actualHours` cannot be updated through `update-field`.
- `ProjectWorkItemRespVO` exposes only the two aggregate read values: `estimatedHours` and `actualHours` (`ProjectWorkItemRespVO.java:61-65`). The MapStruct conversion intentionally ignores both values (`ProjectWorkItemConvert.java:32-38`); they are populated later from worklogs rather than copied from a work-item DO.
- `ProjectWorkItemDO` has no estimated/actual/legacy workload member (`ProjectWorkItemDO.java:24-79`). Repository-wide source search found the legacy name only in `sql/mysql/project-worklog-incremental-20260728.sql:26`, which drops `estimated_workload`; the baseline `proj_work_item` table also omits it (`sql/mysql/project.sql:126-157`).
- Compatibility implication: an older database that still has the old physical column is harmless to the Java mapping because it is not represented in the DO or DTO. Do not promise that a JSON body containing an unknown legacy property is always rejected: shared `JsonUtils` disables unknown-property failure (`JsonUtils.java:45-49`), while the MVC mapper does not state an explicit unknown-property setting in project YAML. The required invariant is that it has no persistent or response effect.

### Read-time aggregation behavior

- Both work-item reads flow through `assembleWorkItemDetails`: detail invokes it after conversion (`ProjectWorkItemServiceImpl.java:211-218`) and page invokes it for the whole returned page (`ProjectWorkItemServiceImpl.java:222-227`).
- That assembler batch-loads custom values and then calls `WorklogMapper#selectWorkItemSummaries` once for every work-item ID in the response page (`ProjectWorkItemServiceImpl.java:246-260`). Missing rows are filled as `0.00`, so the fields are read-only, live aggregates without N+1 worklog queries.
- The aggregation SQL sums undeleted `ESTIMATED` and `ACTUAL` worklogs by `work_item_id` (`WorklogMapper.java:51`; `WorklogMapper.xml:51-60`).
- Worklog create/update/delete only insert/update/logically delete `proj_worklog` rows (`WorklogServiceImpl.java:67-93`, `100-127`, `133-145`). They deliberately do not write work-item columns. Subsequent work-item reads query the aggregate anew; worklog summary also queries the live table (`WorklogServiceImpl.java:166-182`, `WorklogMapper.xml:36-49`). There is no aggregate cache to invalidate.
- This already satisfies the desired post-CRUD aggregation model. The missing coverage is a focused test proving a newly changed worklog is reflected in the next work-item detail/page aggregate and summary query.

### Existing worklog filter and scope integration point

- `WorklogFilterReqVO` is the shared superclass for page and summary and currently contains `projectId`, optional `workItemId`, and all shared filters, but no scope (`WorklogFilterReqVO.java:13-37`; `WorklogPageReqVO.java:11-31`; `WorklogSummaryReqVO.java:8-15`). It is the correct single DTO insertion point for `workItemScope` and the conditional Bean Validation rule.
- Current mapper filtering directly applies `workItemId = ?` (`WorklogMapper.java:23-37`), and the page method adds fixed `work_date DESC, id DESC` ordering (`WorklogMapper.java:40-43`). A `DESCENDANTS` implementation must not leave that equality predicate active, or it will conflict with the descendant ID set.
- Page presently calls `worklogMapper.selectPage(reqVO)` (`WorklogServiceImpl.java:156-163`); summary separately builds `worklogMapper.buildQueryWrapper(reqVO)` and uses it for total and group aggregates (`WorklogServiceImpl.java:166-182`). Introduce one shared scoped-wrapper path (or a shared resolved-scope object) used by both calls. Resolve the range first, then add the same `IN (resolvedIds)` condition before filters, count/page ordering, total, and groups.
- Empty descendant scope must short-circuit before any `IN` wrapper is built. An empty MyBatis `in` can otherwise omit its predicate and accidentally return the project-wide query. Return `PageResult(emptyList, 0)` and a response with `0.00/0.00` and `groups=[]` explicitly.

### Hierarchy, authorization, and schema facts

- The work-item hierarchy is the nullable `parent_work_item_id` column (`ProjectWorkItemDO.java:72-75`; `sql/mysql/project.sql:139`) with an existing `(tenant_id, parent_work_item_id)` index (`sql/mysql/project.sql:155`). There is no closure table, recursive CTE, or existing descendant-ID mapper method in the project module.
- Ordinary create/update validation prevents newly submitted cycles, but the parent-chain traversal itself has no visited-set guard (`ProjectWorkItemServiceImpl.java:486-516`). It must not be reused to resolve dirty persisted data: a cycle unrelated to the updated item can loop forever.
- Recommended resolver: add one mapper query that returns IDs only. Its recursive member should constrain `tenant_id`, `project_id`, and `deleted=0`; project scoping prevents corrupt cross-project child links from leaking. Use a recursive CTE whose projected recursive key is only `id` and whose set operator is `UNION` (not `UNION ALL`), then filter the root ID out in the final select. The distinct recursive set terminates when a dirty cycle re-emits an already seen ID. `SELF` can return just the root ID; omitted root must skip the resolver and retain the existing project-wide query.
- Project authorization is project-scoped, not work-item-scoped: `ProjectSecurityFrameworkService#hasWorkItemPermissions` resolves the work item and delegates every permission check to its `projectId` (`ProjectSecurityFrameworkService.java:64-77`). The worklog page/summary controller already checks `WORKLOG_QUERY` for the request project (`WorklogController.java:77-94`), while `ProjectWorkItemMapper#applyProjectPermissionFilter` is a reusable SQL-shape example but hard-codes the caller-supplied work-item permission string (`ProjectWorkItemMapper.java:43-55`).
- Therefore a normal same-project child cannot be independently unauthorized in the current data model. To meet the no-leak requirement defensively, the descendant resolver should constrain the requested/root project (and tenant/deletion); if the requirement intends true per-child ACL, that needs a new authorization model rather than a worklog query-only change. A permission test can currently model a corrupt cross-project descendant and verify it is excluded, or assert endpoint denial when `WORKLOG_QUERY` is absent.
- `proj_worklog` is already a separate tenant-scoped entity with project/work-item identifiers and relevant indexes (`sql/mysql/project.sql:159-181`; `WorklogDO.java:19-30`). No schema change is needed for range semantics or aggregate fields.

### Recommended implementation shape

1. Add a String-valued enum following `WorklogGroupByEnum` / `WorklogTypeEnum` (`WorklogGroupByEnum.java:11-30`), for `SELF` and `DESCENDANTS` only. A name such as `WorklogWorkItemScopeEnum` avoids collision with existing field/query-view scope enums.
2. Add `workItemScope` to `WorklogFilterReqVO`, validate it with `@InEnum`, and add an `@AssertTrue` method requiring `workItemId != null` whenever scope is non-null. Keep the default of `SELF` in the service resolver only when `workItemId` is present and no scope was sent.
3. Add a one-query cycle-safe descendant-ID mapper method under `ProjectWorkItemMapper` plus its XML statement. Do not add application-side per-node mapper calls. Preserve `workItemId`-omitted behavior by returning an unscoped sentinel rather than an empty collection.
4. Add mapper overloads/helpers accepting resolved work-item IDs, keeping the existing common filter builder for page and summary. Early-return on empty descendants; otherwise use the same resolved ID collection for page, page total, total aggregate, and group aggregate. Preserve all four current `groupBy` values (`WorklogGroupByEnum.java:13-16`).
5. Keep `ProjectWorkItemRespVO` aggregate-only. No work-item write DTO/enum/converter field should be added. The current batch read aggregation should remain the source of truth.
6. Extend `docs/work-item-frontend-api.md` section 18 (starts at `:997`) with the exact shared body, default/invalid combinations, SELF/DESCENDANTS inclusion rules, empty descendant result, group behavior, and statement that aggregate fields are response-only. Its current wording already says the aggregate fields are read-only (`:999`) but only lists the old shared filter set (`:1014-1016`).

### Test locations and scenarios

- `controller/admin/worklog/vo/WorklogRequestVOTest.java` is the focused Bean Validation suite. Add page and summary inheritance cases for: valid omitted scope; defaultable root without scope; `SELF`/`DESCENDANTS` with root; invalid enum; scope without root; and unchanged date/group validation.
- `dal/mysql/worklog/WorklogMapperIntegrationTest.java` already creates a self-contained H2 MySQL-mode schema (`:44-81`) and verifies page filters, totals, all group dimensions, and batch work-item aggregates (`:84-155`). Extend its work-item table fixture with `parent_work_item_id`, then use root/child/grandchild worklogs to prove SELF, all-depth DESCENDANTS excluding root, date/category filters, fixed-order pagination total, total summary, and every group mode see the same ID set. Add an empty-child root and a dirty cycle fixture.
- The new descendant mapper query needs either a dedicated H2 execution test or an extension of the above manual MyBatis setup to parse `ProjectWorkItemMapper.xml`. The existing `ProjectWorkItemMapperTest` is useful for SQL/tenant rewrite assertions (`:87-99`, `:197-219`) but does not execute a hierarchy query.
- `service/workitem/ProjectWorkItemServiceImplTest.java` already mocks work-item and worklog mappers (`:51-75`) and tests detail/page response assembly (`:77-117`). Add expected `WorkItemWorklogSummaryResult` rows and assert `estimatedHours`/`actualHours`; then assert no persisted work-item write is attempted for aggregates.
- Add a DTO/quick-update regression in that service test or `ProjectWorkItemSaveReqVOTest.java`: reflection/serialization must show no legacy field on save/response DTOs, `ProjectWorkItemUpdateFieldEnum.of("estimatedWorkload")` must be null, and a quick update using it must throw `PROJECT_WORK_ITEM_FIELD_NOT_SUPPORTED` before mapper write. Test raw JSON only for the agreed unknown-property behavior; the product requirement only requires no effect.
- For CRUD freshness, extend `WorklogMapperIntegrationTest` with insert/update/logical-delete steps followed by `selectWorkItemSummaries` and `selectSummary`, or add an integration-level work-item service fixture. The existing unit test only verifies worklog writes/authorization (`WorklogServiceImplTest.java:69-119`), not subsequent aggregate reads.
- `WorklogControllerMethodSecurityTest.java:179-205` covers the project-level page/summary gate. It can confirm scope fields do not bypass that gate, but cannot represent a same-project per-child denial because the model has no such ACL.

## Files Found

- `yudao-module-project/yudao-module-project-server/src/main/java/.../controller/admin/workitem/vo/ProjectWorkItemSaveReqVO.java` - shared create/full-update body, already free of workload fields.
- `yudao-module-project/yudao-module-project-server/src/main/java/.../controller/admin/workitem/vo/ProjectWorkItemRespVO.java` - read DTO that exposes the two aggregate fields.
- `yudao-module-project/yudao-module-project-server/src/main/java/.../service/workitem/ProjectWorkItemServiceImpl.java` - batch aggregate assembly and writable-field allowlist enforcement.
- `yudao-module-project/yudao-module-project-server/src/main/java/.../controller/admin/worklog/vo/WorklogFilterReqVO.java` - shared page/summary request superclass and validation hook.
- `yudao-module-project/yudao-module-project-server/src/main/java/.../service/worklog/WorklogServiceImpl.java` - current page/summary orchestration and worklog CRUD.
- `yudao-module-project/yudao-module-project-server/src/main/java/.../dal/mysql/worklog/WorklogMapper.java` and `src/main/resources/mapper/worklog/WorklogMapper.xml` - common filtering, pagination, summary, grouping, and aggregate SQL.
- `yudao-module-project/yudao-module-project-server/src/main/java/.../dal/mysql/workitem/ProjectWorkItemMapper.java` - candidate owner for one-shot hierarchy lookup; no such method currently exists.
- `sql/mysql/project.sql` and `sql/mysql/project-worklog-incremental-20260728.sql` - independent worklog schema and legacy column-drop migration.
- `yudao-module-project/yudao-module-project-server/docs/work-item-frontend-api.md` - frontend API contract document to extend.
- `yudao-module-project/yudao-module-project-server/src/test/java/.../worklog/WorklogMapperIntegrationTest.java` - strongest existing H2 fixture for scope/filter/aggregate tests.

## External References

- Local runtime reference: `sql/tools/docker-compose.yaml:13-31` pins MySQL `8.0.33`, which supports `WITH RECURSIVE`; the existing mapper integration test runs H2 in MySQL mode (`WorklogMapperIntegrationTest.java:46-48`).
- MySQL 8.0 Reference Manual, "WITH (Common Table Expressions)": https://dev.mysql.com/doc/refman/8.0/en/with.html . Use only the `UNION`-deduplicated ID-set form if implementing cycle termination; do not carry an ever-changing depth/path column in that recursive set.

## Related Specs

- `.trellis/spec/backend/database-guidelines.md` - mapper-owned query logic, shared wrappers, tenant boundaries, and filtered page/aggregate consistency.
- `.trellis/spec/backend/project-rbac-guidelines.md` - project-level authorization boundary and SQL `EXISTS` filtering convention.
- `.trellis/spec/backend/quality-guidelines.md` - focused mapper/service/method-security tests.
- `.trellis/spec/guides/cross-layer-thinking-guide.md` - API/service/mapper/documentation contract alignment.

## Caveats / Not Found

- No existing recursive hierarchy resolver, closure table, or descendant-query abstraction was found in the project module.
- No per-work-item ACL data model or permission service method exists. The acceptance test phrase "unauthorized descendant" must be implemented with the current project-level boundary (or the task must explicitly expand authorization scope).
- Project work-item/worklog bootstrap SQL was found only under `sql/mysql`; framework-wide database support exists, so a raw recursive CTE should be validated against every deployed database dialect before asserting cross-engine support.
- The current `validateParentChain` loop is not safe for manually corrupted cycles. Do not use it in read-range resolution.
