# Competitor Research: Aliyun Yunxiao Demand Management

## Source Pages

- Demand management overview: https://help.aliyun.com/zh/yunxiao/user-guide/demand-management/
- New demand: https://help.aliyun.com/zh/yunxiao/user-guide/new-demand/
- Requirement import: https://help.aliyun.com/zh/yunxiao/user-guide/requirement-import
- Demand assignment: https://help.aliyun.com/zh/yunxiao/user-guide/demand-assignment
- Product theme management: https://help.aliyun.com/zh/yunxiao/user-guide/product-theme-management

The overview page was last modified by Aliyun on 2025-02-28. Adjacent operational pages expose future timestamps in their page metadata, so use their functional descriptions rather than treating the timestamps as reliable product chronology.

## Product Model Observed

Yunxiao separates product collaboration into adjacent concepts:

- Original claims: upstream, raw business input.
- Product themes: planning layer that groups scattered demands, scores value and workload, and helps prioritize.
- Demands: delivery work items that move from creation to assignment, planning, implementation, testing, completion, and cancellation.

Demand management is framed as a lifecycle tool: create, assign, track, split or associate, place into iteration, collaborate, and analyze. The overview explicitly highlights Scrum, Kanban, visual boards, reports, demand relations, history, templates, automation, and linkage to tasks.

## Capabilities To Borrow

MVP candidates:

- Demand list with search, filters, saved view-ready structure, and bulk-operation-ready selection.
- Demand creation from the list through a modal or drawer.
- Main content area with title and rich description.
- Right-side field panel for structured metadata:
  - work item type
  - owner
  - priority
  - project
  - iteration
  - participants
  - CC users
  - tags
  - planned start date
  - planned finish date
  - estimated workload
- Manual assignment by owner field.
- Lifecycle statuses:
  - pending
  - designing
  - developing
  - testing
  - completed
  - canceled
- Status transition history and field-change history.
- Parent-child support or at least a parent-demand field to avoid redesign when demand splitting is introduced.
- Relationship support for linking demands to future tasks, defects, code changes, or releases.

Post-MVP candidates:

- Excel import with template download, format validation, upload progress, result page, and failed-row download.
- Demand templates with configurable fields, ordering, required flags, and work item types such as product demand and technical demand.
- Automation rules, especially owner assignment and state transitions when labels or fields change.
- Theme management:
  - theme list grouped by status
  - value score
  - workload score
  - value/workload ratio
  - expandable child-demand rows
  - cross-project demand aggregation
- Scrum/Kanban boards and burn-down charts.
- Full analytics dashboards.

## Design Implications For `yudao-module-project`

Start with a conservative demand-delivery core instead of building the full Projex-style suite:

- Implement one primary aggregate: `Demand`.
- Keep project and iteration references as fields, but do not require full project/iteration management in the same milestone.
- Represent users by existing system user IDs; resolve display names through the system module at query/VO level.
- Use dictionary-backed enums for demand type, priority, status, and relation type.
- Store tags as a relation table or normalized IDs if a generic tag system exists later; for the first backend milestone, a simple normalized demand-tag relation is preferable to comma strings.
- Add extension points for:
  - parent-child demands
  - demand relations
  - comments
  - field-change history
  - saved views

## Recommended MVP Boundary

The first feature should prove that a team can manage a demand from intake to completion:

1. Create demand.
2. List and filter demands.
3. View detail.
4. Edit fields.
5. Assign owner.
6. Change status with valid transition checks.
7. Record operation history.
8. Link a demand to a parent or related demand.

The following should stay out of MVP unless the user explicitly pulls them in:

- Excel import.
- Workflow automation rule engine.
- Custom template builder.
- Theme scoring and prioritization.
- Kanban board.
- Reports and burn-down charts.
- Task execution integration.

