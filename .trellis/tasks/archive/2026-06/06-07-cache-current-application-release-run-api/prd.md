# Cache current application release run API

## Goal

Add caching for `/admin-api/devops/application/release/current-run` so repeated polling of the current release run avoids rebuilding the same response and repeatedly querying pipeline definition, current run, and code merge log data.

## What I already know

* The endpoint is `ApplicationController#getApplicationReleaseCurrentRun`.
* The service method is `ApplicationServiceImpl#getApplicationReleaseCurrentRun(Long applicationEnvId)`.
* The method builds `ApplicationReleaseCurrentRunRespVO` from application env validation, published pipeline definition/spec, current pipeline run, and code merge log.
* The user explicitly prefers declarative Spring cache annotations: `@Cacheable`, `@CachePut`, and `@CacheEvict`; programmatic cache should be used only if the invalidation path is too complex.
* The project cache manager supports per-cache TTL by using cache names in the `key#ttl` format.

## Assumptions

* Cache key should be `applicationEnvId`, since the endpoint request is scoped by that id.
* A short TTL is acceptable for a polling endpoint as a safety net against missed invalidations.
* Null results do not need caching because validation failures throw service exceptions before a response is returned.

## Requirements

* Add a DevOps cache key constant for current release run response.
* Add `@Cacheable` to the service method backing `/release/current-run`.
* Evict the cache when branch submission creates a new pipeline run for the application env.
* Evict the cache when a pipeline definition is published for the application env.
* Evict the cache when public pipeline execution operations mutate the current run, such as cancel, retry, conflict resolution, or code-merge start.
* Prefer declarative cache annotations. Use programmatic cache only if required by private/internal mutation paths that cannot be expressed cleanly with annotations.

## Acceptance Criteria

* [ ] Repeated calls to `getApplicationReleaseCurrentRun(applicationEnvId)` can be served from Spring cache.
* [ ] New release submissions invalidate the cached current-run response for that application env.
* [ ] Pipeline publication invalidates the cached current-run response for that application env.
* [ ] Pipeline execution state transitions do not leave the endpoint permanently stale.
* [ ] Existing current-run response shape and error behavior remain unchanged.
* [ ] Focused tests compile and pass for the affected DevOps module where feasible.

## Out of Scope

* Changing the endpoint path, request parameters, or response schema.
* Redesigning pipeline run persistence or polling behavior.
* Adding new Redis infrastructure beyond standard Spring cache annotations and the existing TTL cache manager support.

## Technical Notes

* Relevant files:
  * `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/controller/admin/application/ApplicationController.java`
  * `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/service/application/ApplicationServiceImpl.java`
  * `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/service/pipeline/PipelineDefinitionServiceImpl.java`
  * `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/service/pipeline/execution/PipelineExecutionServiceImpl.java`
* Existing cache pattern examples are in system/iot/im modules using `RedisKeyConstants` and Spring cache annotations.
* `TimeoutRedisCacheManager` supports cache name format `key#ttl`, including seconds/minutes suffixes.
