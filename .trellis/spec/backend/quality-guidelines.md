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

## Scenario: Response VO User Name Translation

### 1. Scope / Trigger

- Trigger: a backend response already exposes an admin user id and the frontend needs the display name.
- Scope: `*RespVO` classes under the owning `*-server` module, converters only if the value is not handled by response translation, and affected API contract docs or tests.

### 2. Signatures

- Response VO class must implement `org.dromara.core.trans.vo.VO` when it uses Easy-Trans `@Trans`.
- If the VO has `@Trans` but no `id` field, explicitly declare `Map<String, Object> transMap = new HashMap<>();` so Easy-Trans has a translation container.
- User id field uses:
  ```java
  @Trans(type = TransType.AUTO_TRANS, key = AdminUserApi.PREFIX,
          fields = "nickname", ref = "ownerUserName")
  private Long ownerUserId;
  private String ownerUserName;
  ```
- Admin user name source is `AdminUserApi.PREFIX` with `fields = "nickname"`.
- Keep the id field and add a sibling `*UserName` field; do not replace ids with names.
- When there is no `id` field to anchor translation, `transMap` must be present alongside the translated fields.

### 3. Contracts

- Controllers and services should continue returning domain response VOs; do not add manual user joins when `@Trans` can satisfy the contract.
- Controller methods that return translated response VOs should be annotated with `@TransMethodResult` so Easy-Trans runs on the outgoing result.
- Name fields are response-only display fields derived from ids.
- Nested list item VOs that contain translated ids must also implement `VO`; translating only the wrapper response object is not enough for item-level fields.
- If the requested display field is not an admin user nickname, document the source API and field explicitly before implementation.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| User id is null | Name field remains null |
| User id does not resolve | Name field remains null or follows Easy-Trans fallback behavior |
| VO has `@Trans` but no `id` field and no `transMap` | Add `transMap` before considering the endpoint complete |
| VO has `@Trans` but does not implement `VO` | Fix the VO contract before considering the endpoint complete |
| Controller returns translated VO without `@TransMethodResult` | Add the method annotation or translations will not execute on the response |
| MapStruct reports unmapped `*UserName` or `transMap` warnings | Accept when the field is intentionally populated by Easy-Trans, but compile the module to catch real mapping errors |

### 5. Good / Base / Bad Cases

- Good: `ownerUserId` remains in the response and `ownerUserName` is added through `@Trans(AUTO_TRANS, AdminUserApi.PREFIX, nickname)`.
- Good: a VO without `id` declares `transMap` and lets Easy-Trans populate translated fields through it.
- Good: the controller method carrying the translated VO is marked with `@TransMethodResult`.
- Base: service returns the same DO/VO mapping as before; response translation fills display fields at the API boundary.
- Bad: frontend calls `/system/user/simple-list` to build a dictionary for fields that the endpoint already knows.
- Bad: service loops over rows and calls `AdminUserApi#getUser` per row, creating N+1 RPC behavior.

### 6. Tests Required

- Run the affected module compile command so Lombok, MapStruct, and Easy-Trans imports are checked, for example:
  `mvn -pl yudao-module-devops/yudao-module-devops-server -am -DskipTests compile`
- For broader behavior changes beyond VO annotation, add a focused controller/service test that asserts the new response field is present or translated.
- Run a residual search for the id/name pair to verify every requested response VO was updated.
- If a translated VO has no `id`, verify `transMap` is present and not omitted by MapStruct/Lombok builders.
- If a translated VO is returned from a controller, verify the method is annotated with `@TransMethodResult`.

### 7. Wrong vs Correct

#### Wrong

```java
private Long ownerUserId;
```

The frontend must make a second dictionary request to render the owner.

#### Correct

```java
@Trans(type = TransType.AUTO_TRANS, key = AdminUserApi.PREFIX,
        fields = "nickname", ref = "ownerUserName")
private Long ownerUserId;
private String ownerUserName;
```

For `@Trans` VOs without an `id` field:

```java
private Map<String, Object> transMap = new HashMap<>();
```
