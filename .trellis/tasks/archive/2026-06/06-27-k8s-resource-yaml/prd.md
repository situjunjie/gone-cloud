# 环境大盘增加 K8s 资源 YAML 查看接口

## Goal

为 DevOps 环境大盘的 K8s 环境增加一个资源 YAML 查看接口，支持按资源类型和名称查看指定资源的 YAML 内容。

## Requirements

- 仅对 `infraType=K8S` 的环境开放。
- 支持查看环境默认 Namespace 下的 Pod、Deployment、Service YAML。
- 支持查看 Namespace 资源 YAML。
- Namespace 可由请求显式传入；未传时默认使用环境配置的 Namespace。
- 返回值应包含资源标识和 YAML 文本，便于前端直接展示。
- 不泄露 kubeconfig 或其它连接敏感信息。

## Acceptance Criteria

- [ ] 环境大盘接口可按资源类型和名称返回指定资源 YAML。
- [ ] 非 K8s 环境访问时返回基础设施类型不支持错误。
- [ ] 请求参数校验与现有 DevOps 接口风格一致。
- [ ] 关键路径有单测覆盖成功、未找到和不支持三类情况。

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
