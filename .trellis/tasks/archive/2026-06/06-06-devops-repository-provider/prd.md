# devops repository provider

## Goal

Add a DevOps repository provider aggregate for GitLab code-source connections. The feature must provide admin CRUD for `devops_repository_provider`, persist its MySQL schema in `sql/mysql/devops.sql`, and add access-token based GitLab integration primitives for connection validation and basic repository listing.

## What I Already Know

* The user wants the code-source entity named `devops_repository_provider`.
* SQL for the DevOps module must be maintained in `sql/mysql/devops.sql`.
* The provider represents a GitHub/GitLab platform connection, while later concrete code repositories can belong to a provider and be associated with applications.
* Existing DevOps module code uses `TenantBaseDO`, admin controllers, VO classes, MapStruct converters, `BaseMapperX`, module-level error codes, and `CommonResult`.

## Requirements

* Create `devops_repository_provider` table in `sql/mysql/devops.sql`.
* Implement admin CRUD endpoints for repository providers.
* Support paginated query by provider name/type/status and creation time.
* Store GitLab access-token credentials as provider configuration fields.
* Do not expose raw access tokens in response payloads.
* Add GitLab access-token integration using `org.gitlab4j:gitlab4j-api`.
* Add service-level connection validation against GitLab APIs.
* Keep the implementation scoped to repository providers and provider API access primitives; do not implement the concrete repository entity or application association in this task.

## Acceptance Criteria

* [ ] `devops_repository_provider` schema is present in `sql/mysql/devops.sql`.
* [ ] Backend compiles for `yudao-module-devops`.
* [ ] Admin endpoints support create, update, delete, get, page, and connection check.
* [ ] GitLab access token integration can query the authenticated user and projects.
* [ ] Raw access tokens are not returned by normal response VOs.

## Definition of Done

* Module compile/check passes or any blocker is reported.
* Code follows existing DevOps module patterns.
* New module error codes are added for provider validation failures.
* No unrelated files are changed.

## Out of Scope

* Concrete `devops_repository` entity.
* Application-to-repository association.
* GitHub integration.
* OAuth/GitHub App/GitLab Project Token flows.
* Token encryption framework beyond keeping token out of response payloads.
* Full repository file/branch/commit management UI APIs.

## Research References

* [`research/git-provider-access-token.md`](research/git-provider-access-token.md) - official GitHub/GitLab access-token authentication and initial API endpoints.
