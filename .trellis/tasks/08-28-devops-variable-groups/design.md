# Technical Design

## Boundaries

- New `variablegroup` domain under DevOps server: DO, mapper, service, parser, converter, admin VOs/controller.
- Add `dev_variable_group` to `sql/mysql/devops.sql`; entity extends `TenantBaseDO` and uses logical delete.
- Add a small runtime provider consumed by `PipelineVariableResolver`/`PipelineExecutionEngine` to load effective variables once per run context. Keep handlers unchanged; they already consume resolved `with` values and command env maps.

## Data Model

`scope` enum values: `GLOBAL`, `APPLICATION`, `APPLICATION_ENV`. `app_id` is required only for `APPLICATION`; `application_env_id` is required only for `APPLICATION_ENV`. Store source in `variables text`. Unique active name key is tenant + scope + nullable scope key; because MySQL NULL uniqueness is unsafe, use zero sentinel for unused foreign keys and validate scope/keys in service. Add indexes for tenant/scope/status and application/application-env lookup.

## Contracts

- CRUD: `/devops/variable-group/create`, `/update`, `/delete`, `/{id}`, `/page`; permissions `devops:variable-group:*`.
- Runtime service: `Map<String,String> getEffectiveVariables(Long appId, Long applicationEnvId)`, returning deterministic merge global then application then application-env, only enabled groups. Within a scope, groups are ordered by id and later entries override earlier keys; service rejects duplicate names but permits key override across groups.
- Text parser trims surrounding whitespace on keys, preserves value whitespace after the first `=`, ignores blank lines, accepts CRLF, and rejects malformed/duplicate keys with stable error codes.
- Reserved runtime keys (run/stage/job metadata and output aliases) are inserted after group variables so system values cannot be shadowed. Existing camelCase/env-style aliasing remains in `PipelineVariableResolver`.

## Runtime Flow

`PipelineExecutionEngine.buildRunContext` obtains app/application-env ids from `PipelineRunDO`, asks provider for effective variables, and stores an immutable map in context/shared state. `PipelineVariableResolver.buildVariables` starts from that map, then adds existing run metadata/output variables. No variable values are logged or serialized into run context/result JSON.

## Compatibility / Rollout

- Existing pipelines without variable groups behave identically (empty provider result).
- Migration is additive; deploy SQL before server version. Rollback removes runtime provider wiring and table only after groups are no longer used.
- No frontend or secret-management migration in this iteration.

## Tests

Parser tests, service scope/ownership/uniqueness tests, controller validation tests, runtime precedence and reserved-key tests, plus existing pipeline resolver/engine regression tests.
