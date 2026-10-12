# Bug Analysis: 应用编排接入后的 Spring Bean 循环依赖

## 1. Root Cause Category

- D（测试覆盖缺口）+ E（隐式假设）。认为现有 ApplicationService.validateApplicationExists 只是基础查询，忽略了其实现持有流水线异步启动服务。新增 DeploymentOrderService → ApplicationOrchestrationService 依赖后，原有编排 → ApplicationService 的依赖闭合为环。缓存/异步等 Bean 代理使 raw-version 引用无法安全使用。

## 2. Why Initial Verification Missed It

- Mockito @InjectMocks 验证部署业务，却替代了服务依赖，不经过 Spring Bean 创建与代理阶段。129 个定向测试通过不等于真实依赖链能启动。
- 新增 OrchestrationBeanWiringTest 在原代码中两种初始化顺序都失败，异常链完整覆盖应用 → 异步服务 → 引擎 → 注册表 → handler → 部署 → 编排 → 应用。

## 3. Prevention Mechanisms

| Priority | Mechanism | Action | Status |
|---|---|---|---|
| P0 | 架构边界 | 编排基础存在性校验直接使用 ApplicationMapper，移除对发布编排 ApplicationService 的反向依赖 | DONE |
| P0 | 容器回归 | 注册真实链路，外部设施 mock，禁止循环引用；启用缓存/异步代理，两种初始化顺序 | DONE |
| P1 | 行为回归 | 查/校验/保存时应用不存在仍返回 APPLICATION_NOT_EXISTS 且不访问草稿 | DONE |
| P1 | Spec | pipeline spec 明确配置服务不得反向依赖执行入口 | DONE |

## 4. Systematic Expansion

- 本次仅切断编排服务的一条反向边，不重构应用/执行引擎，也不使用 @Lazy/allow-circular-references。
- 同样风险适用于任何被 handler 使用、却反向注入流水线入口服务的配置模块。新增跨模块 service 注入应验证 Bean 图，不能只检查方法调用。

## 5. Verification / Knowledge Capture

- OrchestrationBeanWiringTest 两例由失败变成功，保留应用缓存代理及异步服务代理。
- 扩大后的 165 个定向测试全部通过；git diff --check 通过。没有连接真实基础设施做完整应用启动。
- 已更新 design.md、implement.md 与 .trellis/spec/backend/devops-pipeline-guidelines.md；本业务仓库无 Trellis 源模板需要同步。修复未提交，待用户审阅。
