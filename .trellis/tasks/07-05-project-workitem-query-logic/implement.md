# Implementation Plan

- [x] Add field/operator enums for work item query conditions.
- [x] Extend `ProjectWorkItemPageReqVO` with a structured condition DTO.
- [x] Normalize and validate condition values in `ProjectWorkItemServiceImpl`, including current-user tokens.
- [x] Apply normalized conditions in `ProjectWorkItemMapper`.
- [x] Add/adjust error code constants for unsupported query conditions.
- [x] Compile the project module.
