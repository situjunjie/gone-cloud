# Error Handling

Backend responses are normalized around `CommonResult`, while business failures are expressed with project error codes and `ServiceException`. Gateway code has its own reactive exception adapter but still returns the same `CommonResult` shape.

## Core Contract

Follow this split:

- Business validation and rule failures: throw `ServiceException` or use the project exception helpers with module error-code constants.
- Unexpected failures: let the global handler translate them to a generic internal error payload.
- Gateway failures: convert reactive exceptions to `CommonResult` in the gateway exception handler.

Examples:

- Gateway global handling in [GlobalExceptionHandler.java](/Users/situjunjie/projects/gone-cloud/yudao-gateway/src/main/java/cn/iocoder/yudao/gateway/handler/GlobalExceptionHandler.java:1)
- Business exceptions in [ProductSkuServiceImpl.java](/Users/situjunjie/projects/gone-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/service/sku/ProductSkuServiceImpl.java:1)
- Shared error catalogs like [ErrorCodeConstants.java](/Users/situjunjie/projects/gone-cloud/yudao-module-crm/yudao-module-crm-api/src/main/java/cn/iocoder/yudao/module/crm/enums/ErrorCodeConstants.java:1)

## Error Types

Use the existing layers of error definition:

- `GlobalErrorCodeConstants` for shared/common failures
- `<domain>.enums.ErrorCodeConstants` for module-specific business rules
- `ServiceException` for domain-level failure propagation
- `ResponseStatusException` only when reactive/gateway internals already produce it

## API Error Responses

The response shape should remain `CommonResult.error(code, message)` rather than ad hoc exception DTOs.

In gateway code:

- `ResponseStatusException` is mapped by `responseStatusExceptionHandler(...)`
- all other exceptions fall back to `INTERNAL_SERVER_ERROR`

See [GlobalExceptionHandler.java](/Users/situjunjie/projects/gone-cloud/yudao-gateway/src/main/java/cn/iocoder/yudao/gateway/handler/GlobalExceptionHandler.java:1).

## Validation and Rule Failures

Prefer readable module-level error constants over inline strings in service methods. The existing modules heavily rely on static imports from `ErrorCodeConstants` plus `ServiceExceptionUtil.exception(...)`.

Good examples:

- `AUTH_LOGIN_BAD_CREDENTIALS` assertions in [AdminAuthServiceImplTest.java](/Users/situjunjie/projects/gone-cloud/yudao-module-system/yudao-module-system-server/src/test/java/cn/iocoder/yudao/module/system/service/auth/AdminAuthServiceImplTest.java:1)
- `REPEATED_REQUESTS` thrown in protection aspects under `yudao-framework`

## Wrong vs Correct

### Wrong

- Returning custom map payloads for errors from one controller
- Throwing bare `RuntimeException("xxx")` for business validation
- Logging and swallowing an exception, then returning success

### Correct

- Throw module `ErrorCodeConstants` via `ServiceException`
- Let the project's global exception infrastructure render `CommonResult`
- Log unexpected failures at the boundary handler, not in every lower layer

## Common Mistakes

- Encoding business rules in message text without defining a reusable error code.
- Mixing gateway reactive exception handling patterns into servlet-side server modules.
- Catching exceptions too early and losing the original stack trace or standard response shape.
