# 应用编排 K8s 部署配置实施计划

## Phase 1: Backend Model

- 新增编排类型和组件模板类型枚举。
- 在 `sql/mysql/devops.sql` 新增 `dev_application_orchestration` 单草稿表。
- 新增 DO、Mapper、Convert 和请求/响应 VO。
- 新增编排不存在、并发更新、组件非法、依赖非法和 YAML 非法等错误码。

## Phase 2: Backend Service And APIs

- 实现组件模板服务，提供 Secret、ConfigMap、PVC、Service、Deployment、Ingress、Blank 默认 YAML。
- 实现编排校验服务：顺序归一化、唯一性、依赖引用/逆序/环、单资源 YAML、白名单、kind 和 metadata.name。
- 实现编排服务：按应用查询、校验、带乐观并发控制的原子保存。
- 新增 `component-templates/get/validate/save-draft` API。
- 在 `sql/mysql/devops-menu.sql` 增加 query/update 权限。

## Phase 3: Frontend Contract

- 在前端仓库的应用详情新增“应用编排”入口。
- 实现 Kubernetes 查看/编辑态、资源卡片拖拽排序和 YAML 编辑器。
- 实现组件新增、删除、复制、重命名和依赖调整。
- 对接模板、查询、校验和保存接口。
- 主机部署页签置灰。

当前仓库没有业务前端源码，因此本仓库实施范围只包含后端、SQL、接口契约和测试。

## Phase 4: Tests

- Service 测试：首次保存、覆盖保存、并发更新冲突、不同应用隔离、校验失败不覆盖旧草稿。
- Validation 测试：七类模板、顺序归一化、重复 id/名称、依赖不存在/逆序/成环、多文档、YAML 语法、kind 不一致、缺少 metadata.name。
- Controller 测试：权限和请求校验。
- 回归测试：现有 RAW_MANIFEST 校验和部署单测试保持通过。

建议验证命令：

```bash
mvn -pl yudao-module-devops/yudao-module-devops-server -am \
  -Dtest='*ApplicationOrchestration*Test,*KubernetesDeploymentManifestSupport*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

## Review Gates

- 确认所有保存路径使用同一校验服务。
- 确认 `component_json` 列表顺序和 `applyOrder` 永远一致。
- 确认 canvas 变化不会改变组件语义。
- 确认没有修改流水线 handler、部署单或 Kubernetes 执行逻辑。

## Rollback Points

- SQL、菜单权限和 Java 编排包均为新增内容，可整体回滚。
- 不修改现有 RAW_MANIFEST 数据和接口，无数据迁移回滚要求。
