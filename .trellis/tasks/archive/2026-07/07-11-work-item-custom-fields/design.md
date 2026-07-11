# 工作项自定义字段与动态筛选：技术设计

## 1. 设计结论

采用“项目级字段定义 + 选项 + 类型化字段值 + 选择值关联”的关系模型。核心工作项主表保持稳定，自定义字段作为工作项聚合内的扩展数据，由独立服务统一校验和持久化。

首期所有字段均直接归属项目。未来若需要租户模板，采用“模板复制到项目”的方式扩展，不让运行时字段值同时依赖租户继承和项目覆盖规则。

## 2. 数据模型

### 2.1 `proj_work_item_field`

字段定义表，继承租户与审计字段。

主要字段：

- `id`
- `project_id`
- `code`：项目内稳定唯一编码
- `name`
- `field_type`
- `applicable_category`：为空表示全部分类
- `required`
- `enabled`
- `sort`
- `config_json`：文本最大长度、数字上下界等低频校验配置，首期保留但不承载查询值

约束与索引：

- 唯一键 `(tenant_id, project_id, code)`
- 索引 `(tenant_id, project_id, enabled, sort)`

字段不提供删除入口，避免逻辑删除唯一键冲突及历史引用失效。

### 2.2 `proj_work_item_field_option`

选择项定义表。

主要字段：

- `id`
- `field_id`
- `code`
- `name`
- `color`
- `enabled`
- `sort`

约束与索引：

- 唯一键 `(tenant_id, field_id, code)`
- 索引 `(tenant_id, field_id, enabled, sort)`

选项不提供删除入口。修改名称不会影响字段值和查询视图，因为外部协议使用 `optionId`。

### 2.3 `proj_work_item_field_value`

每个工作项和字段最多一条值容器记录。

主要字段：

- `id`
- `work_item_id`
- `field_id`
- `text_value`
- `number_value`
- `datetime_value`
- `boolean_value`
- `user_value`

约束与索引：

- 唯一键 `(tenant_id, work_item_id, field_id)`
- `(tenant_id, field_id, text_value, work_item_id)`
- `(tenant_id, field_id, number_value, work_item_id)`
- `(tenant_id, field_id, datetime_value, work_item_id)`
- `(tenant_id, field_id, user_value, work_item_id)`
- `(tenant_id, field_id, boolean_value, work_item_id)`

未填写字段不创建值容器。字段值属于工作项内部从属数据，没有下游表引用其编号；完整快照更新时在事务内物理删除旧值和关联后重建，从而避免逻辑删除唯一键重复冲突。

### 2.4 `proj_work_item_field_value_option`

选择值关联表，单选和多选统一使用。

主要字段：

- `id`
- `field_value_id`
- `work_item_id`
- `field_id`
- `option_id`

冗余工作项和字段编号用于筛选索引，写入时必须校验与值容器一致。

约束与索引：

- 唯一键 `(tenant_id, field_value_id, option_id)`
- `(tenant_id, field_id, option_id, work_item_id)`
- `(tenant_id, work_item_id, field_id)`

## 3. Java 分层

新增领域组件：

- `WorkItemFieldDO`、`WorkItemFieldOptionDO`
- `WorkItemFieldValueDO`、`WorkItemFieldValueOptionDO`
- 对应 `Mapper`
- `WorkItemFieldService`：字段和选项配置管理
- `WorkItemCustomFieldValueService`：字段值解析、校验、差量写入和批量加载
- `ProjectWorkItemMapper`：在现有 Wrapper 查询上追加经过服务端校验的参数化 `EXISTS` 条件

`ProjectWorkItemServiceImpl` 仍负责工作项聚合事务，调用字段值服务，而不是自己包含全部字段类型分支。

## 4. API 设计

### 4.1 字段与选项管理

新增 `/project/work-item-field` 管理接口：

- 创建、更新字段
- 启用/禁用字段
- 按项目查询字段列表
- 创建、更新选项
- 启用/禁用选项

权限复用现有 `project:work-item:query` 和 `project:work-item:update`，避免引入未配置菜单权限。

### 4.2 工作项写入

`ProjectWorkItemSaveReqVO` 增加 `customFieldValues`：

```json
[
  {"fieldId": 1201, "value": 3101},
  {"fieldId": 1202, "value": [3102, 3103]},
  {"fieldId": 1203, "value": "客户提出"}
]
```

值继续使用 `JsonNode`，由字段元数据决定解析类型，避免为每种字段提供一组大量可空属性。

快捷更新沿用当前结构，字段名使用 `custom:<fieldId>`。

### 4.3 工作项响应

`ProjectWorkItemRespVO` 增加：

```text
customFields[]:
  fieldId/code/name/fieldType/required/enabled
  value
  options[]
```

详情查询加载单个工作项字段值；分页先获取工作项页，再按 `workItemIds` 批量加载并组装。

## 5. 校验与生命周期

写入顺序：

1. 校验项目和工作项核心字段。
2. 批量加载提交的字段定义和选项。
3. 校验字段属于项目、适用分类、启用、必填及类型。
4. 校验用户字段对应用户存在。
5. 写入工作项主记录。
6. 差量写入字段值与选择项关联。

完整更新将 `customFieldValues` 视为完整快照；未提交的非必填字段被清空，缺少必填字段则失败。快捷更新只修改指定字段。

分类变化前检查已有字段值是否仍适用。项目变化前检查是否存在任何字段值；存在则拒绝。

## 6. 查询设计

保留当前固定字段枚举。条件字段以 `custom:` 开头时，由服务解析数值 `fieldId` 并加载元数据，生成内部 `CustomCondition`，客户端字符串不直接作为列名。

自定义条件要求 `projectId`。分页 SQL 使用：

- 标量正向：`EXISTS` 值容器并比较类型化列
- 标量反向：`EXISTS` 值容器且值不匹配，未填写不自动命中
- 选项正向：`EXISTS` 选择值关联
- 选项反向：值容器存在且 `NOT EXISTS` 指定选择值
- `is_empty`：`NOT EXISTS` 值容器
- `is_not_empty`：`EXISTS` 值容器

动态条件继续复用 MyBatis-Plus 分页 Wrapper，使 count 和 data SQL 自动共享同一条件。字段类型决定的物理列来自服务端枚举，字段编号和值全部通过 MyBatis 参数绑定，客户端字段字符串不进入 SQL 标识符。

## 7. 查询视图兼容

`WorkItemQueryViewDO.FieldCondition.field` 无需变更结构，可直接保存 `custom:<fieldId>`。列配置同样使用该编码。

查询视图保存时不强制字段仍启用；执行筛选时允许禁用字段和选项，以支持历史数据。不存在或不属于项目的字段仍返回业务错误。

## 8. 性能边界

- 自定义条件上限 20 个。
- 自定义字段列表和值均批量加载。
- 主查询始终先按租户和项目缩小工作项集合。
- 多选筛选使用 `EXISTS`，不让关联表扩张分页结果。
- 首期不支持对自定义字段排序和分组；查询视图可以保存列，但排序/分组仅支持固定字段。

## 9. 兼容与回滚

- 现有请求不提交 `customFieldValues` 时，创建操作等价于无自定义值；如果项目配置了适用于当前分类的必填字段，则必须提交。
- 现有固定字段筛选协议保持不变。
- 数据库变更只新增表，不修改工作项主表，回滚代码不会破坏原有工作项数据。
- 回滚前若已产生自定义字段数据，保留新增表，避免数据丢失。

## 10. 延后能力

- 租户字段模板与项目复制
- 跨项目自定义字段查询
- 自定义字段排序、分组与统计
- 字段类型迁移
- 计算字段、级联选项、字段级权限
- 搜索引擎投影
