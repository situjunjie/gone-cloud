# Backend Development Guidelines

These guidelines describe the backend conventions that already exist in this repository. They are meant to keep future Trellis implementation and review sessions aligned with the Yudao-style multi-module Spring Boot codebase in this project.

## Scope

This layer applies when editing:

- `yudao-server/`
- `yudao-gateway/`
- `yudao-framework/`
- `yudao-module-*/`
- shared backend build files such as `pom.xml`, module `pom.xml`, and `sql/`

## Pre-Development Checklist

Read these files before changing backend code:

1. [Directory Structure](./directory-structure.md)
2. [Database Guidelines](./database-guidelines.md)
3. [Error Handling](./error-handling.md)
4. [Logging Guidelines](./logging-guidelines.md)
5. [Quality Guidelines](./quality-guidelines.md)

Also read the shared guides index at `.trellis/spec/guides/index.md`.

## Guidelines Index

| Guide | Description | Status |
|---|---|---|
| [Directory Structure](./directory-structure.md) | Multi-module Maven layout, app entrypoints, and package conventions | Ready |
| [Database Guidelines](./database-guidelines.md) | MyBatis-Plus access patterns, SQL script conventions, transaction placement | Ready |
| [Error Handling](./error-handling.md) | `CommonResult`, `ServiceException`, and gateway/server exception boundaries | Ready |
| [Logging Guidelines](./logging-guidelines.md) | Logback setup, log levels, and request logging expectations | Ready |
| [Quality Guidelines](./quality-guidelines.md) | Testing patterns, review checks, and project-specific forbidden shortcuts | Ready |

## Quality Check

Before calling backend work done, verify:

1. The change fits the existing module split: framework starter, `*-api`, `*-server`, gateway, or app entrypoint.
2. New persistence code follows `dataobject` + `dal/mysql` + `BaseMapperX` patterns.
3. Errors still surface as `CommonResult` payloads and business failures use project error-code constants.
4. Logging stays structured and does not leak secrets or large request payloads unnecessarily.
5. The affected module has focused tests, usually with `BaseDbUnitTest`, `BaseMockitoUnitTest`, or Spring test slices already used nearby.
