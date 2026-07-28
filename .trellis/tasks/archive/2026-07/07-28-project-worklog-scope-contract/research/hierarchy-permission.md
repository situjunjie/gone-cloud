# Research: hierarchy-permission

- Query: Locate the work-item hierarchy representation, descendant traversal options, database/mapping conventions, and project/work-item permission boundaries for scoped worklog page and summary.
- Scope: internal, with local runtime version evidence
- Date: 2026-07-28

## Findings

### Hierarchy storage and current traversal

- `proj_work_item` is an adjacency-list tree. `parent_work_item_id` is nullable, and the baseline schema has an index on `(tenant_id, parent_work_item_id)`; there is no closure table or materialized path. See `sql/mysql/project.sql:125-157` and `ProjectWorkItemDO.java:19-75`.
- `proj_worklog` is an independent tenant-scoped row with both `project_id` and `work_item_id`; it is not stored on the work-item row. Relevant worklog indexes include `(tenant_id, work_item_id, type)` and project/date filter indexes. See `sql/mysql/project.sql:159-181` and `WorklogDO.java:14-30`.
- No existing descendant/ancestor/closure mapper was found in the project module or repository search. `ProjectWorkItemMapper` only has a direct-child count helper at `ProjectWorkItemMapper.java:262-264`.
- The only current hierarchy walk is an upward parent-chain validation loop. It repeatedly calls `selectById` at `ProjectWorkItemServiceImpl.java:506-516`, so it is N+1 by depth and has no visited-set guard. A pre-existing dirty cycle that does not reach `currentWorkItemId` can make this loop never terminate.
- Normal writes reject self-parenting, cross-project direct parents, and a cycle that reaches the currently edited item at `ProjectWorkItemServiceImpl.java:486-497`; this does not make reads safe against already-corrupt rows.

### Existing worklog query shape

- `WorklogFilterReqVO` is the common page/summary filter superclass. It currently applies `workItemId` directly as equality in `WorklogMapper.buildQueryWrapper` (`WorklogFilterReqVO.java:13-37`, `WorklogMapper.java:23-37`). A descendants implementation must not leave that equality active while also supplying a descendant set.
- Page currently invokes `WorklogMapper.selectPage(reqVO)`, which applies the shared wrapper then fixed `work_date DESC, id DESC` ordering (`WorklogMapper.java:40-43`). MyBatis-Plus owns both the count and records query.
- Summary builds the same wrapper once and supplies it to both total and group SQL (`WorklogServiceImpl.java:166-182`; `WorklogMapper.xml:36-49`). This is the correct consistency pattern to preserve.
- Summary SQL handles all four requested group dimensions (`WORK_ITEM`, `OWNER`, `CATEGORY`, `SPRINT`) through the XML group expression at `WorklogMapper.xml:25-49`. No group-specific scope path should be added.
- Page response enrichment is already batched: `getWorkItemMap` performs one `selectBatchIds` for page work-item ids (`WorklogServiceImpl.java:258-264`). Do not replace it with per-row work-item reads.

### Permission model and SQL expressibility

- Authorization is project-scoped, not work-item-scoped. `ProjectSecurityFrameworkService.hasWorkItemPermissions` reads one work item and delegates every requested permission to that item's `projectId` (`ProjectSecurityFrameworkService.java:64-77`). The test confirms this mapping (`ProjectSecurityFrameworkServiceTest.java:72-84`). There is no work-item ACL table or child-specific grant model.
- Page and summary require system `project:worklog:query` plus project `WORKLOG_QUERY` at the controller (`WorklogController.java:77-94`), and `WorklogServiceImpl.validateQuery` repeats the project existence/permission check (`WorklogServiceImpl.java:197-200`). `WORKLOG_QUERY` is independent from `WORK_ITEM_QUERY` in the permission catalog (`ProjectPermissionEnum.java:22-34`).
- Project visibility can be expressed in SQL. `ProjectWorkItemMapper.applyProjectPermissionFilter` emits an `EXISTS` join over member, member-role, role, and role-permission rows with tenant, deletion, admin, user, and permission checks (`ProjectWorkItemMapper.java:43-55`); its SQL shape is tested at `ProjectWorkItemMapperTest.java:87-99`.
- That helper must not be reused with `WORK_ITEM_QUERY` for worklog scope resolution: a caller can legitimately have `WORKLOG_QUERY` without `WORK_ITEM_QUERY`. If defense-in-depth SQL filtering is added, adapt the same EXISTS shape using `WORKLOG_QUERY` and the appropriate worklog/work-item alias.
- Under the current data model, once `WORKLOG_QUERY(projectId)` succeeds, every active work item in that same project is equally readable. The no-leak rule therefore requires the recursive query to pin tenant, requested project, and logical-delete state at every recursion step. This excludes a corrupt cross-project child edge; a true same-project child-level denial cannot be represented without a new ACL model.

### Mapper/database conventions and dialects

- The module uses `TenantBaseDO`, `BaseMapperX`, and `LambdaQueryWrapperX`; simple conditional filtering is in mapper default methods, while custom aggregates use XML (`ProjectWorkItemDO.java:24`, `WorklogMapper.java:20-43`, `WorklogMapper.xml:17-49`). This matches `.trellis/spec/backend/database-guidelines.md:1-82`.
- The local runtime pins MySQL 8.0.33 (`sql/tools/docker-compose.yaml:13-15`), which supports recursive CTEs. Existing mapper integration tests use H2 in MySQL mode (`WorklogMapperIntegrationTest.java:44-81`), so the chosen CTE must execute there as well.
- Repository search found no existing `WITH RECURSIVE` or `UNION DISTINCT` mapper usage. The project schema itself is currently present only in `sql/mysql`, so any raw recursive SQL remains unverified for the framework's other database engines.

### Reusable tests and fixtures

- `WorklogMapperIntegrationTest` is the strongest scope test target. It creates isolated H2/MySQL-mode worklog and work-item tables at `WorklogMapperIntegrationTest.java:44-81`, then already verifies filters, pagination total/order, total summary, all group dimensions, and batch aggregates at `:84-155`.
- Extend that fixture's `proj_work_item` table with `parent_work_item_id`, then seed root, child, grandchild, empty root, and a corrupt cycle. It can assert SELF, all-depth DESCENDANTS excluding root, empty descendants, filters, pagination `total`, total summary, each groupBy, and bounded cycle behavior against real SQL.
- `WorklogRequestVOTest` is the focused Bean Validation location (`WorklogRequestVOTest.java:18-85`). Add valid omitted scope/default-SELF/root cases, both legal enum values, invalid enum, and the invalid `workItemScope`-without-`workItemId` combination.
- `WorklogControllerMethodSecurityTest` already proves page/summary project-query gates (`WorklogControllerMethodSecurityTest.java:179-205`). It should prove new scope fields do not bypass that gate. A scoped-data permission test should use a corrupt cross-project child edge (or an SQL-level `WORKLOG_QUERY` predicate), because same-project per-child denial is not a supported model.
- `ProjectWorkItemMapperTest` contains the local tenant-rewrite precedent for XML SQL (`ProjectWorkItemMapperTest.java:197-241`); add an equivalent CTE/tenant-rewrite test if a raw XML recursive statement is introduced.

## Recommended Direction

1. Add a dedicated descendant-ID mapper method on `ProjectWorkItemMapper`, implemented in its XML with a recursive CTE, rather than a service loop. It should receive root id and requested project id, be bounded to active rows in that project and tenant, and return only descendant ids.
2. Use `UNION DISTINCT` (not `UNION ALL`) and keep the recursive CTE projection to stable identity columns only, such as `id` plus a stable tenant/project key. Do not include recursion depth in the CTE projection. Duplicate elimination then terminates arbitrary cycles. Explicitly exclude the root id in the recursive result, because a corrupt cycle can otherwise re-enter the root and violate DESCENDANTS semantics while still terminating.
3. Resolve the scope once per request after the existing `validateQuery(projectId)` call:
   - no `workItemId`: keep project-level wrapper unchanged and reject a supplied scope in DTO validation;
   - `SELF` or omitted scope: use exactly the root id, still constrained by the worklog `project_id` filter;
   - `DESCENDANTS`: use the one CTE result. If it is empty, short-circuit to empty page/zero summary/empty groups rather than relying on an empty `IN` wrapper behavior.
4. Introduce one shared scoped-wrapper path for page and summary. It must replace the current direct `eqIfPresent(workItemId)` condition with `IN (resolvedIds)` for scoped requests, then feed the exact same wrapper to page count/records and to total/groups. Add an overload of `selectPage` that accepts the prepared wrapper so its existing ordering and MyBatis-Plus count behavior are retained.
5. Keep authorization as `WORKLOG_QUERY`. The existing controller plus service check is the authoritative project permission boundary; the CTE must include project/tenant/deleted predicates. If a SQL authorization predicate is added, copy the existing EXISTS structure with `WORKLOG_QUERY`, never `WORK_ITEM_QUERY`.
6. In the same change, harden `validateParentChain` with a visited-id set (or replace it with the cycle-safe mapper strategy). The scoped CTE is cycle-safe, but the existing write-time loop remains independently vulnerable to dirty cycles.

## Suggested CTE Shape

Conceptually, the anchor joins the requested active root to its direct active children in the same project and tenant. The recursive member joins each candidate child to the prior CTE ids and repeats the same project, tenant, deleted, and `id != rootId` constraints. Join the two members with `UNION DISTINCT`, then select ids only. This ensures:

- root is not included in DESCENDANTS, even when corrupt data closes a loop back to root;
- cycles terminate because an already-seen identity cannot be re-added;
- cross-project or cross-tenant parent links do not enter the result;
- deleted work items do not contribute worklogs, totals, or groups.

For very large descendant sets, materializing ids into a Java `IN` list can hit parameter/SQL-size limits. The requested contract explicitly permits one CTE-derived id set, so the list approach is the least invasive fit for the current mapper design. If expected hierarchy breadth is unbounded, use the same CTE as a derived predicate inside custom page/count/summary SQL instead, and add pagination-interceptor tests before adopting it.

## Files Found

- `sql/mysql/project.sql` - authoritative MySQL table definitions and indexes for work items and worklogs.
- `ProjectWorkItemDO.java` - tenant-scoped work-item adjacency-list data object.
- `ProjectWorkItemMapper.java` / `ProjectWorkItemMapper.xml` - direct child helper and SQL project-permission filter precedent.
- `ProjectWorkItemServiceImpl.java` - parent validation and current unsafe upward traversal.
- `WorklogMapper.java` / `WorklogMapper.xml` - common filters, page ordering, shared total/group aggregation SQL.
- `WorklogServiceImpl.java` / `WorklogController.java` - query orchestration and dual controller/service permission checks.
- `ProjectSecurityFrameworkService.java` / `ProjectPermissionServiceImpl.java` - project-level permission resolution implementation.
- `WorklogMapperIntegrationTest.java`, `WorklogRequestVOTest.java`, `WorklogControllerMethodSecurityTest.java` - focused SQL, validation, and authorization test starting points.

## External References

- Local runtime version evidence: MySQL 8.0.33 at `sql/tools/docker-compose.yaml:13-15`.
- Official MySQL reference to validate the exact recursive CTE syntax during implementation: https://dev.mysql.com/doc/refman/8.0/en/with.html
- H2 is the local mapper-test engine in MySQL mode; no external H2 compatibility source was fetched. Execute the focused mapper test after adding the CTE rather than assuming MySQL syntax compatibility.

## Related Specs

- `.trellis/spec/backend/database-guidelines.md:1-82` - mapper/DO/query placement and MyBatis-Plus conventions.
- `.trellis/spec/backend/project-rbac-guidelines.md:352-422` - worklog entity, project authorization, shared filter, aggregation, and test contract.
- `.trellis/spec/backend/quality-guidelines.md:1-27` - service-vs-mapper placement and focused test selection.
- `.trellis/spec/backend/error-handling.md:1-60` - keep validation/business failures in standard module error handling and `CommonResult` response shape.

## Caveats / Not Found

- No closure table, recursive CTE, or reusable descendant resolver exists today.
- There is no child-specific authorization domain. A requirement for different visibility among work items in one authorized project needs a new persisted ACL and cannot be fulfilled solely by changing worklog queries.
- The requirement does not define whether a nonexistent/root-from-another-project `workItemId` should be rejected or yield the safely empty project-filtered result. Preserve non-disclosure by resolving authorization on `projectId` first and do not use `hasWorkItemPermission(..., WORK_ITEM_QUERY)` as an accidental stronger gate.
- Current schema has only `(tenant_id, parent_work_item_id)`, not a composite `(tenant_id, project_id, parent_work_item_id, deleted)` traversal index. Profile a representative hierarchy before adding an index migration; add one only if the CTE plan demonstrates a real need.
