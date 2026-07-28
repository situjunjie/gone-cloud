# 工作项工时范围契约实施计划

## Preconditions

- 用户审阅本任务最新规划摘要并明确批准后，才执行 `task.py start`。
- Phase 2 开始前加载 `trellis-before-dev`，并阅读 backend、database、error-handling、quality、project-rbac、cross-layer 和 code-reuse 规范。
- 实现与检查代理使用本任务的 `implement.jsonl` / `check.jsonl` 及研究文件作为上下文；不要覆盖其他工作树的改动。

## Ordered Implementation

1. Contract and validation
   - Add the server-controlled `SELF` / `DESCENDANTS` scope enum.
   - Add `workItemScope` to shared page/summary filter DTO; validate enum and the forbidden no-root combination.
   - Keep default-`SELF` resolution in Service, not as an unconditional DTO default.

2. Hierarchy resolver
   - Add a one-shot recursive CTE descendant-ID mapper query under `ProjectWorkItemMapper` XML.
   - Pin tenant/project/deletion predicates and use an ID-only `UNION` set with root exclusion.
   - Harden the existing parent-chain write validation with a visited set against dirty cycles.

3. Scoped query pipeline
   - Resolve the root/range once in `WorklogServiceImpl` after existing project authorization, preserving empty results for a nonexistent or foreign root rather than exposing it through a new error.
   - Refactor `WorklogMapper` to build the shared wrapper from an explicit resolved range and to page from the prepared wrapper.
   - Explicitly short-circuit empty `DESCENDANTS` for page and summary.
   - Preserve existing ordering, all filters, four groups, batched response enrichment, and `{ code, data, msg }` controller shape.

4. Materialized work-item aggregates
   - Add non-null `estimated_hours` / `actual_hours` to baseline `proj_work_item` DDL, `ProjectWorkItemDO`, and a dated idempotent migration that backfills from undeleted worklogs.
   - Change work-item get/page conversion to map the persisted counters directly; remove the now-redundant page-level worklog aggregation query and its unused result path.
   - In `WorklogServiceImpl`, use a single transactional counter-maintenance helper: create adds hours, update locks then applies the hour delta, delete locks then subtracts hours.
   - Use parent-work-item-first row locking and atomic mapper counter updates so concurrent mutations cannot use stale deltas; preserve immutable work-item/type semantics.
   - Keep range/filter summary aggregation against `proj_worklog`, because the materialized totals cannot express request-specific filters.
   - Verify no work-item request/write path gains aggregate or legacy fields.

5. Documentation
   - Update `docs/work-item-frontend-api.md` section 18 with shared scope body, defaults, invalid combinations, group behavior, zero/empty behavior, and readonly materialized aggregate fields.
   - Correct only directly related stale wording if it conflicts with the live worklog contract.

6. Focused tests
   - Extend request DTO validation tests.
   - Extend mapper integration tests and/or add a hierarchy mapper execution test covering all user-mandated scenarios.
   - Extend service/security/work-item tests for permissions, stale/legacy input, counter deltas, direct read mapping, migration/backfill, and dynamic-summary consistency.

7. Quality gates
   - Run focused tests first, then project module reactor tests, compile/package, whitespace diff check, and source/doc residual searches.
   - Dispatch `trellis-check` for an independent contract/quality review before reporting completion.

## Expected Files

- `.../controller/admin/worklog/vo/WorklogFilterReqVO.java`
- `.../enums/WorklogWorkItemScopeEnum.java`
- `.../service/worklog/WorklogServiceImpl.java`
- `.../dal/mysql/worklog/WorklogMapper.java`
- `.../dal/mysql/workitem/ProjectWorkItemMapper.java`
- `.../resources/mapper/workitem/ProjectWorkItemMapper.xml`
- `sql/mysql/project.sql` and a new dated project-worklog aggregate incremental migration
- `.../service/workitem/ProjectWorkItemServiceImpl.java`
- focused worklog/work-item request, mapper, service, and security tests
- `.../docs/work-item-frontend-api.md`

## Validation Commands

```bash
mvn -pl yudao-module-project/yudao-module-project-server -am \
  -Dtest='*Worklog*Test,ProjectWorkItemServiceImplTest,ProjectWorkItemMapperTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -pl yudao-module-project/yudao-module-project-server -am test

mvn -pl yudao-server -am -DskipTests package

git diff --check

rg -n 'estimatedWorkload|estimated_workload' yudao-module-project sql/mysql \
  --glob '!**/target/**'
```

## Review Gates

- [ ] Page and summary share a resolved range and all filters.
- [ ] Empty descendants cannot become an unfiltered query.
- [ ] CTE terminates on a dirty cycle and excludes the root.
- [ ] Cross-project/cross-tenant/deleted work items cannot enter the range.
- [ ] Existing project-level authorization remains intact; no accidental `WORK_ITEM_QUERY` regression.
- [ ] Work-item aggregate fields are read-only, persisted, backfilled, and transactionally maintained from worklogs.
- [ ] Filtered/ranged worklog summary remains dynamically aggregated from worklogs rather than stale counters.
- [ ] Required tests and documentation precisely match the public contract.
