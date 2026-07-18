# 工作项快捷更新适配自定义字段：实施计划

## 1. 准备

- [x] 读取 Trellis Phase 2 上下文和 backend 必需规范。
- [x] 确认工作区只保留既有 `.trellis/.template-hashes.json` 修改及当前任务文件。
- [x] 复核完整快照、快捷更新、物理删除和必填校验现状。

## 2. 自定义字段局部 Patch

- [x] 在字段值与选择值 Mapper 增加按工作项和字段编号集合的物理删除方法。
- [x] 在 `WorkItemCustomFieldValueService` 增加批量 `updateValues` 契约。
- [x] 实现目标字段批量加载、可写校验、类型归一化和最终必填字段校验。
- [x] 实现只删除/重建目标字段的局部写入，保留未指定历史值。
- [x] 移除或收敛旧的逐字段完整快照更新路径。

## 3. `/update-field` 接入

- [x] 将 `custom:<fieldId>` 一次解析为自定义字段请求列表。
- [x] 在写入前拒绝非法编码和重复字段。
- [x] 单次调用批量自定义字段更新服务，并保持内置字段同事务行为。
- [x] 更新请求 VO 的 Swagger 描述和示例。

## 4. 测试与验证

- [x] 扩展 `ProjectWorkItemServiceImplTest` 覆盖自定义字段解析、批量委托、混合更新和错误路径。
- [x] 扩展 `WorkItemCustomFieldValueServiceImplTest` 覆盖局部替换、清空、必填和禁用历史值保留。
- [x] 视 Mapper 动态 SQL 风险补充 Mapper 测试或通过现有 DB 测试基座验证 SQL。
- [x] 运行相关聚焦测试。
- [x] 运行 project server 全量测试与 `git diff --check`。

建议命令：

```bash
mvn -pl yudao-module-project/yudao-module-project-server -am \
  -Dtest='ProjectWorkItemServiceImplTest,WorkItemCustomFieldValueServiceImplTest,WorkItemFieldValueMapperTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl yudao-module-project/yudao-module-project-server -am test
git diff --check
```

## 5. 质量与回滚

- [x] 使用 `trellis-check` 检查局部 Patch、事务和历史值兼容边界。
- [x] 使用 `trellis-update-spec` 更新 `/update-field` 自定义字段运行时契约。
- [x] 局部删除和必填计算验证通过，无需执行回滚。

## 6. 验证结果

- clean 聚焦测试：18 tests，0 failures，0 errors。
- project server 全量测试：54 tests，0 failures，0 errors。
- `git diff --check`：通过。
- 残留搜索：`WorkItemCustomFieldValueService#updateValue` 调用为 0。
