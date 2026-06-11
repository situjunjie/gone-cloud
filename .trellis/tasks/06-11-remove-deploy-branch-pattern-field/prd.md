# Remove deploy branch pattern field

## Goal

Remove unused application-environment configuration fields from the backend API, persistence model, SQL schema, and tests. The deploy branch pattern and approval configuration fields are currently only stored and returned, but release and approval behavior do not use them.

## What I already know

* `ApplicationEnvDO.deployBranchNamePattern` maps to `dev_application_env.deploy_branch_name_pattern`.
* `ApplicationEnvDO.approvalRequired` and `approvalConfigJson` map to `dev_application_env.approval_required` and `approval_config_json`.
* The field is accepted by application environment save requests and returned by application environment / release environment APIs.
* Release branch selection and submission do not use the field for filtering, validation, or branch generation.
* Approval can be represented later as pipeline approval nodes instead of application-environment configuration.
* The generated deploy branch currently uses `release/{envKey}/{timestamp}`.
* The repository does not contain a frontend project, so frontend adaptation must be communicated as a prompt/note.

## Assumptions

* Removing the field from backend contracts is acceptable as a breaking API cleanup.
* Existing databases will be handled by the deployment/migration owner if needed; this task updates the tracked MySQL schema file.
* No compatibility shim is needed for old clients that still send `deployBranchNamePattern`.

## Requirements

* Remove `deployBranchNamePattern`, `approvalRequired`, and `approvalConfigJson` from backend DO and VO classes.
* Remove explicit SQL selects/updates for `deploy_branch_name_pattern`, `approval_required`, and `approval_config_json`.
* Remove `deploy_branch_name_pattern`, `approval_required`, and `approval_config_json` from the tracked MySQL schema.
* Update affected unit tests and helper builders.
* Provide a concise frontend adaptation prompt describing the removed field and expected UI/API changes.

## Acceptance Criteria

* [x] `rg "deployBranchNamePattern|deploy_branch_name_pattern|approvalRequired|approvalConfigJson|approval_required|approval_config_json"` finds no remaining production/schema references.
* [x] DevOps application service tests compile and pass, or any inability to run them is documented.
* [x] Frontend adaptation prompt is available in the final response.

## Definition of Done

* Tests updated for removed field.
* Relevant backend guidelines checked before implementation.
* No unrelated files changed.

## Out of Scope

* Implementing frontend changes in another repository.
* Adding a live database migration script beyond the existing tracked schema file unless the repo already has an established migration location for this module.

## Technical Notes

* Primary files: `ApplicationEnvDO`, application environment request/response VOs, `ApplicationEnvMapper`, `ApplicationServiceImplTest`, `sql/mysql/devops.sql`.
* Current search showed no pattern matching usage such as `Pattern`, glob, or `AntPathMatcher` tied to this field.
