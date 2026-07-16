# Implementation Plan

## 1. Persistence And Contract

- [x] Add `WorkItemFieldScopeEnum` and persist `scope` on `WorkItemFieldDO`.
- [x] Add `scope` to field response VO while preserving existing project-field request compatibility.
- [x] Update MySQL bootstrap DDL and add an incremental migration for existing `PROJECT` rows and indexes.
- [x] Add the `project:work-item-field:global-manage` menu permission.

## 2. Field Management

- [x] Extend `WorkItemFieldMapper` with global-code, any-project-code, and effective-field-list queries.
- [x] Refactor `WorkItemFieldServiceImpl` around explicit expected-scope validation.
- [x] Keep existing write endpoints project-only and add separately authorized global write endpoints.
- [x] Enforce immutable scope/project/code/type and cross-scope code conflicts.
- [x] Return merged global and project field metadata with stable ordering.

## 3. Work Item Integration

- [x] Accept global fields in create, full update, quick update, detail, and page response paths.
- [x] Merge global and project definitions for required-field validation.
- [x] Preserve delayed backfill semantics for existing work items.
- [x] Accept global fields and reject foreign project fields in dynamic query normalization.

## 4. Tests And Verification

- [x] Add focused field-service tests for scope, conflicts, permissions boundary helpers, and merged listing.
- [x] Extend custom-field-value tests for global writes and merged required validation.
- [x] Extend query/service tests for global custom-field filters.
- [x] Run focused work-item tests.
- [x] Run `mvn -pl yudao-module-project/yudao-module-project-server -am -DskipTests compile`.
- [x] Review the final diff for SQL/API compatibility and verify the worktree contains only task-scoped changes.

## Verification Result

- `WorkItemFieldMapperTest`: 1 passed.
- Project server test suite: 20 passed, 0 failures, Reactor `BUILD SUCCESS`.
- Affected-module compile: Reactor `BUILD SUCCESS`.
- `git diff --check` and Trellis task validation passed.

## Risk And Rollback Points

- Stop after DDL changes if existing-data migration cannot preserve all field ids and project bindings.
- Stop after service refactoring if project-only callers no longer behave identically.
- Roll back Java and both MySQL SQL changes together before any global rows are written.
