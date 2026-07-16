# 工作项快捷更新适配自定义字段：技术设计

## 1. 设计结论

保留 `/project/work-item/update-field` 的统一字段 Patch 协议：

```json
{
  "id": 1024,
  "fields": [
    {"field": "priority", "value": "HIGH"},
    {"field": "custom:1201", "value": 3101},
    {"field": "custom:1202", "value": [3102, 3103]}
  ]
}
```

自定义字段由 `custom:<fieldId>` 标识。Service 将内置字段与自定义字段拆分后分别批量校验，但所有写入仍位于 `ProjectWorkItemServiceImpl#updateWorkItemField` 的同一事务中。

## 2. 当前问题

当前每个 `custom:<fieldId>` 都调用一次 `WorkItemCustomFieldValueService#updateValue`。该方法读取全部现有值、覆盖一个字段，再调用 `saveSnapshot` 删除并重建整份快照，导致：

1. 多字段请求重复读取、校验和重建全部值。
2. 未修改的禁用字段/禁用选项历史值会再次进入可写校验，从而阻塞其他字段更新。
3. 未指定字段事实上也被物理删除后重建，不符合局部 Patch 边界。
4. 缺少 Service 测试证明 `custom:<fieldId>` 请求解析与事务委托行为。

## 3. API 与分层

### Controller / VO

- Controller 方法和 URL 不变。
- `ProjectWorkItemUpdateFieldReqVO.FieldValue.field` 的 Swagger 描述补充
  `custom:<fieldId>` 协议与示例。
- `value` 继续使用 `JsonNode`，类型由字段元数据决定。

### ProjectWorkItemService

`updateWorkItemField`：

1. 查询工作项并拆分内置字段、自定义字段。
2. 内置字段沿用现有解析、交叉校验和差异更新。
3. 将所有 `custom:<fieldId>` 一次解析为 `List<WorkItemCustomFieldValueReqVO>`；非法编码和重复字段在写入前失败。
4. 只调用一次 `WorkItemCustomFieldValueService#updateValues`。

### WorkItemCustomFieldValueService

新增批量局部更新方法：

```java
void updateValues(Long workItemId, Long projectId, String category,
                  List<WorkItemCustomFieldValueReqVO> values);
```

`updateValue` 可删除或保留为兼容委托，但项目工作项快捷更新只使用批量方法。

## 4. 批量局部更新算法

1. 校验请求项非空、`fieldId` 非空且不重复。
2. 一次批量加载目标字段定义。
3. 对每个目标字段执行现有可写校验和类型归一化：scope、项目、分类、启用、用户、选项归属与选项启用状态。
4. 加载当前工作项的字段值容器，只用于计算最终“已有值字段编号集合”。
5. 将目标更新覆盖到存在集合：非空值加入，空值移除。
6. 按最终存在集合校验当前项目/分类下所有启用必填字段。
7. 先物理删除目标字段的选择值关联，再物理删除目标字段值容器。
8. 只插入归一化后非空的目标字段值和选择项关联。

未指定字段不进入可写校验，也不参与删除/重建，因此已禁用字段及已禁用选项的历史值保持原样。

## 5. Mapper 设计

在两个字段值 Mapper 增加按 `workItemId + fieldIds` 的物理删除方法：

- `WorkItemFieldValueOptionMapper#physicalDeleteByWorkItemIdAndFieldIds`
- `WorkItemFieldValueMapper#physicalDeleteByWorkItemIdAndFieldIds`

使用 MyBatis 动态 SQL 与绑定参数生成 `IN` 条件；Service 保证 `fieldIds` 非空。删除顺序必须先关联表、后值容器表。

继续物理删除是因为字段值属于聚合内部子记录，现有唯一键不包含 `deleted`，与完整快照替换策略一致。

## 6. 校验与事务

- 所有目标自定义字段在任何删除/插入前完成解析和业务校验。
- 清空必填字段通过最终存在集合校验失败。
- 同一请求内置字段写入后若自定义字段校验/写入失败，由外层事务整体回滚。
- 工作项项目变更仍沿用“存在自定义值禁止迁移”规则。
- 分类变更仍先走现有 `validateCategoryChange`；本任务不支持在同一请求通过清理旧字段绕过分类变更拒绝。

## 7. 测试设计

### ProjectWorkItemServiceImplTest

- `custom:<fieldId>` 正确解析并一次批量委托。
- 内置字段和多个自定义字段可在同一请求处理。
- 非法 `custom:` 编码和重复自定义字段在委托前失败。

### WorkItemCustomFieldValueServiceImplTest

- 多字段批量更新只删除目标字段并插入新值。
- 清空可选字段只删除目标值，不插入新记录。
- 清空必填字段失败。
- 未指定的禁用历史字段不进入字段定义可写校验，并保留原值。
- 目标字段为跨项目、禁用、不适用分类或禁用选项时失败。

## 8. 兼容与回滚

- 外部请求协议和权限不变，现有客户端继续兼容。
- 不新增数据库表或字段。
- 回滚可恢复逐字段 `updateValue` 调用并删除局部删除 Mapper 方法；已有数据无需迁移。
