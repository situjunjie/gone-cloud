# 项目工作项自定义字段 Schema：实施计划

## 1. 准备

- [x] 读取 backend 必需规范和 Trellis Phase 2 实施上下文。
- [x] 确认工作区既有修改并避免覆盖无关文件。
- [x] 确认现有字段列表、scope 合并、分类校验和选项排序实现。

## 2. 实现

- [x] 在 `WorkItemFieldService` 增加运行时 Schema 查询方法。
- [x] 在 `WorkItemFieldServiceImpl` 复用有效项目字段查询，过滤禁用字段和不适用分类。
- [x] 复用现有响应转换，并让 Schema 只保留启用选项，管理列表行为保持不变。
- [x] 在 `ProjectWorkItemController` 增加 `GET /schema`，补充参数校验、Swagger 说明和查询权限。

## 3. 测试

- [x] 扩展 `WorkItemFieldServiceImplTest`，覆盖全局/项目字段合并、字段启用过滤、分类过滤、选项启用过滤。
- [x] 覆盖未传分类时返回全部启用字段的行为。
- [x] 运行 project server 聚焦单测。
- [x] 运行 project server 编译或模块级测试，确认 Controller 参数校验和接口签名可编译。

建议命令：

```bash
mvn -pl yudao-module-project/yudao-module-project-server -am \
  -Dtest=WorkItemFieldServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl yudao-module-project/yudao-module-project-server -am -DskipTests compile
```

## 4. 检查与回滚

- [x] 使用 `trellis-check` 检查规范、复用、数据流和测试结果。
- [x] 确认 `/project/work-item-field/list` 的禁用项返回行为未变化。
- [x] 若新增接口验证失败，回滚 Controller、Service 和测试修改；无需处理数据库。

## 5. 验证结果

- `mvn -pl yudao-module-project/yudao-module-project-server -am clean -Dtest=WorkItemFieldServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过，8 个测试。
- `mvn -pl yudao-module-project/yudao-module-project-server -am test`：通过，project server 46 个测试。
- `git diff --check`：通过。
