# Implementation Plan

1. Read backend specs and inspect existing application and user-favorite mapper patterns.
2. Add nullable `favori` request field and wire current user/tenant context into the page query contract.
3. Implement database-side favorite and non-favorite predicates while preserving existing filters and ordering.
4. Add focused unit/integration coverage for `true`, `false`, and omitted values, including tenant/user scoping.
5. Run module tests and compile checks; review generated API binding and SQL behavior.
6. Run Trellis quality check, update specs if a reusable convention is discovered, then commit and archive the task.
