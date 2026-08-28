# Implementation Plan

1. Add scope enum, variable-group DO, mapper, SQL schema, error codes, request/response/page VOs, converter, service implementation, parser, and admin controller with permissions.
2. Implement scope ownership validation against ApplicationMapper and ApplicationEnvMapper; enforce active-name uniqueness and logical delete/update behavior.
3. Add runtime provider and wire it into pipeline run context; merge variables with global/application/application-env precedence and protect reserved runtime keys.
4. Add focused unit tests for parser, service, controller contracts, provider precedence, and pipeline resolver integration.
5. Run `mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dtest='*VariableGroup*Test,*VariableResolverTest,*PipelineExecutionEngineTest' -Dsurefire.failIfNoSpecifiedTests=false test`, then `git diff --check` and secret/log residual search.

Risk points: SQL unique-key sentinel semantics, application-env ownership checks, and accidental persistence/logging of merged values. Rollback point is before runtime wiring; CRUD/schema can remain additive independently.
