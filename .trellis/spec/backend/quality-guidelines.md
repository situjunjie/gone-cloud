# Quality Guidelines

This repository already has a strong testing footprint and repeated implementation patterns. Quality work here means matching those patterns, not introducing clever one-off structures.

## Required Patterns

- Keep business logic in services, not controllers.
- Put persistence rules in mapper default methods using `BaseMapperX` and wrapper helpers.
- Reuse module `ErrorCodeConstants`, shared test bases, and framework utilities before adding new abstractions.
- Follow existing module boundaries: `*-api` for shared contracts, `*-server` for local execution.

## Testing Requirements

Match the narrowest test style that already exists nearby:

- Pure unit/service logic: `BaseMockitoUnitTest`
- Persistence-heavy service logic: `BaseDbUnitTest`
- Spring bean override tests: `@MockitoBean` with imported service classes or test slices

Examples:

- Database-backed service tests in [AdminAuthServiceImplTest.java](/Users/situjunjie/projects/gone-cloud/yudao-module-system/yudao-module-system-server/src/test/java/cn/iocoder/yudao/module/system/service/auth/AdminAuthServiceImplTest.java:1)
- Mockito-based framework tests in [ApiSignatureTest.java](/Users/situjunjie/projects/gone-cloud/yudao-framework/yudao-spring-boot-starter-protection/src/test/java/cn/iocoder/yudao/framework/signature/core/ApiSignatureTest.java:1)
- Reactor/module wiring helper in [ProjectReactor.java](/Users/situjunjie/projects/gone-cloud/yudao-gateway/src/test/java/cn/iocoder/yudao/ProjectReactor.java:1)

When adding new backend behavior, place the test in the same module rather than inventing a central test project.

## Forbidden Shortcuts

- Do not add business logic straight into controllers to avoid writing a service.
- Do not create public cross-module dependencies on `*-server` internals when `*-api` is the contract layer.
- Do not bypass shared test bases with custom hand-rolled setup unless the module already requires a different style.
- Do not duplicate utility code that already exists in `yudao-framework`.

## Code Review Checklist

Review backend changes for:

1. Correct module placement.
2. Consistent naming (`DO`, `Mapper`, `ReqVO`, `RespVO`, `ErrorCodeConstants`).
3. Mapper methods that read clearly and encapsulate query details.
4. Error handling that still returns standard `CommonResult` payloads.
5. Focused tests that cover both success and business-failure branches.
6. Logging that is useful at boundaries and not noisy in core loops.

## Common Failure Modes

- Adding a feature to `yudao-server` that should have been a reusable domain module change.
- Writing mapper queries inline in services and repeating them across methods.
- Forgetting to assert error-code behavior in tests, especially for authentication, workflow, and external integration paths.
