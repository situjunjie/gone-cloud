# 制品仓库 Nexus Maven 搜索 - Implementation Plan

## Checklist

1. Read backend implementation specs before coding:
   - `.trellis/spec/backend/directory-structure.md`
   - `.trellis/spec/backend/database-guidelines.md`
   - `.trellis/spec/backend/error-handling.md`
   - `.trellis/spec/backend/logging-guidelines.md`
   - `.trellis/spec/backend/quality-guidelines.md`
   - `.trellis/spec/backend/devops-repository-guidelines.md`
   - shared guides index and relevant reuse/cross-layer guides.
2. Add enums and errors:
   - `ArtifactRegistryProviderTypeEnum`
   - `ArtifactRegistryAuthTypeEnum`
   - `ArtifactRepositoryFormatEnum`
   - `ArtifactRepositoryTypeEnum`
   - error codes under `1-011-011-000`.
3. Add DO and mapper classes:
   - `ArtifactRegistryDO`
   - `ArtifactRepositoryDO`
   - `ArtifactRegistryMapper`
   - `ArtifactRepositoryMapper`
4. Add SQL:
   - tables in `sql/mysql/devops.sql`
   - menu and permission bootstrap in `sql/mysql/devops-menu.sql`
   - dictionary rows only if the module requires them for enum display.
5. Add VO/DTO/Convert:
   - registry CRUD/page/check VOs
   - repository response VOs
   - Maven search request/response VOs
   - MapStruct convert interface.
6. Add Nexus client layer:
   - request URL building and Basic Auth header
   - repository list parsing
   - Maven search response parsing and standardization
   - message truncation and secret-safe logging.
7. Add service layer:
   - uniqueness validation
   - provider/auth validation
   - password preserve-on-update behavior
   - delete blocking when repositories exist
   - repository sync upsert
   - Maven search orchestration.
8. Add controller:
   - `/devops/artifact-registry/**`
   - permission annotations
   - OpenAPI annotations consistent with existing controllers.
9. Add focused tests:
   - create/update password behavior
   - duplicate name validation
   - delete blocked by repository records
   - repository sync filters Maven/Docker only
   - search delegates to client and maps result.
10. Run verification:
   - `mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dsurefire.failIfNoSpecifiedTests=false test`
   - If focused tests are added, optionally narrow with `-Dtest=...`.

## Review Gates

- Do not log password or Basic Auth header.
- Do not persist search results in this task.
- Do not add npm-specific behavior beyond enums/model extensibility.
- Keep Controller thin and business rules in Service.
- Keep Nexus API quirks inside the client layer.

## Rollback Points

- SQL additions are isolated to new tables/menu rows and can be reverted without touching existing DevOps tables.
- Java additions are under new `artifactregistry` packages and new enums/errors.
- Existing code should only receive imports or menu/error additions; avoid modifying application/pipeline behavior.
