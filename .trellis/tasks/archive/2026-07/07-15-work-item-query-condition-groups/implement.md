# Implementation Plan

## 1. Query Contracts

- [x] Add `ConditionGroup` to `ProjectWorkItemPageReqVO` and expose `conditionGroups`.
- [x] Add the equivalent persisted condition-group structure to `WorkItemQueryViewDO.QueryCondition`.
- [x] Preserve the existing flat `conditions` contract and document mutual exclusivity.

## 2. Shared Validation

- [x] Extract reusable condition validation/normalization from `ProjectWorkItemServiceImpl`.
- [x] Validate non-empty groups, mutual exclusivity, and the 20-condition total limit.
- [x] Reuse existing field, operator, value, current-user token, custom-field scope, and option validation.
- [x] Ensure query-view validation operates on a temporary copy and does not mutate persisted tokens.

## 3. Grouped SQL

- [x] Extend `ProjectWorkItemMapper` to apply grouped conditions inside one top-level `AND` block.
- [x] Combine conditions inside each group with `AND` and groups with `OR`.
- [x] Reuse existing scalar and custom-field condition builders with bound parameters.
- [x] Keep flat `conditions` behavior unchanged when no groups are supplied.

## 4. Query View Integration

- [x] Validate query conditions during query-view create and update.
- [x] Persist and return `conditionGroups` through the existing JSON column and response conversion.
- [x] Verify old query-view JSON containing only flat `conditions` still deserializes and replays.

## 5. Tests And Verification

- [x] Add service tests for valid groups, invalid structure, total limit, and field boundaries.
- [x] Add Mapper tests for `(A AND C) OR (A AND B)` SQL and top-level fixed-filter AND semantics.
- [x] Add query-view tests for validation, round-trip structure, legacy JSON, and preserved user tokens.
- [x] Run focused project work-item and query-view tests.
- [x] Run `mvn -pl yudao-module-project/yudao-module-project-server -am -DskipTests compile`.
- [x] Run `mvn -pl yudao-module-project/yudao-module-project-server -am test`.
- [x] Run `git diff --check` and review the final task-scoped diff.

## Verification Result

- Grouped-query focused suite: 15 passed before final additions.
- Project server full suite: 30 passed, 0 failures, Reactor `BUILD SUCCESS`.
- Final condition-service suite: 10 passed, including execution-time user-token resolution and JSON round-trip.
- Affected-module compile, `git diff --check`, and Trellis task validation passed.

## Risk And Rollback Points

- Stop after contract changes if old flat requests or old view JSON no longer deserialize.
- Stop after shared validation extraction if existing flat-condition tests change behavior.
- Stop after Mapper grouping if generated SQL lacks explicit group parentheses or changes custom-field parameter binding.
- Roll back Java changes together; no schema rollback is required.
