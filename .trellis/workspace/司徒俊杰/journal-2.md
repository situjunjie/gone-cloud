# Journal - 司徒俊杰 (Part 2)

> Continuation from `journal-1.md` (archived at ~2000 lines)
> Started: 2026-07-02

---



## Session 60: DevOps approval deploy detail HTML

**Date**: 2026-07-02
**Task**: DevOps approval deploy detail HTML
**Branch**: `master-gigi`

### Summary

Added deploy_html rich-text approval details for DevOps pipeline BPM approvals, including application, environment, run metadata, ordered changes, tester/reviewer display fallback, HTML escaping, focused tests, and pipeline spec documentation.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `c0e4c7636` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 61: Pipeline execution context workspace refactor

**Date**: 2026-07-03
**Task**: Pipeline execution context workspace refactor
**Branch**: `master-gigi`

### Summary

Unified pipeline handler context around PipelineExecutionContext, initialized run workspace at execution start, moved CodeMerge to run workspace, persisted namespaced step outputs, updated source preparation and focused tests.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `2a05626e2` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 62: Pipeline step actions and approval resume

**Date**: 2026-07-04
**Task**: Pipeline step actions and approval resume
**Branch**: `master-gigi`

### Summary

Implemented pipeline step action metadata and platform step logs for CodeMerge, Approval, and image export steps; fixed approval resume by unblocking the blocked job after BPM approval so the engine continues to downstream steps; verified focused DevOps pipeline tests.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `befc2ee26` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 63: Return project page user avatars

**Date**: 2026-07-06
**Task**: Return project page user avatars
**Branch**: `master-gigi`

### Summary

Added project page response fields for creator user id/name/avatar and owner avatar via Easy-Trans, and expanded AdminUserApi auto-trans fields to include avatar. Verified with project module clean compile.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `d6437f85f` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 64: Project announcement update support

**Date**: 2026-07-06
**Task**: Project announcement update support
**Branch**: `master-gigi`

### Summary

Recorded the follow-up project commit that adds project announcement persistence, save/response fields, SQL schema support, and an update-announcement endpoint/service path.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `1994f7572` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 65: Add project sprint persistence model

**Date**: 2026-07-06
**Task**: Add project sprint persistence model
**Branch**: `master-gigi`

### Summary

Added project sprint persistence primitives: proj_sprint schema, ProjectSprintDO, mapper, sprint status enum, and renamed work item iteration references to sprintId/sprint_id across DOs, VOs, mappers, query/update field enums, and service field handling. Validated with project server clean compile.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `91972e400` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 66: DevOps deploy action and SSE disconnect fixes

**Date**: 2026-07-07
**Task**: DevOps deploy action and SSE disconnect fixes
**Branch**: `master-gigi`

### Summary

Added pipeline deploy-order frontend action/status support, simplified GONE startup banner, and handled Docker/Kubernetes SSE log client disconnects without noisy global errors.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `2daece178` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete
