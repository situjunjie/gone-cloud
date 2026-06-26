# Implement: 流水线工作目录改用分布式锁

## 执行顺序

### 1. RedisKeyConstants 新增锁 key
文件：`yudao-module-devops-server/.../dal/redis/RedisKeyConstants.java`
- 新增 `String PIPELINE_WORKSPACE_LOCK = "devops:pipeline_workspace_lock:%s";`
- 带注释（KEY 格式 / VALUE 为 Redisson RLock HASH / lease 兜底 / 共享存储同路径假设）。

### 2. LocalPipelineWorkspaceService 改造
文件：`.../framework/pipeline/runtime/LocalPipelineWorkspaceService.java`
- import：`org.redisson.api.RLock`、`org.redisson.api.RedissonClient`、`java.util.concurrent.TimeUnit`、
  `RedisKeyConstants`。
- 删除字段 `runWorkspaceLocks`（及 `ConcurrentHashMap` import 若不再使用）。
- 注入 `@Resource private RedissonClient redissonClient;`
- 新增常量 `LOCK_WAIT_MS = 30_000L`、`LOCK_LEASE_MS = 60_000L`。
- 新增 `runWithWorkspaceLock(Path workspace, WorkspaceAction action)` + 私有函数接口 `WorkspaceAction`
  （`run() throws IOException`），实现见 design.md：tryLock(wait,lease)→失败抛 IllegalStateException→
  finally isHeldByCurrentThread 释放；InterruptedException 复位中断并抛 IllegalStateException。
- `createWorkspace`：把原 `synchronized (lock) { ... }` 整块替换为
  `runWithWorkspaceLock(runWorkspace, () -> { ... })`，删除 `Object lock = ...computeIfAbsent(...)`。
  外层 try/catch(IOException→IllegalStateException) 保留。
- `resetRunWorkspace`：删除 `Object lock = ...computeIfAbsent(...)` 与 `synchronized`，
  改为 `runWithWorkspaceLock(runWorkspace, () -> clearPath(runWorkspace))`；
  IOException→IllegalStateException 文案保持 "Reset run workspace failed: ..."。

### 3. 单测调整
文件：`.../framework/pipeline/runtime/LocalPipelineWorkspaceServiceTest.java`
- `buildService()` 注入 mock：
  - `RedissonClient redissonClient = mock(RedissonClient.class);`
  - `RLock lock = mock(RLock.class);`
  - `when(redissonClient.getLock(anyString())).thenReturn(lock);`
  - `when(lock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);`
  - `when(lock.isHeldByCurrentThread()).thenReturn(true);`
  - `ReflectionTestUtils.setField(service, "redissonClient", redissonClient);`
- 现有 7 个用例应继续通过（锁对逻辑透明）。
- 可选新增：验证 `getLock` 被调用且 key 含工作目录路径；验证 tryLock 失败时 createWorkspace 抛 IllegalStateException。

## 验证命令
```bash
# 编译 + 单测（仅 devops-server 模块）
mvn -q -pl yudao-module-devops/yudao-module-devops-server -am \
  -Dtest=LocalPipelineWorkspaceServiceTest test
```
若全量编译过慢，至少保证上面模块级 test 通过。

## 回滚点
- 改动集中在 1 个核心类 + 1 个常量 + 1 个测试；如分布式锁引入问题，
  可单独 revert `LocalPipelineWorkspaceService` 与 `RedisKeyConstants` 两处回到 synchronized 版本。

## Review Gate
实现完成后跑 `trellis-check`：核对 prd 验收标准 1-6、design 的异常转译不变、四处不改的 synchronized 未被误动。
