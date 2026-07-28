Active task: .trellis/tasks/07-28-project-worklog-scope-contract

You are already the `trellis-implement` sub-agent. Implement the reviewed task directly; do not spawn another `trellis-implement` or `trellis-check` agent.

You are not alone in the codebase. Preserve all existing/user changes, do not revert unrelated edits, and adapt to any concurrent changes you observe. You own the approved implementation across the project worklog/work-item contract and its focused tests/docs; do not edit task planning artifacts, commit, push, or reset git state.

Implement the latest `prd.md`, `design.md`, and `implement.md`, including these non-negotiable points:

1. Add the shared `workItemScope` contract (`SELF` / `DESCENDANTS`) to page and summary, validate illegal scope-without-root input, default a supplied root without scope to SELF, and preserve unscoped project queries.
2. Resolve descendants in one cycle-safe recursive CTE constrained by tenant/project/deleted state, excluding the root and avoiding per-node queries. Do not accidentally require `WORK_ITEM_QUERY`; retain project-level `WORKLOG_QUERY` semantics.
3. Ensure the resolved range is applied before all filters, count/page/order, total, and every existing groupBy. Explicitly short-circuit empty descendants.
4. Materialize `estimated_hours` / `actual_hours` in `proj_work_item` and `ProjectWorkItemDO`. Worklog create/update/delete must transactionally maintain them using parent-first locking and atomic deltas. Worklogs remain the source of truth for filtered/ranged summaries. Add baseline DDL plus an idempotent dated migration that backfills from undeleted worklogs.
5. Work-item read DTOs must map the persisted aggregate fields directly. Work-item write DTOs and quick-update allowlist must continue to exclude `estimatedWorkload`, `estimatedHours`, and `actualHours`; remove obsolete dynamic batch aggregate plumbing if no longer used.
6. Add/update focused tests for scope, filter/page/summary consistency, no descendants, cycle termination, corrupted cross-project leakage prevention/project permission gates, materialized counter lifecycle, readonly/legacy field protection, and documentation.

Run the focused Maven tests and compile checks feasible in the environment. Report exact modified files, verification results, remaining failures, and any assumptions through this channel.
