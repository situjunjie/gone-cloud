# 项目工作项自定义字段 Schema：技术设计

## 1. 设计结论

新增面向工作项录入场景的运行时 Schema 接口，Controller 仅接收参数并返回结果，字段合并、可写过滤和响应组装由 `WorkItemFieldService` 统一完成。

管理列表与运行时 Schema 保持两个语义：

- 管理列表返回全量配置，包括禁用字段和禁用选项。
- 运行时 Schema 只返回当前项目/分类允许新写入的启用字段和启用选项。

## 2. API 契约

```http
GET /project/work-item/schema?projectId=1024&category=REQUIREMENT
```

- `projectId`：必填。
- `category`：可选；提供时使用 `ProjectWorkItemCategoryEnum` 校验。
- 权限：`project:work-item:query`。
- 返回：`CommonResult<List<WorkItemFieldRespVO>>`。

`category` 可选是为了同时支持两种前端流程：先加载项目全部字段再本地切换分类，或在分类确定后只加载适用字段。后端在提供分类时始终执行精确过滤。

## 3. 数据流与职责

```text
ProjectWorkItemController
  -> WorkItemFieldService#getFieldSchema(projectId, category)
     -> ProjectService#validateProjectUsable(projectId)
     -> WorkItemFieldMapper#selectListByEffectiveProjectId(projectId)
     -> 过滤 enabled 与 applicableCategory
     -> WorkItemFieldOptionMapper#selectListByFieldIds(fieldIds)
     -> 过滤 enabled option
     -> WorkItemFieldRespVO
```

- scope 合并继续由 `selectListByEffectiveProjectId` 作为唯一事实来源。
- 分类语义与 `WorkItemCustomFieldValueServiceImpl#validateWritableField`、`validateRequiredFields` 对齐：空分类限制表示适用于全部分类。
- Schema 过滤放在 Service 层，避免 Controller 承担业务逻辑，也避免修改管理查询的全量语义。

## 4. 代码结构

### Controller

`ProjectWorkItemController` 注入 `WorkItemFieldService`，新增 `getWorkItemSchema` 方法。使用参数级 Bean Validation 校验 `projectId` 和可选 `category`。

### Service

`WorkItemFieldService` 新增：

```java
List<WorkItemFieldRespVO> getFieldSchema(Long projectId, String category);
```

`WorkItemFieldServiceImpl`：

1. 校验项目可用。
2. 查询全局与项目有效字段。
3. 过滤禁用字段及分类不匹配字段。
4. 批量查询剩余字段的选项。
5. Schema 组装时过滤禁用选项。

现有 `buildFieldRespList` 增加可控制“是否仅保留启用选项”的内部参数，管理列表继续传 `false`，Schema 传 `true`，避免复制 VO 转换代码。

### Mapper

不新增 Mapper 方法。现有查询已正确完成 scope 合并和排序；Service 过滤后的字段顺序保持不变，选项查询也已有稳定排序。

## 5. 边界情况

- 没有有效字段时返回空列表，不触发空 `IN` 查询。
- `category` 为空时不做分类过滤，但仍过滤禁用字段/选项。
- 全局字段响应中的 `projectId` 继续为 `null`，项目字段返回实际项目编号。
- 字段非选择类型即使存在异常遗留选项，也按现有响应结构返回查询结果；本任务只按启用状态过滤，不扩展数据修复规则。
- 字段/选项在 Schema 查询后被禁用时，最终写入仍由 `WorkItemCustomFieldValueServiceImpl` 二次校验，避免竞态绕过。

## 6. 兼容与回滚

- 仅新增接口和 Service 方法，不改变已有 API 返回。
- 无数据库变更。
- 回滚时删除新增 Controller 方法、Service 方法及测试即可，不影响已有字段和值数据。
