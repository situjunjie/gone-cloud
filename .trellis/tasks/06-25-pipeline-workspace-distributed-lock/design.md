# Design: 流水线工作目录改用分布式锁

## 改动文件
1. `LocalPipelineWorkspaceService.java`（核心改造）
2. `RedisKeyConstants.java`（新增锁 key 常量）
3. 对应单测（验证锁包裹后原逻辑结果不变）

## 锁 key 设计

复用现有进程锁的 key —— `buildRunWorkspace(run).toString()`（工作目录绝对路径），
保证与原 `synchronized(lock)` 语义逐字对齐：

- definition-scoped run：路径为 `.../definition-{id}/workspace`，多个 run 落到同一路径 → 同一把锁。
- standalone run：路径含 `run-{id}` → 按 run 隔离。

在 `RedisKeyConstants` 新增：

```java
/**
 * 流水线工作目录操作的分布式锁。
 *
 * KEY 格式：devops:pipeline_workspace_lock:${workspacePath}
 * VALUE 数据类型：HASH（Redisson RLock）
 * 过期时间：lease 兜底，见 LocalPipelineWorkspaceService
 */
String PIPELINE_WORKSPACE_LOCK = "devops:pipeline_workspace_lock:%s";
```

> 假设：多节点共享存储时各节点挂载到相同路径（与原进程锁用 `toString()` 作 key 的隐含假设一致）。
> 这一点写进常量注释与本设计文档，不在本次扩大处理。

## 加锁写法（对齐 `ImRtcCallLockRedisDAO`）

- 注入 `RedissonClient`（已在 devops 类路径，无需新增依赖）。
- 删除字段 `private final Map<String, Object> runWorkspaceLocks`。
- 新增私有方法集中加锁逻辑：

```java
private static final long LOCK_WAIT_MS = 30_000L;   // 等待获取锁上限：网络存储下并发创建给足余地
private static final long LOCK_LEASE_MS = 60_000L;  // 持锁上限：自动释放兜底，避免节点宕机死锁

private void runWithWorkspaceLock(Path workspace, WorkspaceAction action) {
    String lockKey = String.format(RedisKeyConstants.PIPELINE_WORKSPACE_LOCK, workspace.toString());
    RLock lock = redissonClient.getLock(lockKey);
    boolean acquired;
    try {
        acquired = lock.tryLock(LOCK_WAIT_MS, LOCK_LEASE_MS, TimeUnit.MILLISECONDS);
    } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Acquire workspace lock interrupted: " + lockKey, ex);
    }
    if (!acquired) {
        throw new IllegalStateException("Acquire workspace lock timeout: " + lockKey);
    }
    try {
        action.run();   // 抛 IOException 时由调用方原样转译，保持对外异常类型不变
    } finally {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}

@FunctionalInterface
private interface WorkspaceAction {
    void run() throws IOException;
}
```

### createWorkspace 改造
原 `synchronized (lock) { ... }` 块整体搬进 `runWithWorkspaceLock(runWorkspace, () -> { ... })`。
外层仍 `try { ... } catch (IOException ex) { throw new IllegalStateException(...) }`，
异常转译路径不变（`WorkspaceAction.run()` 声明 `throws IOException`，由 lambda 抛出、外层捕获）。

### resetRunWorkspace 改造
原 `synchronized (lock) { try { clearPath(runWorkspace); } catch (IOException ...) }`
改为 `runWithWorkspaceLock(runWorkspace, () -> clearPath(runWorkspace))`，
IOException 转 `IllegalStateException` 的语义保留（放到 action 内或外层均可，保持与原文案一致）。

## 行为差异（需在 PRD/注释体现）
- 原进程锁“无限等待”，新分布式锁改为 `LOCK_WAIT_MS` 等待 + 超时抛 `IllegalStateException`。
  这是分布式锁的必要取舍（避免不可见的跨节点死等），属可接受且更安全的行为。
- 异常类型对外不变：超时/中断与原 IOException 失败都收敛为 `IllegalStateException`。

## 不改动项（含原因）
见 `prd.md` Out of scope：engine 的 `sharedState`、broadcaster 的 `emitter`、
三个 websocket handler 的 `session` 都是节点本地内存/连接对象，分布式锁不适用。

## 测试策略
- 复用/扩展现有 `LocalPipelineWorkspaceService` 单测（若无则新增）。
- 用嵌入式或 mock 的 `RedissonClient`：
  - 验证 `createWorkspace` 后目录、缓存挂载、metadata 文件如期生成（锁内逻辑结果不变）。
  - 验证 `resetRunWorkspace` 清空目录行为不变。
- 锁本身的分布式互斥依赖 Redisson，不在单测内做跨进程验证；通过代码评审确认 tryLock/unlock 配对。
