# 工作项复杂条件单字段分组统计：技术设计

## 1. 范围与数据流

实现范围位于 `yudao-module-project/yudao-module-project-server`。

```text
GroupCountReqVO
  -> 条件标准化与 groupField 元数据解析
  -> 共用工作项查询 Wrapper 构造
  -> Mapper 动态单字段 GROUP BY
  -> GroupCountRespVO
```

不新增数据库表，不修改查询视图持久化结构。

## 2. API 合约

新增：

- `ProjectWorkItemGroupCountReqVO`
  - 继承 `ProjectWorkItemPageReqVO`，从而复用完全相同的筛选入参结构。
  - 新增 `@NotBlank String groupField`。
  - 继承得到的 `pageNo/pageSize` 不参与统计。
- `ProjectWorkItemGroupCountRespVO`
  - `String groupField`
  - `List<Group> groups`
  - `Group.value` 使用 `Object`，保留字符串、长整型、数字、布尔和时间等原始 JSON 类型。
  - `Group.count` 使用 `Long`。

Controller 新增 `POST /project/work-item/group-count`，使用 `project:work-item:query` 权限。

## 3. 条件复用

`ProjectWorkItemQueryConditionService` 继续作为条件值标准化和业务校验的唯一入口。统计服务先调用与分页相同的 `normalize(reqVO)`，再解析 `groupField`。

将 `ProjectWorkItemMapper` 当前分页方法内部的基础条件和动态条件拼接提取为可复用的查询 Wrapper 构造方法。分页和统计都调用该构造方法：

- 分页在共用 Wrapper 上追加 `id DESC` 并调用 `selectPage`。
- 统计不追加分页与明细排序，把同一 Wrapper 交给聚合 SQL。

共用部分必须包含快捷条件、关键字、平铺条件、条件组、自定义字段 `EXISTS/NOT EXISTS` 条件。这样修复或新增筛选操作符时，分页和统计会同时生效。

## 4. 分组字段解析

增加服务端内部的分组字段描述对象，至少包含：

- 原始 API 字段编码。
- 分组来源：固定字段、自定义标量、自定义选项。
- 固定字段物理列，或自定义值类型对应的物理值列。
- 自定义字段编号和字段类型。

固定字段必须通过 `ProjectWorkItemQueryFieldEnum` 解析。为动态 SQL 提供物理列时，列名作为服务端枚举常量维护，不能从请求字符串拼接。

自定义字段解析复用有效字段规则：

- 编码必须为 `custom:<fieldId>`。
- 必须有 `projectId`。
- 字段必须对项目有效。
- 字段类型决定标量值列或选项关联表。

解析失败继续使用项目模块查询字段/条件业务错误，不新增数据库异常暴露。

## 5. 聚合 SQL

增加 `ProjectWorkItemMapper.xml` 聚合查询，通过内部描述对象选择以下三种数据源：

### 5.1 固定字段

```sql
SELECT wi.<server_column> AS group_value,
       COUNT(DISTINCT wi.id) AS count
FROM proj_work_item wi
WHERE wi.deleted = 0
  AND <shared conditions>
GROUP BY wi.<server_column>
```

### 5.2 自定义标量字段

使用 `LEFT JOIN proj_work_item_field_value cfv`，连接条件包含工作项、租户、字段编号和逻辑删除。字段类型从服务端映射到 `text_value`、`number_value`、`datetime_value`、`boolean_value` 或 `user_value`。

`LEFT JOIN` 保留未填写字段的工作项，从而形成 `null` 分组。

### 5.3 自定义单选/多选字段

使用 `LEFT JOIN proj_work_item_field_value_option cfvo`，按照 `option_id` 分组。多选工作项会产生多个选项分组；每个分组使用 `COUNT(DISTINCT wi.id)`，避免重复关系数据放大数量。没有选项关系的工作项进入 `null` 分组。

### 5.4 安全与隔离

- `${}` 只允许用于服务端枚举解析出的固定列名/类型列名，调用方字符串不能直接传入。
- 字段编号和所有查询值使用 MyBatis 参数绑定。
- 主表保留逻辑删除条件，租户插件继续处理主表；关联表连接同时显式约束租户一致及逻辑删除。
- 聚合结果按数量降序、值升序、空值最后排序。

## 6. Service 组装

`ProjectWorkItemService#getWorkItemGroupCount` 返回响应 VO：

1. 标准化查询条件。
2. 解析分组字段描述。
3. 构造共用筛选 Wrapper。
4. 调用 Mapper 聚合查询。
5. 保留 Mapper 返回的原始分组值和数量，组装 `groupField`。

不在服务中二次加载用户、字典、项目或字段选项名称，避免动态字段翻译规则进入统计核心。

## 7. 兼容与性能

- 分页接口仍使用原请求和响应，不新增必填字段。
- 聚合接口不读取页码和页大小，不先加载工作项列表到内存。
- 过滤和分组在数据库完成。
- 自定义多选筛选继续使用相关 `EXISTS`，不会与分组 JOIN 相互放大；最终计数使用唯一工作项编号。
- 不设置固定分组数量上限；高基数字段会返回较多分组，这是调用方选择该字段的显式结果。

## 8. 测试设计

- 条件服务：固定字段、自定义字段、缺少项目、无效字段的 groupField 解析。
- Mapper Wrapper：分页与统计生成相同筛选片段；现有 AND/OR、自定义字段条件不回归。
- Mapper 数据库测试或聚合 SQL 测试：
  - 固定状态分组。
  - 自定义标量值及 null 分组。
  - 单选分组。
  - 多选分别计数且同组不重复。
  - 复杂条件组过滤后再分组。
- Service：响应携带原 groupField，空结果返回空 groups。
- 运行现有项目工作项查询测试和模块编译。

## 9. 回滚

本任务无数据库迁移。回滚 Controller、VO、Service、Mapper XML 和共用查询构造重构即可；现有工作项及查询视图数据不受影响。
