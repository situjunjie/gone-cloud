# 实施清单

- [x] 枚举、注册表、条件参数校验增加 ORCHESTRATION。
- [x] 编排读取/验证/排序与来源快照，复用配置渲染。
- [x] KubernetesDeploymentManifestSupport 增加按模式的资源白名单、准备与展示脱敏。
- [x] 部署执行/状态收集区分 Service 门禁，日志/详情屏蔽 Secret。
- [x] 测试 DSL、六类资源/非法组件、执行顺序、快照重试、敏感信息及既有行为。
- [x] 同步 PIPELINE_YAML_SPEC.md 与 DevOps pipeline spec。
- [x] Trellis check 与 git diff --check。

## Validation

```bash
mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dtest=OrchestrationBeanWiringTest,ApplicationServiceImplTest,OrchestrationDeploymentTest,PipelineNodeRegistryServiceImplTest,PipelineSpecValidationServiceImplTest,KubernetesDeploymentManifestSupportTest,DeploymentOrderServiceImplTest,KubernetesDeploymentStatusCollectorTest,K8sDeployStepHandlerTest,ApplicationOrchestrationServiceImplTest,ApplicationOrchestrationValidationServiceImplTest,PipelineExecutionEngineTest,K8sImageUpgradeStepHandlerTest -Dsurefire.failIfNoSpecifiedTests=false test
git diff --check
```

验证结果（2026-10-12）：Maven 3.9.9 / JDK 17，reactor compile 成功；初始定向测试 129 个通过但没有覆盖 Spring 装配；循环依赖修复后扩大为 165 个全部通过，git diff --check 通过。测试使用现有 Mockito/Fabric8 测试设施，不调用真实集群；未做真实集群联调。模块编译由 reactor test 覆盖。

检查修复：旧部署单详情可能没有 config_json，展示脱敏不能将空快照升级为读取错误；已保持旧读取行为并通过既有回归。检查过所有 deployment config_json 出口，内部存储用于执行，详情/page 与日志 context 使用脱敏副本。无数据库迁移。当前 harness 未提供可用的子代理工具，实施与检查由主代理执行。

## Startup Regression Follow-up

- 用户发现 BeanCurrentlyInCreationException，确认新增部署 → 编排依赖与编排 → ApplicationService 反向依赖成环。
- ApplicationOrchestrationServiceImpl 改用 ApplicationMapper 做相同存在性查询，保持 APPLICATION_NOT_EXISTS 错误码，未增加 @Lazy / ObjectProvider 或放开循环引用。
- OrchestrationBeanWiringTest 注册真实应用/异步启动/执行引擎/handler 注册表/部署/编排服务，外部设施 mock，禁止循环引用，启用缓存与异步代理，验证两种初始化顺序。修改前两例均因依赖环失败，修改后均通过。
- 编排查询/校验/保存缺失应用时仍失败且不读写草稿。
- 完整定向测试 165 个全部通过；未运行连接真实 MySQL/Redis/Kubernetes 的整机启动。

## Review / Rollback

风险点为 deployMode 分支、Service 门禁和 Secret 展示。复核所有读取 config_json/contextJson 的出口。回滚应用代码即可，新增 JSON 字段可被旧代码忽略，无数据库迁移。用户本轮明确批准开始实现，完整规划材料已落盘；执行后保留改动供审阅，不推送。
