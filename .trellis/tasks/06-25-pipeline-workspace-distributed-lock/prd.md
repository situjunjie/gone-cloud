# PRD: 流水线工作目录改用分布式锁

## 背景

`LocalPipelineWorkspaceService` 用 `synchronized` + 进程内 `ConcurrentHashMap<String, Object>`
（`runWorkspaceLocks`）按工作目录路径串行化两处文件系统操作：

- `createWorkspace(...)`：创建 run / cache 工作目录、缓存挂载目录、写 `cache-directories.json`。
- `resetRunWorkspace(...)`：清空 run 工作目录。

进程内锁只在单 JVM 内有效。一旦多节点部署、且 `yudao.devops.pipeline.workspace-root`
指向共享存储（NFS / EFS 等），不同节点对同一工作目录的并发 create / reset 不再受任何保护，
可能出现目录创建与清空交错、metadata 文件写坏等问题。

## 目标

把这两处针对“工作目录路径”这一跨节点共享资源的串行化，从进程内 `synchronized`
改为基于 Redis（Redisson）的分布式锁，保证多节点下同一工作目录的 create / reset 互斥。

## 范围

### In scope
- 仅 `LocalPipelineWorkspaceService` 的 `createWorkspace` 与 `resetRunWorkspace` 两处。
- 锁 key 复用现有 `buildRunWorkspace(run)` 计算出的工作目录路径，保证与原 `synchronized` 语义一致
  （同一路径互斥；definition-scoped 多个 run 共用一把锁；standalone 按 run 隔离）。
- 采用编程式 Redisson（`RedissonClient.getLock` + `tryLock` + `finally` 释放），与仓库现有
  `ImRtcCallLockRedisDAO` 同款写法对齐。

### Out of scope（明确不改，附技术原因）
- `PipelineExecutionEngine` 的 `synchronized(sharedState)`：守护的是单 run 进程内 `Map`，
  分布式锁无法协调“各 JVM 各自一份的内存 Map”，换锁会制造跨节点安全的假象。
- `PipelineLogBroadcaster` 的 `synchronized(emitter)`：守护节点本地的 `SseEmitter`（HTTP 连接），
  只存在于本节点。
- 三个终端 WebSocket Handler 的 `synchronized(session)`：守护 `WebSocketSession.sendMessage`，
  Spring 的 session 并发发送不安全，连接物理绑定本节点；换分布式锁会给每帧输出加 Redis 往返开销，
  且解决不了“本 JVM 内并发写本地 socket”这一真实竞争。

## 约束 / 兼容性
- 不新增 Maven 依赖：`RedissonClient` 已随 `yudao-spring-boot-starter-redis` 在 devops 类路径上。
  不引入 `yudao-spring-boot-starter-protection`（`@Lock4j`）。
- 行为对外保持不变：方法签名、返回值、抛出的异常类型不变；正常路径只是把“拿进程锁”换成“拿分布式锁”。
- 锁需带 lease 兜底，避免节点宕机导致死锁。

## 验收标准
1. `createWorkspace` / `resetRunWorkspace` 不再使用 `synchronized` 与 `runWorkspaceLocks` 进程内 Map，
   改为基于 `RedissonClient` 的分布式锁。
2. 锁 key 由工作目录路径派生，且与原语义等价：同一 `buildRunWorkspace(run)` 路径串行，互不相同路径并发。
3. 锁写法包含 `tryLock(wait, lease, unit)`、获取失败的处理、`finally` 中 `isHeldByCurrentThread()` 兜底释放。
4. 其余四处 `synchronized`（engine / broadcaster / 三个 websocket handler）保持不变。
5. 编译通过；新增/调整的单测覆盖“锁包裹了原文件系统逻辑、原逻辑结果不变”。
6. devops 模块整体不新增第三方依赖。
