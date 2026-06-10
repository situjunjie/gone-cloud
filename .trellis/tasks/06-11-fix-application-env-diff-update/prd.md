# Fix Application Env Differential Update

## Goal

Change application environment update from delete-and-reinsert to differential update so existing application-env relation IDs are preserved and logical deletes do not cause unique-key collisions on `dev_application_env.uk_tenant_app_env`.

## What I Already Know

* The failing endpoint is `PUT /admin-api/devops/application/update-envs`.
* The current service deletes all rows for `appId`, then inserts the requested env list.
* `ApplicationEnvDO` extends `TenantBaseDO`, which extends `BaseDO` with `@TableLogic deleted`.
* The database unique key is `(tenant_id, app_id, env_id)`, so logically deleted rows still occupy the unique key.
* The observed error is `Duplicate entry '1-2-1' for key 'dev_application_env.uk_tenant_app_env'`.

## Requirements

* Existing requested environment relations must be updated in place by `envId`, preserving `dev_application_env.id`.
* Newly requested environment relations must be inserted.
* Relations no longer present in the request must be logically deleted.
* Keep existing validation behavior for app existence, env existence, and duplicate envs in the request.
* Avoid broad refactors outside the application env update path.

## Acceptance Criteria

* Re-submitting an app env already associated with the application no longer attempts to insert a duplicate `(tenant_id, app_id, env_id)`.
* Updating fields such as display order, branch pattern, pipeline definition, approval config, status, and remark changes the existing relation row.
* Adding a new env inserts one relation row.
* Removing an env logically deletes that relation row.
* Focused tests or compile verification cover the changed service behavior.

## Out of Scope

* Changing the table unique key.
* Restoring previously logically deleted relations with the same `(tenant_id, app_id, env_id)` if they are not returned by normal MyBatis-Plus queries.
* Changing release, deployment, pipeline, or change-env history behavior.

## Technical Notes

* Service: `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/service/application/ApplicationServiceImpl.java`
* Mapper: `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/dal/mysql/application/ApplicationEnvMapper.java`
* Table DDL: `sql/mysql/devops.sql`
