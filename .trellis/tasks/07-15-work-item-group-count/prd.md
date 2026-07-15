# 工作项复杂条件单字段分组统计

## Goal

为项目工作项提供一个单字段分组计数接口：先使用与工作项分页接口完全一致的查询条件筛选工作项，再按照调用方指定的一个固定字段或自定义字段分组，返回每个不同字段值对应的工作项数量。

## Requirements

### 查询条件

- 新接口使用与 `POST /project/work-item/page` 相同的查询语义。
- 支持现有快捷条件：`keyword`、`category`、`status`、`priority`、`assigneeUserId`、`creator`、`projectId`、`sprintId`、`plannedEndTime`、`createTime`。
- 支持现有 `conditions` 平铺 AND 条件。
- 支持现有 `conditionGroups`：组内 AND、组间 OR，且继续与 `conditions` 互斥。
- 支持固定字段及 `custom:<fieldId>` 自定义字段条件，包括当前已有的操作符、值解析、`me/currentUser/self` 用户令牌和最多 20 个条件的限制。
- 分页查询和分组统计必须复用同一套条件标准化与 SQL 条件构造逻辑，不能各自维护一份筛选规则。

### 分组字段

- 请求新增必填字段 `groupField`，每次只允许指定一个分组字段。
- 固定字段编码沿用 `ProjectWorkItemQueryFieldEnum` 的 API 字段编码。
- 自定义字段使用 `custom:<fieldId>` 编码。
- 自定义字段分组必须提供 `projectId`，字段必须是当前租户下对该项目生效的全局字段或项目字段。
- 不接受客户端提供数据库列名；实际列和关联表必须由服务端枚举及字段元数据确定。

### 汇总语义

- 普通固定字段和自定义标量字段按照数据库中的实际值分组。
- 单选字段按照选项编号分组。
- 多选字段按照每个选项分别计数；同一个工作项选择多个选项时会进入多个分组，因此各组数量之和允许大于筛选后的工作项总数。
- 每个分组统计唯一工作项数量，关联表不能造成同一工作项在同一个值下被重复计数。
- 未填写分组字段的工作项以 `value = null` 返回为独立分组。
- 返回原始字段值，不在本接口内解析字典名称、用户昵称、项目名称或选项名称。
- 结果不分页，默认按照 `count` 降序排列；数量相同时按照分组值稳定排序，空值放在最后。

### API

- 新增 `POST /project/work-item/group-count`。
- 权限复用 `project:work-item:query`。
- 请求体包含分页查询的全部筛选字段以及 `groupField`；分页参数即使出现也不参与统计。
- 响应包含 `groupField` 和 `groups`。
- 每个分组项包含动态类型的 `value` 与 `Long count`。

响应示例：

```json
{
  "groupField": "status",
  "groups": [
    {"value": "PENDING", "count": 12},
    {"value": "DEVELOPING", "count": 8},
    {"value": null, "count": 2}
  ]
}
```

## Constraints

- 本任务只实现单字段工作项数量统计，不实现多维透视、求和、平均值、时间粒度转换或工作项明细返回。
- 不修改工作项、自定义字段和查询视图表结构。
- 保持现有工作项分页接口兼容，不能改变已有条件的过滤结果。
- 查询必须保留租户隔离和逻辑删除语义。

## Acceptance Criteria

- [x] 使用与分页接口相同的快捷条件、`conditions` 或 `conditionGroups` 时，分组统计只统计同一筛选结果集中的工作项。
- [x] 可以按照固定字段分组并正确返回每个原始值及唯一工作项数量。
- [x] 可以按照文本、数字、日期、布尔、用户等自定义标量字段分组。
- [x] 可以按照自定义单选和多选字段分组，多选工作项分别计入每个已选选项。
- [x] 固定字段或自定义字段未填写的数据以 `null` 分组返回。
- [x] 非法固定字段、自定义字段编码、非本项目有效字段或缺少自定义字段所需 `projectId` 时返回项目模块业务错误。
- [x] 客户端字段字符串不能直接成为 SQL 标识符，统计 SQL 对值参数继续使用参数绑定。
- [x] 统计查询使用 `COUNT(DISTINCT work_item.id)` 或等价语义，关联数据不会重复放大数量。
- [x] 现有分页查询测试继续通过，并新增固定字段、自定义标量、单选/多选、空值及复杂条件组合的统计测试。
- [x] `yudao-module-project-server` 相关测试和编译通过。

## Notes

- 前端展示和标签翻译不在本任务范围内。
- 查询视图中的 `config.groupBy` 仍只是保存展示配置；前端可自行将该值作为本接口的 `groupField` 提交。
