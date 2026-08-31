# Technical Design

## Boundaries

- Add nullable `Boolean favori` to `ApplicationPageReqVO` so query binding distinguishes omitted from `false`.
- Resolve current user and tenant IDs at the service/controller boundary using the existing security and tenant utilities.
- Extend application page query to filter against `system_user_favorite` for business type `DEVOPS_APPLICATION` and `biz_id = application.id`.

## Data Flow

`GET /devops/application/page?favori=...` -> request VO -> application service/mapper -> SQL predicate using current tenant/user favorite rows -> paged applications.

The favorite predicate should be expressed as an `EXISTS`/`NOT EXISTS` condition (or equivalent mapper query) so pagination and total counts are calculated by the database. Soft-deleted favorite rows must not count as active favorites, matching the existing favorite mapper behavior.

## Compatibility

- Existing callers that omit `favori` retain current SQL and results.
- No schema migration is needed.
- Favorite business type remains the existing literal `DEVOPS_APPLICATION`.

## Trade-offs

Passing user and tenant context into the mapper keeps filtering in SQL and avoids loading all favorites into memory. The query must remain tenant-scoped to prevent cross-tenant leakage.
