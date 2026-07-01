# WS日志实时滚动问题

## Goal

排查并修复 DevOps 中 Kubernetes Pod 日志和 Docker 容器日志的“不能正常实时滚动”问题，确认前端实际调用的是日志流接口还是终端 WebSocket 接口，并修正后端流式输出行为。

## Confirmed Facts

- 仓库里和“Pod 日志 / Docker 容器日志”直接相关的接口是 SSE 流，不是 WebSocket：
  - `GET /admin-api/devops/environment/kubernetes/pod-logs/stream`
  - `GET /admin-api/devops/environment/docker/container-logs/stream`
- 对应实现分别在 `KubernetesPodLogServiceImpl` 和 `DockerContainerLogServiceImpl`，都使用 `SseEmitter` 持续发送 `log` 事件。
- 底层库语义也已经确认：
  - fabric8 `PodOperationsImpl.watchLog()` 会拼接 `follow=true`
  - docker-java `LogContainerCmdImpl` 支持 `withFollowStream(true)`，当前代码已经启用
- 仓库里的 WebSocket 处理器是终端交互接口，不是日志接口：
  - `KubernetesTerminalWebSocketHandler`
  - `DockerTerminalWebSocketHandler`
- 这两个 WebSocket 处理器都会把终端输出转发成 `TYPE_OUTPUT` 消息，本质上是交互终端，而不是日志尾流查看。
- 现有日志 SSE 接口都支持 `tailLines`，并在连接后持续读取后续输出。
- 当前仓库没有前端代码目录可直接核对调用方式，所以还需要确认前端到底连的是 SSE 还是 WebSocket。
- 目前更像是“流式消费/展示”问题，而不是“后端没开 follow 参数”问题。

## Requirements

- R1. 确认“没有正常实时滚动”的具体链路：K8s Pod 日志、Docker 容器日志、还是终端 WebSocket。
- R2. 如果前端连错了接口，修正调用方式或补充统一的日志流接口说明。
- R3. 如果后端流本身有问题，定位并修复导致日志不持续推送、只返回首屏、或断流后不恢复的问题。
- R4. 保持现有权限、参数语义和错误处理风格一致。

## Acceptance Criteria

- [ ] 能明确指出问题发生在 SSE 日志流还是 WebSocket 终端链路。
- [ ] 对应接口在前端刷新或持续连接时能稳定收到后续日志行。
- [ ] 修复后能用最小复现步骤验证日志持续滚动。

## Open Questions

- 前端当前接的是哪一个接口路径，还是页面直接做了代理封装。
- 问题是“首屏能出但不继续滚动”，还是“连接直接失败/断开”，或者“有数据但不自动滚到底部”。
- 只影响 K8s、只影响 Docker，还是两者都影响，现象是否完全一致。
