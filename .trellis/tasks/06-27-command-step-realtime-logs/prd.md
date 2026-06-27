# Investigate CommandStepHandler realtime logs

## Goal

Unify realtime log emission for pipeline step handlers in `yudao-module-devops`: execution-capable handlers should stream output live into step logs, and completed logs should still be archived.

## Requirements

- Identify the step handler(s) that write log output during execution rather than only collecting it in `resultJson`.
- Confirm whether `CommandStepHandler` reads live stdout/stderr from the executor.
- Confirm whether the project has a realtime log path for pipeline steps and where it is implemented.
- Define which handler categories are in scope for realtime output.

## Acceptance Criteria

- [ ] The realtime-log-capable handler(s) are named explicitly.
- [ ] The data flow from executor output to realtime UI/log stream is explained.
- [ ] It is clear whether `CommandStepHandler` itself publishes logs live or only stores them after execution.
- [ ] It is clear whether there is a build-machine stream source or only local/container exec streams.
- [ ] The in-scope handler categories are explicit.

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
