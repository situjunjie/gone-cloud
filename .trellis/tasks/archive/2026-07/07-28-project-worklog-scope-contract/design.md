# 工作项工时范围契约设计

## Architecture

本次变更位于现有 `project` 模块内，不新增工时表或授权模型，但为 `proj_work_item` / `ProjectWorkItemDO` 增加两列物化合计。请求仍由 `WorklogController` 暴露在 `/admin-api/project/worklog` 下，使用 `CommonResult` 标准封装；Service 负责范围解析、工时写事务与业务校验，Mapper 负责一次性递归查询、原子计数更新和统一工时筛选/聚合。

```text
page / summary request
  -> Bean Validation: scope enum + scope requires workItemId
  -> projectId + WORKLOG_QUERY validation
  -> resolve root and range once
       no root        => project-wide (no range predicate)
       SELF/default   => [root]
       DESCENDANTS    => recursive CTE -> descendant IDs
  -> shared scoped worklog wrapper
       page: count + ordered records
       summary: totals + optional groups
```

The resolved range is applied before the existing sprint/type/category/owner/date filters, ordering, pagination, total, total aggregate, and group aggregate. The page endpoint continues to enrich work-item labels in one batch.

## HTTP Contract

Both `WorklogPageReqVO` and `WorklogSummaryReqVO` inherit these additions from `WorklogFilterReqVO`:

| Field | Type | Rules |
|---|---|---|
| `workItemId` | `Long?` | Optional. Omitted means existing project-level semantics. Present roots a scoped query. A nonexistent or other-project ID resolves to no matching records, preserving the previous project-plus-work-item filter behavior without disclosing the foreign root. |
| `workItemScope` | `String?` | Only `SELF` / `DESCENDANTS`. It is invalid when `workItemId` is absent. When omitted with a root, Service interprets it as `SELF`. |

`SELF` produces only the root ID. `DESCENDANTS` produces all recursive, active descendants and excludes the root. No descendants yields a `PageResult` with `list=[]`, `total=0`, and a summary with `estimatedHours=0.00`, `actualHours=0.00`, `groups=[]` even if `groupBy` was supplied.

The request continues to require `projectId`; all other filter and `groupBy` contracts remain unchanged. `groupBy` retains `WORK_ITEM`, `OWNER`, `CATEGORY`, and `SPRINT`.

## Range Resolution And Safety

`ProjectWorkItemMapper` receives a dedicated XML-backed descendant-ID query. The recursive CTE anchors at the requested root and traverses `parent_work_item_id` with all of these predicates in both anchor/recursive members:

- same tenant;
- same requested project;
- `deleted = 0`;
- stable projection containing only identity/key columns.

It uses `UNION` rather than `UNION ALL`, which deduplicates IDs and terminates on dirty cycles. The final result excludes the root ID defensively, including when a corrupt cycle points back to it. This is a single hierarchy query, not a per-node mapper loop.

The Service first validates `projectId` and existing `WORKLOG_QUERY` permission. For `SELF`, the requested root ID becomes the scoped set and the existing worklog project predicate preserves empty results for a foreign/nonexistent root. For `DESCENDANTS`, the CTE anchor requires an active root in the requested tenant/project, so a foreign/nonexistent root produces an empty set. Current RBAC grants are project scoped, so a caller with `WORKLOG_QUERY` has the same visibility for all active items in that project. The CTE's tenant/project/deletion predicates are the defense against corrupt cross-tenant/cross-project hierarchy edges. No new `WORK_ITEM_QUERY` requirement or per-item ACL is introduced.

## Unified Data Path

Introduce a resolved-range value that distinguishes:

- unscoped project query;
- non-empty scoped ID set;
- empty descendants.

`WorklogMapper.buildQueryWrapper` accepts that resolved set or a similarly explicit scope argument. It must not retain `eqIfPresent(workItemId)` when a scoped ID set is supplied. A prepared-wrapper page overload retains the existing `work_date DESC, id DESC` ordering and lets MyBatis-Plus generate the matching count query. The same prepared wrapper feeds `selectSummary` and `selectGroupSummary`.

Empty descendant ranges are handled in Service before constructing an `IN` predicate. This prevents an empty collection from degrading into an unrestricted project query and makes the required zero response deterministic.

## DTO And Materialized Aggregate Model

`ProjectWorkItemRespVO` remains the read-only surface for `estimatedHours` and `actualHours`. `ProjectWorkItemSaveReqVO` and generic field-update allowlists remain free of `estimatedWorkload`, `estimatedHours`, and `actualHours`. `ProjectWorkItemDO` gains only the two internally maintained read-model fields; MapStruct maps them to the response, never from a request.

`proj_worklog` remains the source of truth. `proj_work_item.estimated_hours` and `actual_hours` are non-null `DECIMAL(20,2)` materialized aggregates used by work-item detail/page reads. The wider precision is required because a valid work item's total can exceed the maximum value allowed for one `DECIMAL(10,2)` worklog row. The current page-level batch aggregate query is removed: work-item DTO conversion reads the persisted counters directly.

All counter maintenance happens in the same transaction as the worklog mutation:

| Mutation | Worklog action | Counter action |
|---|---|---|
| Create | Insert immutable `workItemId` / `type` log | Atomically add `hours` to the matching counter. |
| Update | Lock current row, then update allowed fields | Atomically add `newHours - oldHours` to the matching counter. |
| Delete | Lock current row, then logical delete it | Atomically subtract its `hours` from the matching counter. |
| Work-item delete | Logical-delete child worklogs and the work item in its existing transaction | No counter update is needed because the parent record becomes invisible. |

For update/delete, the Service locks the parent work item before locking the worklog row and calculating the delta. Create and work-item delete use the same parent-first lock order. The mapper performs an atomic `column = column + delta` update after the lock, preventing lost updates and stale-delta races. Worklog type and target work item remain immutable, so no cross-counter transfer is needed.

Both baseline DDL and a new idempotent incremental migration add the columns with `0.00` defaults. The migration then derives each active work item's two counters from undeleted `proj_worklog` rows joined by tenant and work-item ID. It makes existing data correct before new delta writes begin.

Summary remains a dynamic aggregate over `proj_worklog`, because a cached total cannot honor arbitrary range, category, owner, date, sprint, and scope filters. Regression tests will prove stored counters and dynamic summaries agree after create/update/delete, and legacy/aggregate request input cannot affect work-item data.

## Tests

1. DTO validation tests prove legal/default range cases, bad enum, and scope-without-root rejection for both page and summary inheritance.
2. H2/MySQL-mode mapper tests seed root, child, grandchild, empty root, and a dirty cycle; they verify `SELF`, arbitrary-depth `DESCENDANTS`, root exclusion, no-child zero results, filters, ordered page/total, total summary, and all four group modes share the ID set.
3. Security tests prove scope does not bypass system/project `WORKLOG_QUERY`, and corrupt cross-project descendants do not enter the CTE range. This is the strongest representable descendant-visibility assertion under existing project-scoped RBAC.
4. Work-item/service/mapper tests prove persisted counters are directly mapped, create/update/delete apply correct transactional deltas, migration-style backfill is correct, writes ignore/reject legacy and aggregate fields, and dynamic summaries agree after mutations.

## Compatibility, Rollout, And Rollback

- Existing project-level page/summary bodies without `workItemId` remain behaviorally unchanged.
- Existing root-only requests without `workItemScope` become explicitly `SELF`, preserving their current equality filter behavior.
- Baseline DDL and a dated idempotent migration add `estimated_hours` / `actual_hours` and backfill them from worklogs. This is the only data migration in this task.
- Rollback must retain the added columns or reverse them only after reverting the readers. Older code continues to work with extra columns and will dynamically aggregate them; a later reconciliation can rebuild values from `proj_worklog` if needed.
- MySQL 8 is the deployed target; H2 compatibility is validated in the mapper suite before release.

## Risks

- Recursive CTE syntax or tenant filtering may behave differently under the test dialect. Focused H2 execution and MyBatis tenant-rewrite tests are release gates.
- A very broad hierarchy produces a large `IN` list. The first implementation follows the requested one-shot ID resolution and current wrapper architecture; profile before adding complexity such as derived-CTE page/summary SQL.
- The pre-existing write-time parent-chain loop is unsafe for arbitrary dirty cycles. Harden it with a visited ID set in this change so the new range feature does not leave the known same-class failure path untouched.
- Materialized counters can drift only if a write path bypasses `WorklogService` or migration/backfill fails. All supported mutations must share the transactional delta helper; tests and a rerunnable SQL backfill provide the repair path.
