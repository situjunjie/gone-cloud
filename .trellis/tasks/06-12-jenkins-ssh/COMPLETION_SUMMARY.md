# jenkins-ssh 最终整理总结

**提交**: 316bc0278  
**日期**: 2026-06-14  
**状态**: 已按反馈收敛并提交

## 本次整理结果

- 将流水线执行收敛为平台侧责任链执行，由 `PipelineExecutionEngine` 统一驱动。
- 抽出 `CodeMergeService` 和 `CodeMergeNodeHandler`，代码合并作为责任链中的 `CODE_MERGE` 节点执行。
- 用 `PipelineExecutionAsyncService` 替代旧的代码合并异步入口，提交发布后异步启动整条流水线。
- 精简节点注册表，仅保留当前需要的 `CODE_MERGE` 和 `EXECUTE_SHELL` 节点。
- 删除独立 BuildHost/SSH 构建机模型、SSH 执行器、脚本生成器和多余节点 handler。
- 删除重复的 `PipelineNodeTypeEnum`，节点类型以 `PipelineNodeRegistryServiceImpl` 为声明来源。
- 增加 `DevOpsAsyncConfiguration`，为本机构建日志读取提供线程池。

## 验证

- `git diff --check`
- `mvn -pl yudao-module-devops/yudao-module-devops-server -am -DskipTests compile`
- `mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dtest='ApplicationServiceImplTest,DeploymentOrderServiceImplTest,PipelineExecutionServiceImplTest,CodeMergeNodeHandlerTest,PipelineNodeRegistryServiceImplTest,PipelineSpecValidationServiceImplTest,PipelineDefinitionServiceImplTest,PipelineApprovalServiceImplTest,LocalBuildExecutorTest' -Dsurefire.failIfNoSpecifiedTests=false test`

## 测试结果

- 目标测试共 62 个，通过 62 个，失败 0 个，跳过 0 个。

## 备注

- 当前提交是对早期 ST-1~ST-6 方案的收敛整理，不再保留独立 BuildHost/SSH 构建机抽象。
- 后续如果重新引入远程主机执行，应优先基于环境/应用主机模型扩展，而不是恢复独立 BuildHost 模型。
