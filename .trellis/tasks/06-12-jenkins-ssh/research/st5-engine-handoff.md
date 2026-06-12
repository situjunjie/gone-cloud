# ST-5 引擎实现交接文档

**面向**: ST-6 实施人员  
**日期**: 2026-06-12

---

## 已实现内容

### 1. 责任链核心组件

#### 1.1 NodeOutcome 枚举
- **路径**: `handler/NodeOutcome.java`
- **作用**: 定义节点处理结果 (CONTINUE / SUSPEND / FAIL)，驱动链流转

#### 1.2 PipelineNodeContext
- **路径**: `handler/PipelineNodeContext.java`
- **作用**: 节点处理上下文，承载 run / version / node / sharedState / userId
- **sharedState**: Map<String, Object>，跨节点传递数据（如 repoUrl / branchName / commitSha / appKey / Docker 凭据）

#### 1.3 PipelineNodeHandler 接口
- **路径**: `handler/PipelineNodeHandler.java`
- **方法**: `boolean supports(String nodeType)` + `NodeOutcome handle(PipelineNodeContext ctx)`
- **模式**: 策略模式，Spring 自动注入所有实现类到引擎

#### 1.4 PipelineNodeLogHelper
- **路径**: `handler/PipelineNodeLogHelper.java`
- **作用**: RunLog CRUD 辅助组件，handler 复用写 node-level log
- **方法**: getOrCreateLog / markStarted / markSuccess / markFailed / markSuspended

### 2. Handler 实现

#### 2.1 ApprovalNodeHandler
- **支持类型**: `TYPE_APPROVAL`
- **逻辑**: 调用 `PipelineApprovalService.startApproval` → 返回 SUSPEND
- **幂等**: 检查 log status，SUCCESS 跳过，WAITING_INPUT 保持挂起

#### 2.2 ContainerDeployNodeHandler
- **支持类型**: `TYPE_CONTAINER_DEPLOY`
- **逻辑**: 调用 `DeploymentOrderService.startContainerDeploy` → 返回 CONTINUE/FAIL
- **特点**: 同步完成，不挂起

#### 2.3 MockNodeHandler
- **支持类型**: `TYPE_MOCK`
- **逻辑**: 读取 params.shouldFail 决定返回 CONTINUE/FAIL
- **用途**: 测试与演示

#### 2.4 BuildNodeHandler
- **支持类型**: 所有 BUILD 类节点 (通过 `StepScriptGenerator.supports` 判定)
- **流程**:
  1. `StepScriptGenerator.generate(node)` 生成纯 shell 脚本
  2. `BuildHostSelector.selectDefault()` 选择构建主机
  3. 构建 `ExecContext`，注入环境变量 (REPO_URL / BRANCH_NAME / COMMIT_SHA / APP_KEY / IMAGE_TAG / DOCKER_REGISTRY 凭据)
  4. 根据 `BuildHostTypeEnum.isSsh(host.getType())` 选择 LocalBuildExecutor 或 SshBuildExecutor
  5. 执行脚本，LogSink 流式写入 RunLog resultJson
  6. 退出码 0 → CONTINUE，否则 FAIL
- **凭据安全**: 环境变量 env 不进日志 (ExecContext.toString 已排除)，脚本仅引用变量名

### 3. PipelineExecutionEngine 驱动器

#### 3.1 核心方法
- **execute(run, version, userId)**: 执行流水线节点链
- **cancel(run, userId)**: 取消流水线，遍历 RunLog，BUILD 类调用 BuildExecutor.cancel，平台类调用对应服务取消

#### 3.2 执行流程
1. 从 version.specJson 解析 PipelineSpec
2. 调用 `PipelineSpecValidationService.sortNodes` 拓扑排序
3. 遍历节点，跳过 `enabled=false`
4. 通过 `resolveHandler(nodeType)` 匹配 handler (策略模式)
5. 调用 `handler.handle(context)`，根据 NodeOutcome 流转:
   - CONTINUE → 下一节点
   - SUSPEND → 持久化位置并返回（不阻塞线程）
   - FAIL → 标记 run FAILED 并返回
6. 全部节点 CONTINUE → 标记 run SUCCESS

#### 3.3 幂等性
- 每次重入从头遍历，handler 内部检查 log status：
  - SUCCESS → 跳过
  - WAITING_INPUT → 继续挂起
  - PENDING/RUNNING → 执行

#### 3.4 取消逻辑
- 遍历 RunLog，过滤 NODE level + 非终态
- BUILD 类: 同时调用 localBuildExecutor.cancel + sshBuildExecutor.cancel (runId)
- APPROVAL: 调用 PipelineApprovalService.cancelApproval
- CONTAINER_DEPLOY: 调用 DeploymentOrderService.cancelContainerDeploy
- 标记 log CANCELED，标记 run CANCELED

### 4. 单元测试

- **路径**: `PipelineExecutionEngineTest.java`
- **覆盖**:
  - `testExecute_allNodesContinue_success`: 两节点 CONTINUE，run 标记 SUCCESS
  - `testExecute_nodeFailure_runFailed`: 第二节点 FAIL，run 标记 FAILED
  - `testExecute_nodeSuspend_engineStops`: 第一节点 SUSPEND，引擎停止，run 不更新
- **结果**: 3/3 PASS

---

## 未实现内容 (留 ST-6)

### 1. CodeMergeNodeHandler
**原因**: 代码合并是内置伪节点 (`builtin.code_merge`)，不在 spec.nodes 中，是流水线触发前置步骤。  
**现状**: PipelineExecutionServiceImpl 中的 startCodeMerge / continueCodeMerge / finishCodeMerge 保持不动。  
**决策**: 合并逻辑与 GitWorkspaceService (workspaceKey-based) / CodeMergeContext (非 Builder) 耦合深，冲突恢复机制复杂，按任务指导"若会大面积冲击则保留"，留给 ST-6 决定是否重构。

### 2. 触发入口接线
**现状**: 新引擎已独立编译通过，但未接入触发流程。  
**接入点**:
- `PipelineExecutionServiceImpl.startCodeMerge` 完成合并后，调用 `PipelineExecutionEngine.execute(run, version, userId)` 启动链
- `PipelineApprovalStatusEventListener.onApplicationEvent` 审批完成时，调用 `engine.execute(run, userId)` 续跑
- `PipelineExecutionService.cancelRun` 调用 `engine.cancel(run, userId)`

**示例代码** (ST-6 可参考):
```java
// PipelineExecutionServiceImpl.finishCodeMerge 末尾
if (mergeSuccess) {
    pipelineExecutionEngine.execute(run, version, userId);
}

// PipelineApprovalStatusEventListener
if (approved) {
    pipelineExecutionEngine.execute(run, userId);
}
```

### 3. 节点类型分类清理
**现状**: `PipelineNodeRegistryServiceImpl.isJenkinsExecutableNode` 方法仍存在，但新引擎不依赖它。  
**ST-6 任务**: 删除该分类逻辑，统一由 handler.supports 决定节点路由。

---

## 编译与验证

### 编译命令
```bash
mvn -q -pl yudao-module-devops/yudao-module-devops-server -am -DskipTests compile
```
**结果**: 编译通过 ✓

### 测试命令
```bash
mvn -q -pl yudao-module-devops/yudao-module-devops-server -am -DskipTests install
mvn -q -pl yudao-module-devops/yudao-module-devops-server -Dtest=PipelineExecutionEngineTest test
```
**结果**: Tests run: 3, Failures: 0, Errors: 0 ✓

---

## 关键设计决策

### 1. sharedState 传递机制
- BuildNodeHandler 需要 REPO_URL (含 OAuth2 token) / BRANCH_NAME / COMMIT_SHA / APP_KEY
- 设计: PipelineNodeContext.sharedState 作为跨节点传递容器
- **ST-6 接线**: 代码合并成功后，注入这些变量到 sharedState，供后续 BUILD 节点使用

### 2. 凭据安全
- ExecContext.env 含敏感值，已从 toString 排除
- BuildHostDO 敏感字段 (password / privateKey / passphrase) 使用 EncryptTypeHandler + @ToString.Exclude
- 脚本生成: StepScriptGenerator 只引用环境变量名 ($REPO_URL / $DOCKER_REGISTRY_PASSWORD)，不内联明文

### 3. 幂等设计
- handler 每次重入都检查 RunLog status，已完成节点跳过
- SUSPEND 节点保持挂起，等待外部事件（审批完成 / 冲突解决）重入

### 4. 取消机制
- BUILD 类: 同时调用两个 Executor 的 cancel (因为不知道具体用哪个)
- 平台类: 分别调用对应服务的取消方法

---

## ST-6 集成检查清单

- [ ] 代码合并成功后调用 `engine.execute(run, version, userId)`
- [ ] 注入 sharedState: repoUrl (含 token) / branchName / commitSha / appKey / dockerRegistry / dockerUsername / dockerPassword
- [ ] 审批完成事件调用 `engine.execute(run, userId)` 续跑
- [ ] 取消流水线调用 `engine.cancel(run, userId)`
- [ ] 删除 `PipelineNodeRegistryServiceImpl.isJenkinsExecutableNode` 相关逻辑
- [ ] 删除 Jenkins 相关代码 (JenkinsPipelineClient / Jenkinsfile 生成等)
- [ ] 验证完整流程: 代码合并 → BUILD 节点 → 审批 → 部署

---

## 文件清单

### 新增文件
```
handler/NodeOutcome.java
handler/PipelineNodeContext.java
handler/PipelineNodeHandler.java
handler/PipelineNodeLogHelper.java
handler/ApprovalNodeHandler.java
handler/ContainerDeployNodeHandler.java
handler/MockNodeHandler.java
handler/BuildNodeHandler.java
PipelineExecutionEngine.java
```

### 测试文件
```
PipelineExecutionEngineTest.java
```

### 未修改文件 (保持兼容)
- PipelineExecutionServiceImpl (代码合并逻辑保留)
- PipelineApprovalService / DeploymentOrderService (接口不变)
- GitWorkspaceService (接口不变)
- StepScriptGenerator / BuildExecutor / BuildHostSelector (ST-1/2/3/4 已完成)

---

**总结**: ST-5 责任链引擎已独立实现并通过单元测试，可编译运行。代码合并逻辑按任务指导保留不动，触发入口接线留 ST-6 完成。新引擎支持 BUILD / APPROVAL / CONTAINER_DEPLOY / MOCK 节点，具备幂等重入、挂起恢复、取消能力，满足"独立编译、单测验证责任链流转"的目标。
