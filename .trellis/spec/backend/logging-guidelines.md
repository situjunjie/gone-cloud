# Logging Guidelines

Logging is based on SLF4J with Logback. The shipped configuration prefers console output plus async file logging, and request/edge diagnostics are implemented explicitly in gateway filters.

## Baseline Configuration

The server application uses:

- console pattern with timestamp, thread, level, logger, and line number
- file rolling by day and size
- async file appender enabled by default

Reference: [logback-spring.xml](/Users/situjunjie/projects/gone-cloud/yudao-server/src/main/resources/logback-spring.xml:1)

The gateway has its own matching `logback-spring.xml` and edge logging code under `filter/logging`.

## Log Levels

Use levels the way the repository already does:

- `INFO` for normal operational events and structured access logs
- `ERROR` for unexpected failures at system boundaries
- `DEBUG` only for targeted local troubleshooting; avoid introducing noisy permanent debug logs

Examples:

- Structured gateway access log at `log.info(...)` in [AccessLogFilter.java](/Users/situjunjie/projects/gone-cloud/yudao-gateway/src/main/java/cn/iocoder/yudao/gateway/filter/logging/AccessLogFilter.java:1)
- Boundary exception logging at `log.error(...)` in [GlobalExceptionHandler.java](/Users/situjunjie/projects/gone-cloud/yudao-gateway/src/main/java/cn/iocoder/yudao/gateway/handler/GlobalExceptionHandler.java:1)

## Structured Logging

Prefer logging with stable prefixes and structured content rather than free-form prose. Existing code commonly uses:

- bracketed method/context prefixes, for example `[writeAccessLog][...]`
- serialized objects via `JsonUtils.toJsonString(...)` or `JsonUtils.toJsonPrettyString(...)`
- deterministic map assembly before logging, as shown in `AccessLogFilter`

## What to Log

Good candidates:

- request path, method, route id, and duration at the gateway boundary
- translated exceptions at the boundary layer
- important state transitions where asynchronous or distributed behavior is hard to reconstruct later

## What Not to Log

- secrets, tokens, or passwords
- oversized request or response bodies unless the surrounding component already treats them as controlled diagnostics
- duplicate logs for the same exception at every layer

`AccessLogFilter` logs request and response bodies for JSON/form traffic, so new code should be cautious about adding a second body logger in downstream services.

## Anti-Patterns

- Logging and rethrowing the same unexpected exception in multiple layers.
- Adding permanent per-record `INFO` logs inside batch loops.
- Introducing a new logging format that does not match the existing prefix and JSON-heavy style.
