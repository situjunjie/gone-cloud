# 项目工作项前端接口对接文档

## 1. 对接结论

以下能力已经落地，可以开始前端对接：

- 运行时自定义字段 Schema：合并租户全局字段和当前项目字段。
- 工作项创建、详情与完整更新：支持自定义字段完整快照和历史值回显。
- 复杂分页查询：支持快捷条件、动态字段条件、AND/OR 条件组和多字段排序。
- 单字段分组统计：复用分页筛选语义，支持固定字段和自定义字段。
- 快捷更新：支持内置字段与 `custom:<fieldId>` 混合批量局部更新。
- 修改动态：按工作项编号分页查询完整更新与快捷更新产生的实际字段变化。
- 工作项动态：将评论与操作记录按统一时间线分页返回，并提供工作项专用发表评论接口。

本文以当前 Controller、VO、Service、Mapper 和自动化测试为准。

## 2. 接口总览

| 功能 | 方法 | 路径 | 权限 |
|---|---|---|---|
| 查询运行时 Schema | GET | `/admin-api/project/work-item/schema` | `project:work-item:query` |
| 创建工作项 | POST | `/admin-api/project/work-item/create` | `project:work-item:create` |
| 查询工作项详情 | GET | `/admin-api/project/work-item/get` | `project:work-item:query` |
| 完整更新工作项 | PUT | `/admin-api/project/work-item/update` | `project:work-item:update` |
| 复杂分页查询 | POST | `/admin-api/project/work-item/page` | `project:work-item:query` |
| 单字段分组统计 | POST | `/admin-api/project/work-item/group-count` | `project:work-item:query` |
| 快捷更新字段 | PUT | `/admin-api/project/work-item/update-field` | `project:work-item:update` |
| 查询修改动态 | GET | `/admin-api/project/work-item/change-log/page` | `project:work-item:query` |
| 查询工作项动态 | GET | `/admin-api/project/work-item/activity/page` | 系统查询 + `WORK_ITEM_QUERY` + `COMMENT_QUERY` |
| 发表工作项评论 | POST | `/admin-api/project/work-item/comment/create` | `COMMENT_CREATE` |

认证头、租户头和错误拦截沿用管理后台通用请求封装，本文只描述工作项业务载荷。

## 3. 通用协议

### 3.1 返回包络

所有接口返回 `CommonResult<T>`：

```json
{
  "code": 0,
  "msg": "",
  "data": {}
}
```

`code = 0` 表示成功；非 0 时使用 `msg` 展示或映射业务提示，不应继续读取成功数据。

分页接口的 `data` 是：

```json
{
  "list": [],
  "total": 0
}
```

### 3.2 编号与时间

- 工作项、项目、用户、选项等 Long 编号在 JavaScript 安全整数范围内为 `number`，超出范围时为 `string`；前端类型使用 `string | number`，比较、缓存和路由传参时统一转换为字符串，禁止用 `Number` 强制转换雪花编号。
- 普通 VO 中的 `LocalDateTime` 请求/响应使用 Unix 毫秒时间戳，例如 `1784282400000`。
- 动态查询条件的 `value`/`valueEnd` 是字符串；日期条件推荐
  `"2026-07-17 18:00:00"`。
- 自定义 `DATE_TIME` 写入值可使用 Unix 毫秒时间戳，或 ISO 本地时间字符串
  `"2026-07-17T18:00:00"`；前端推荐统一使用毫秒时间戳。

### 3.3 固定枚举

工作项分类：

| 值 | 名称 |
|---|---|
| `REQUIREMENT` | 需求 |
| `TASK` | 任务 |
| `BUG` | 缺陷 |

优先级：

| 值 | 名称 |
|---|---|
| `URGENT` | 紧急 |
| `HIGH` | 高 |
| `MEDIUM` | 中 |
| `LOW` | 低 |

状态：

| 值 | 名称 |
|---|---|
| `PENDING` | 待处理 |
| `DESIGNING` | 设计中 |
| `DEVELOPING` | 开发中 |
| `TESTING` | 测试中 |
| `COMPLETED` | 已完成 |
| `CANCELED` | 已取消 |

自定义字段类型：`TEXT`、`NUMBER`、`DATE_TIME`、`SINGLE_SELECT`、
`MULTI_SELECT`、`USER`、`BOOLEAN`。

自定义字段 scope：

- `GLOBAL`：当前租户内对所有项目生效，Schema 响应中的 `projectId` 为 `null`。
- `PROJECT`：只对指定项目生效，Schema 响应中的 `projectId` 为实际项目编号。

## 4. 前端推荐流程

### 4.1 创建

1. 用户选定项目和工作项分类。
2. 调用 Schema 接口获取当前可写字段。
3. 按 `fieldType` 渲染控件，并按 `required` 做前端校验。
4. 将填写值组装为 `customFieldValues`，调用创建接口。

### 4.2 编辑

1. 调用详情接口获得固定字段和历史 `customFields`。
2. 调用当前项目/分类的 Schema 获得仍可写字段。
3. 详情中的禁用历史字段/选项可以回显，但不能作为新值重新提交。
4. 表单保存使用完整更新；表格、看板等单字段操作使用快捷更新。

### 4.3 列表与统计

1. 使用同一份筛选状态构造分页请求。
2. 需要图表或筛选项计数时，将相同筛选请求增加 `groupField` 后调用分组接口。
3. 分组接口返回原始值，前端使用固定枚举、Schema 选项或用户字典翻译显示名称。

Schema 应在项目或分类变化时重新获取。字段配置发生变化后，最终写入仍由后端再次校验。

## 5. 查询运行时 Schema

### 5.1 请求

```http
GET /admin-api/project/work-item/schema?projectId=1024&category=REQUIREMENT
```

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `projectId` | Long | 是 | 项目编号，项目必须存在且可用 |
| `category` | String | 否 | 工作项分类；传入时按分类过滤字段 |

### 5.2 响应示例

```json
{
  "code": 0,
  "msg": "",
  "data": [
    {
      "id": 1201,
      "scope": "GLOBAL",
      "projectId": null,
      "code": "customer",
      "name": "客户",
      "fieldType": "SINGLE_SELECT",
      "applicableCategory": "REQUIREMENT",
      "required": true,
      "enabled": true,
      "sort": 10,
      "configJson": null,
      "createTime": 1784282400000,
      "options": [
        {
          "id": 3101,
          "fieldId": 1201,
          "code": "customer_a",
          "name": "客户 A",
          "color": "#1677ff",
          "enabled": true,
          "sort": 10
        }
      ]
    },
    {
      "id": 1202,
      "scope": "PROJECT",
      "projectId": 1024,
      "code": "reviewers",
      "name": "评审人",
      "fieldType": "USER",
      "applicableCategory": null,
      "required": false,
      "enabled": true,
      "sort": 20,
      "configJson": null,
      "createTime": 1784282400000,
      "options": []
    }
  ]
}
```

### 5.3 Schema 语义

- 返回当前租户 `GLOBAL` 字段与当前项目 `PROJECT` 字段的合并结果。
- 只返回启用字段和启用选项。
- 指定 `category` 时，返回 `applicableCategory` 为空或等于该分类的字段。
- 不指定 `category` 时，返回项目下所有启用字段，前端可按
  `applicableCategory` 本地过滤。
- 字段按 `sort ASC, id ASC`；选项按 `sort ASC, id ASC`。
- 没有可用字段时 `data` 为 `[]`。
- 不要使用字段管理接口作为表单 Schema；管理接口可能包含禁用字段和禁用选项。

## 6. 自定义字段值格式

创建/完整更新使用：

```json
{
  "fieldId": 1201,
  "value": 3101
}
```

| `fieldType` | 写入 `value` | 详情/分页 `value` | 推荐控件 |
|---|---|---|---|
| `TEXT` | string | string | 输入框/文本域 |
| `NUMBER` | number 或数字字符串 | number | 数字输入框 |
| `DATE_TIME` | 毫秒时间戳或 ISO 本地时间 | 毫秒时间戳 | 日期时间选择器 |
| `SINGLE_SELECT` | option id；也兼容单元素数组 | option id | 单选下拉 |
| `MULTI_SELECT` | option id 数组 | option id 数组 | 多选下拉 |
| `USER` | user id 或数字字符串 | user id | 用户选择器 |
| `BOOLEAN` | boolean 或 `"true"`/`"false"` | boolean | 开关 |

可选字段使用 JSON `null` 清空最明确。`MULTI_SELECT` 的空数组也会归一化为空，
但前端仍建议统一发送 `null`；必填字段清空会返回业务错误。

新写入的字段、用户和选项必须仍处于有效状态，并属于当前项目/字段；Schema 获取后发生配置变化时，写入可能被拒绝。

## 7. 创建工作项

### 7.1 请求

```http
POST /admin-api/project/work-item/create
Content-Type: application/json
```

```json
{
  "title": "支持项目工作项前端对接",
  "description": "创建表单示例",
  "category": "REQUIREMENT",
  "priority": "HIGH",
  "assigneeUserId": 1,
  "projectId": 1024,
  "sprintId": 2048,
  "plannedStartTime": 1784282400000,
  "plannedEndTime": 1784887200000,
  "parentWorkItemId": null,
  "remark": "前端联调",
  "customFieldValues": [
    {"fieldId": 1201, "value": 3101},
    {"fieldId": 1202, "value": 1},
    {"fieldId": 1203, "value": [3201, 3202]}
  ]
}
```

### 7.2 固定字段

| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| `title` | String | 是 | 非空，最多 128 字符 |
| `description` | String | 否 | 最多 5000 字符 |
| `category` | String | 是 | 工作项分类枚举 |
| `priority` | String | 是 | 优先级枚举 |
| `assigneeUserId` | Long | 是 | 用户必须有效 |
| `projectId` | Long | 是 | 项目必须有效 |
| `sprintId` | Long | 否 | 迭代编号 |
| `plannedStartTime` | Long | 否 | Unix 毫秒时间戳 |
| `plannedEndTime` | Long | 否 | 不得早于计划开始时间 |
| `parentWorkItemId` | Long | 否 | 父工作项必须同项目且不能形成循环 |
| `remark` | String | 否 | 最多 512 字符 |
| `customFieldValues` | Array | 否 | 自定义字段完整快照 |

创建时后端生成 `workItemNo`，初始状态固定为 `PENDING`。

### 7.3 响应

```json
{"code": 0, "msg": "", "data": 1024}
```

即使 `customFieldValues` 未提交，当前项目/分类存在启用必填自定义字段时，创建也会失败；因此创建前必须获取 Schema。

## 8. 查询工作项详情

### 8.1 请求

```http
GET /admin-api/project/work-item/get?id=1024
```

### 8.2 响应示例

```json
{
  "code": 0,
  "msg": "",
  "data": {
    "id": 1024,
    "workItemNo": "WI-000001",
    "title": "支持项目工作项前端对接",
    "description": "创建表单示例",
    "category": "REQUIREMENT",
    "priority": "HIGH",
    "status": "PENDING",
    "assigneeUserId": 1,
    "assigneeUserName": "张三",
    "projectId": 1024,
    "sprintId": 2048,
    "sprintTitle": "第 1 迭代",
    "plannedStartTime": 1784282400000,
    "plannedEndTime": 1784887200000,
    "estimatedHours": 8.00,
    "actualHours": 5.50,
    "parentWorkItemId": 512,
    "parentWorkItemTitle": "项目工作项基础能力",
    "remark": "前端联调",
    "creator": "1",
    "creatorUserName": "张三",
    "createTime": 1784282400000,
    "updateTime": 1784282400000,
    "customFields": [
      {
        "fieldId": 1201,
        "scope": "GLOBAL",
        "code": "customer",
        "name": "客户",
        "fieldType": "SINGLE_SELECT",
        "required": true,
        "enabled": true,
        "value": 3101,
        "selectedOptions": [
          {
            "id": 3101,
            "fieldId": 1201,
            "code": "customer_a",
            "name": "客户 A",
            "color": "#1677ff",
            "enabled": true,
            "sort": 10
          }
        ]
      }
    ]
  }
}
```

详情和分页中的 `customFields` 只包含实际已存值，按 `fieldId` 升序。历史字段或选项即使已禁用仍可回显，此时 `enabled` 可能为 `false`。

`sprintTitle` 和 `parentWorkItemTitle` 分别由 `sprintId`、`parentWorkItemId` 翻译得到；关联编号为空或记录不存在时，对应标题为 `null`。

选择类型的 `selectedOptions` 用于历史显示：

- `SINGLE_SELECT`：`value` 是一个 option id。
- `MULTI_SELECT`：`value` 是 option id 数组。
- 非选择字段：`selectedOptions` 为 `[]`。

## 9. 完整更新工作项

### 9.1 请求

```http
PUT /admin-api/project/work-item/update
Content-Type: application/json
```

请求结构与创建相同，但 `id` 必须提供：

```json
{
  "id": 1024,
  "title": "支持项目工作项前端联调",
  "description": "完整表单保存",
  "category": "REQUIREMENT",
  "priority": "MEDIUM",
  "assigneeUserId": 2,
  "projectId": 1024,
  "sprintId": 2048,
  "plannedStartTime": 1784282400000,
  "plannedEndTime": 1784887200000,
  "parentWorkItemId": null,
  "remark": null,
  "customFieldValues": [
    {"fieldId": 1201, "value": 3101},
    {"fieldId": 1202, "value": 2}
  ]
}
```

### 9.2 完整快照语义

- `customFieldValues` 不出现或为 `null`：保留当前全部自定义字段值。
- `customFieldValues` 出现且为数组：该数组是完整快照；未包含的旧值会删除。
- `customFieldValues: []`：尝试清空全部自定义字段；存在必填字段时失败。
- 重复 `fieldId`、无效/禁用字段、跨项目字段、分类不适用字段、无效用户或选项会被拒绝。
- `workItemNo` 和 `status` 不由完整更新接口修改；状态更新使用快捷更新。

存在自定义字段值时不能迁移到其他项目。修改分类后若已有值对应字段不再适用，也会被拒绝；当前不支持在同一完整更新请求中通过清空旧值绕过该校验。

### 9.3 响应

```json
{"code": 0, "msg": "", "data": true}
```

## 10. 复杂分页查询

### 10.1 请求

```http
POST /admin-api/project/work-item/page
Content-Type: application/json
```

完整示例：

```json
{
  "pageNo": 1,
  "pageSize": 20,
  "projectId": 1024,
  "keyword": "登录",
  "category": "REQUIREMENT",
  "conditionGroups": [
    {
      "conditions": [
        {"field": "status", "operator": "in", "values": ["PENDING", "DEVELOPING"]},
        {"field": "assigneeUserId", "operator": "eq", "value": "me"}
      ]
    },
    {
      "conditions": [
        {"field": "custom:1201", "operator": "eq", "value": "3101"},
        {"field": "plannedEndTime", "operator": "between",
         "value": "2026-07-01 00:00:00", "valueEnd": "2026-07-31 23:59:59"}
      ]
    }
  ],
  "sortingFields": [
    {"field": "priority", "order": "desc"},
    {"field": "plannedEndTime", "order": "asc"}
  ]
}
```

整体逻辑为：

```text
快捷条件 AND ((组 1 内全部条件) OR (组 2 内全部条件))
```

### 10.2 快捷过滤字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `keyword` | String | 工作项编号或标题模糊匹配 |
| `category` | String | 分类等值 |
| `status` | String | 状态等值 |
| `priority` | String | 优先级等值 |
| `assigneeUserId` | Long | 负责人等值 |
| `creator` | String | 创建者等值 |
| `projectId` | Long | 项目等值；使用自定义字段条件时必填 |
| `sprintId` | Long | 迭代等值 |
| `plannedEndTime` | Long[2] | 毫秒时间戳区间 |
| `createTime` | Long[2] | 毫秒时间戳区间 |

快捷过滤与动态条件始终使用 AND 连接。

### 10.3 分页与排序

- `pageNo`：从 1 开始，默认 1。
- `pageSize`：1 到 200，默认 10。
- `sortingFields`：按数组顺序形成多字段排序，`order` 使用 `asc` 或 `desc`。
- 未提供排序时默认 `id DESC`。
- 排序字段使用工作项主表 Java 属性名，例如 `id`、`workItemNo`、`title`、
  `category`、`priority`、`status`、`assigneeUserId`、`projectId`、`sprintId`、
  `plannedStartTime`、`plannedEndTime`、`parentWorkItemId`、
  `createTime`、`updateTime`。
- 不支持 `custom:<fieldId>` 自定义字段排序。

### 10.4 动态条件结构

```json
{
  "field": "status",
  "operator": "in",
  "value": null,
  "valueEnd": null,
  "values": ["PENDING", "DEVELOPING"]
}
```

| 字段 | 说明 |
|---|---|
| `field` | 固定字段编码，或 `custom:<fieldId>` |
| `operator` | 操作符编码 |
| `value` | 单值；也可作为逗号分隔的多值兼容输入 |
| `valueEnd` | `between` 的结束值 |
| `values` | `in/not_in` 等多值操作符的推荐输入 |

值规则：

- `eq/ne/gt/ge/lt/le` 使用 `value`。
- `between` 推荐使用 `value + valueEnd`；也兼容 `values` 的前两个值。
- `in/not_in` 使用 `values`；也兼容 `value: "A,B"`。
- 文本 `contains/not_contains` 使用 `value`。
- 选择/用户字段 `contains/not_contains` 使用 `values`，语义分别等同多值包含/排除。
- `is_empty/is_not_empty` 不需要任何值。
- 用户字段支持 `me`、`currentUser`、`self`，执行时解析为当前登录用户。
- `conditions` 与 `conditionGroups` 不能同时提交。
- `conditions` 中全部条件使用 AND。
- `conditionGroups` 组内 AND、组间 OR；不支持递归嵌套。
- 动态条件总数最多 20 个；空组、null 条件或非法字段/操作符会被拒绝。

### 10.5 固定查询/分组字段

| 字段编码 | 类型族 | 备注 |
|---|---|---|
| `workItemNo` | TEXT | 工作项展示编号 |
| `title` | TEXT | 标题 |
| `description` | TEXT | 描述 |
| `category` | SELECT | 分类 |
| `priority` | SELECT | 优先级 |
| `status` | SELECT | 状态 |
| `assigneeUserId` | SELECT | 负责人，用户字段 |
| `ownerUserId` | SELECT | `assigneeUserId` 别名 |
| `creator` | SELECT | 创建者，用户字段 |
| `creatorUserId` | SELECT | `creator` 别名 |
| `updater` | SELECT | 更新者，用户字段 |
| `projectId` | SELECT | 项目 |
| `sprintId` | SELECT | 迭代 |
| `plannedStartTime` | COMPARABLE | 计划开始时间 |
| `plannedEndTime` | COMPARABLE | 计划完成时间 |
| `parentWorkItemId` | SELECT | 父工作项 |
| `remark` | TEXT | 备注 |
| `createTime` | COMPARABLE | 创建时间 |
| `updateTime` | COMPARABLE | 更新时间 |

### 10.6 固定字段操作符矩阵

| 类型族 | 支持的操作符 |
|---|---|
| TEXT | `eq`, `ne`, `contains`, `not_contains`, `is_empty`, `is_not_empty` |
| SELECT | `eq`, `ne`, `in`, `not_in`, `contains`, `not_contains`, `is_empty`, `is_not_empty` |
| COMPARABLE | `eq`, `ne`, `gt`, `ge`, `lt`, `le`, `between`, `is_empty`, `is_not_empty` |

### 10.7 自定义字段操作符矩阵

自定义条件编码为 `custom:<fieldId>`，并且请求必须提供单一 `projectId`。字段必须是对该项目有效的全局字段或项目字段。

| 字段类型 | 支持的操作符 |
|---|---|
| `TEXT` | `eq`, `ne`, `contains`, `not_contains`, `is_empty`, `is_not_empty` |
| `NUMBER` | `eq`, `ne`, `gt`, `ge`, `lt`, `le`, `between`, `is_empty`, `is_not_empty` |
| `DATE_TIME` | `eq`, `ne`, `gt`, `ge`, `lt`, `le`, `between`, `is_empty`, `is_not_empty` |
| `SINGLE_SELECT` | `eq`, `ne`, `in`, `not_in`, `contains`, `not_contains`, `is_empty`, `is_not_empty` |
| `MULTI_SELECT` | `eq`, `ne`, `in`, `not_in`, `contains`, `not_contains`, `is_empty`, `is_not_empty` |
| `USER` | `eq`, `ne`, `in`, `not_in`, `contains`, `not_contains`, `is_empty`, `is_not_empty` |
| `BOOLEAN` | `eq`, `ne`, `is_empty`, `is_not_empty` |

选择字段的条件值是 option id，而不是 option code/name。后端校验 option 必须属于目标字段。查询历史数据时可以使用仍存在但已禁用的字段/选项编号；Schema 只提供当前可写的启用项。

### 10.8 空值与否定操作符

- 固定字段 `is_empty` 判断数据库值为 `NULL`；空字符串不等于空值。
- 自定义字段 `is_empty` 判断该工作项不存在目标字段值记录。
- `ne/not_in/not_contains` 不会自动包含缺失值。
- 需要“排除某值，同时包含未填写”的业务效果时，使用 OR 条件组显式组合否定条件和 `is_empty`。

### 10.9 响应

分页 `list` 中每项结构与详情接口一致，并包含 `customFields`：

```json
{
  "code": 0,
  "msg": "",
  "data": {
    "list": [
      {
        "id": 1024,
        "workItemNo": "WI-000001",
        "title": "支持项目工作项前端对接",
        "status": "PENDING",
        "sprintId": 2048,
        "sprintTitle": "第 1 迭代",
        "parentWorkItemId": 512,
        "parentWorkItemTitle": "项目工作项基础能力",
        "customFields": []
      }
    ],
    "total": 1
  }
}
```

## 11. 单字段分组统计

### 11.1 请求

```http
POST /admin-api/project/work-item/group-count
Content-Type: application/json
```

请求继承分页接口的全部筛选字段，并新增必填 `groupField`：

```json
{
  "projectId": 1024,
  "conditionGroups": [
    {
      "conditions": [
        {"field": "assigneeUserId", "operator": "eq", "value": "me"}
      ]
    }
  ],
  "groupField": "status"
}
```

自定义字段分组：

```json
{
  "projectId": 1024,
  "conditions": [
    {"field": "priority", "operator": "in", "values": ["HIGH", "URGENT"]}
  ],
  "groupField": "custom:1201"
}
```

### 11.2 响应

```json
{
  "code": 0,
  "msg": "",
  "data": {
    "groupField": "status",
    "groups": [
      {"value": "PENDING", "count": 12},
      {"value": "DEVELOPING", "count": 8},
      {"value": null, "count": 2}
    ]
  }
}
```

### 11.3 分组语义

- 分页与分组先执行完全相同的快捷过滤和动态条件标准化。
- 固定字段与自定义标量字段按实际值分组。
- 单选/多选字段按 option id 分组。
- 多选工作项会分别计入每个已选 option，因此各组 `count` 之和可能大于筛选工作项总数。
- 每一组使用唯一工作项计数，同一工作项不会在同一 option 组内重复计数。
- 未填写分组字段的工作项以 `value: null` 返回。
- 结果不分页；继承的 `pageNo`、`pageSize`、`sortingFields` 不参与统计。
- 非空组按 `count DESC`、`value ASC`，空值组始终最后。
- 不设置分组数量上限，高基数字段可能返回较长数组。
- 返回原始值，不返回字典名、用户昵称、项目名或选项名。

前端翻译建议：

| `groupField` | `value` 翻译来源 |
|---|---|
| 分类/优先级/状态 | 本文固定枚举 |
| 用户字段 | 管理后台用户字典 |
| 项目/迭代/父工作项 | 对应业务字典或缓存 |
| 自定义单选/多选 | Schema 的 `options` |
| 自定义标量 | 直接按字段类型展示 |
| 任意字段的 `null` | 显示“未填写” |

## 12. 快捷更新字段

### 12.1 请求

```http
PUT /admin-api/project/work-item/update-field
Content-Type: application/json
```

```json
{
  "id": 1024,
  "fields": [
    {"field": "priority", "value": "HIGH"},
    {"field": "plannedEndTime", "value": "2026-07-31 18:00:00"},
    {"field": "custom:1201", "value": 3101},
    {"field": "custom:1203", "value": [3201, 3202]}
  ]
}
```

`fields` 不能为空；同一字段在一次请求中不能重复。

### 12.2 可更新内置字段

| 字段 | 值类型 | 可清空 | 约束 |
|---|---|---|---|
| `title` | string | 否 | 非空，最多 128 字符 |
| `description` | string | 是 | 最多 5000 字符 |
| `category` | string | 否 | 分类枚举 |
| `priority` | string | 否 | 优先级枚举 |
| `status` | string | 否 | 状态枚举 |
| `assigneeUserId` | number/数字字符串 | 否 | 用户必须有效 |
| `projectId` | number/数字字符串 | 否 | 项目必须有效 |
| `sprintId` | number/数字字符串 | 是 | 迭代编号 |
| `plannedStartTime` | 时间字符串 | 是 | 与结束时间交叉校验 |
| `plannedEndTime` | 时间字符串 | 是 | 不得早于开始时间 |
| `parentWorkItemId` | number/数字字符串 | 是 | 同项目且不能形成循环 |
| `remark` | string | 是 | 最多 512 字符 |

可清空字段使用 JSON `null`：

```json
{
  "id": 1024,
  "fields": [
    {"field": "sprintId", "value": null},
    {"field": "custom:1202", "value": null}
  ]
}
```

`value` 属性不能缺失；JSON `null` 是存在的值，允许通过 Bean Validation 后由业务规则判断能否清空。

### 12.3 自定义字段局部 Patch

- 自定义字段名固定为 `custom:<fieldId>`。
- 一个请求可混合更新多个内置字段和多个自定义字段。
- 只替换请求中指定的自定义字段，未指定值保持不变。
- 未指定的禁用历史字段/选项不会阻塞更新，也不会被删除。
- JSON `null` 清空可选自定义字段；清空必填字段失败。
- 目标字段必须对工作项更新后的项目/分类有效且启用，用户和选项必须有效。
- 所有写入处于同一事务；任一字段失败，整个请求回滚。

项目/分类边界与完整更新一致：存在自定义值时不能迁移项目；已有值不适用于新分类时不能直接修改分类。

### 12.4 响应

```json
{"code": 0, "msg": "", "data": true}
```

## 13. 查询工作项修改动态

### 13.1 请求

```http
GET /admin-api/project/work-item/change-log/page?workItemId=1024&pageNo=1&pageSize=20
```

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `workItemId` | Long | 是 | 工作项主键编号 |
| `pageNo` | Integer | 否 | 页码，默认沿用通用分页参数 |
| `pageSize` | Integer | 否 | 每页数量，受通用分页上限约束 |

接口同时校验系统 `project:work-item:query` 权限和工作项所属项目的
`WORK_ITEM_QUERY` 权限，不能用于检索其他模块的操作日志。

### 13.2 响应示例

```json
{
  "code": 0,
  "msg": "",
  "data": {
    "list": [
      {
        "id": 9001,
        "userId": 1,
        "userName": "张三",
        "createTime": 1784786400000,
        "subType": "更新工作项",
        "action": "更新了工作项【登录异常】：【优先级】从【低】修改为【高】",
        "changes": [
          {
            "field": "priority",
            "name": "优先级",
            "oldValue": "LOW",
            "newValue": "HIGH",
            "oldDisplay": "低",
            "newDisplay": "高"
          }
        ]
      }
    ],
    "total": 1
  }
}
```

`changes` 中的固定字段编码与快捷更新字段一致，自定义字段编码为
`custom:<fieldId>`。前端优先展示 `action`；需要逐字段布局时使用
`oldDisplay`、`newDisplay`，原始值只作为辅助信息。描述是富文本字段：发生变化时
`action` 仅显示 `【描述】发生了修改`，对应 change 只返回 `field`、`name`，
不返回旧/新正文内容。

### 13.3 记录语义

- 完整更新和快捷更新都会记录；一次请求最多生成一条动态。
- 只记录前后真实变化，同值更新和多选项仅顺序变化不会生成动态。
- 动态复用系统异步操作日志，更新成功后可能存在短暂查询延迟，前端不能依赖立即可见。
- 描述富文本不会写入活动日志；其他长文本和一次请求内过多的变化会截断。该接口定位为活动流，不是完整审计账本。
- 旧日志、异常 JSON 或无结构化扩展信息时，仍返回 `action`，`changes` 为 `[]`。
- 功能上线前的历史修改不补录。

## 14. 查询工作项动态

该接口是详情页推荐使用的统一时间线，将上一节的操作记录与工作项评论合并返回。

### 14.1 请求

```http
GET /admin-api/project/work-item/activity/page?workItemId=1024&pageNo=1&pageSize=20
```

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `workItemId` | Long | 是 | 工作项主键编号 |
| `pageNo` | Integer | 否 | 页码，默认 `1` |
| `pageSize` | Integer | 否 | 每页数量，默认 `10`，最大 `200` |

为了保证两个数据源全局合并后的分页结果正确，`pageNo * pageSize` 不能超过 `200`。
例如 `pageNo=10&pageSize=20` 合法，`pageNo=11&pageSize=20` 会被参数校验拒绝。

查询同时要求系统权限 `project:work-item:query`，以及工作项真实所属项目的
`WORK_ITEM_QUERY`、`COMMENT_QUERY`。前端不传 `projectId`、评论目标类型或日志类型。

### 14.2 响应示例

```json
{
  "code": 0,
  "msg": "",
  "data": {
    "list": [
      {
        "type": "COMMENT",
        "sourceId": 7002,
        "operatorUserId": 2,
        "operatorName": "李四",
        "operatorAvatar": "https://example.com/avatar/2.png",
        "createTime": 1784887800000,
        "comment": {
          "id": 7002,
          "projectId": 100,
          "targetType": "WORK_ITEM",
          "targetId": 1024,
          "authorUserId": 2,
          "authorName": "李四",
          "authorAvatar": "https://example.com/avatar/2.png",
          "content": "好的",
          "mentionUserIds": [3],
          "replyTo": {
            "id": 7001,
            "authorUserId": 1,
            "authorName": "张三",
            "content": "请确认验收口径",
            "contentPreview": "请确认验收口径",
            "deleted": false
          },
          "edited": false,
          "deleted": false,
          "createTime": 1784887800000,
          "updateTime": 1784887800000
        },
        "operation": null
      },
      {
        "type": "OPERATION",
        "sourceId": 9001,
        "operatorUserId": 1,
        "operatorName": "张三",
        "operatorAvatar": "https://example.com/avatar/1.png",
        "createTime": 1784887200000,
        "comment": null,
        "operation": {
          "id": 9001,
          "userId": 1,
          "userName": "张三",
          "createTime": 1784887200000,
          "subType": "更新工作项",
          "action": "更新了工作项【登录异常】：【优先级】从【低】修改为【高】",
          "changes": [
            {
              "field": "priority",
              "name": "优先级",
              "oldValue": "LOW",
              "newValue": "HIGH",
              "oldDisplay": "低",
              "newDisplay": "高"
            }
          ]
        }
      }
    ],
    "total": 2
  }
}
```

评论动态为回复时，`comment.replyTo.content` 返回被回复评论的完整当前正文，
`contentPreview` 保持总长度最多 120 字符的摘要，正文过长时以 `...` 结尾；前端可分别用于展开内容和紧凑预览。
被回复评论已删除或不可查询时，`replyTo.deleted = true`，两个正文字段均为 `null`。

- `type` 固定为 `COMMENT` 或 `OPERATION`，前端必须按它选择载荷，不能通过空字段猜类型。
- 动态唯一键使用 `type + sourceId`；评论编号和操作记录编号可能相同。
- 先按 `createTime` 倒序；时间相同时评论在操作记录前，同类型再按 `sourceId` 倒序。
- `total` 是评论数与操作记录数之和，并按合并后的全局顺序截取当前页。
- 已删除评论仍返回占位，但 `content=null`、`mentionUserIds=[]`；未知操作记录结构仍保留
  `action`，并返回 `changes=[]`。
- 操作记录异步写入，刚更新工作项后可能短暂缺失。该时间线最终一致，不应用作强一致审计依据。

## 15. 发表工作项评论

### 15.1 请求

```http
POST /admin-api/project/work-item/comment/create
Content-Type: application/json

{
  "workItemId": 1024,
  "content": "请确认验收口径",
  "replyToCommentId": 7001,
  "mentionUserIds": [2, 3]
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `workItemId` | Long | 是 | 评论所属工作项编号 |
| `content` | String | 是 | Markdown 正文，非空且最多 10000 字符，不允许原始 HTML |
| `replyToCommentId` | Long | 否 | 同一工作项下未删除的评论编号 |
| `mentionUserIds` | Long[] | 否 | 当前项目成员编号；去重并排除作者后最多 20 人 |

请求不接受 `targetType`、`targetId` 或 `projectId`。后端固定目标为
`WORK_ITEM + workItemId`，并复用通用评论的回复、@、归档项目与通知规则。
此接口只要求项目 `COMMENT_CREATE`，不要求系统菜单权限。

### 15.2 响应

```json
{"code": 0, "msg": "", "data": 7002}
```

`data` 是新评论编号。提交成功后可重新查询统一动态；评论立即持久化，@ 通知在事务提交后异步处理。

## 16. 常见业务错误

| code | 含义 | 前端建议 |
|---|---|---|
| `1050000000` | 项目不存在 | 刷新项目上下文 |
| `1050000003` | 项目已归档 | 禁用编辑并提示 |
| `1050001000` | 工作项不存在 | 关闭详情并刷新列表 |
| `1050001001` | 计划完成时间早于开始时间 | 定位时间字段 |
| `1050001002` | 父工作项无效 | 清空并重新选择父项 |
| `1050001003` | 存在子工作项，不能移动或删除 | 提示先处理子项 |
| `1050001004` | 快捷更新字段不支持 | 检查 `field` 编码 |
| `1050001005` | 快捷更新字段值无效 | 定位响应消息中的字段 |
| `1050001006` | 查询字段不支持 | 检查固定字段或 `custom:<id>` |
| `1050001007` | 查询条件无效 | 检查操作符、值或条件组 |
| `1050001011` | 自定义字段不存在 | 刷新 Schema |
| `1050001017` | 自定义字段值无效 | 刷新 Schema/选项后重试 |
| `1050001018` | 自定义字段必填 | 定位 Schema 必填字段 |
| `1050001019` | 存在自定义值，不能修改所属项目 | 禁止直接迁移项目 |
| `1050001020` | 新分类不适用于已有自定义字段 | 保持原分类或先处理历史值 |
| `1050002002` | 评论目标不存在 | 刷新或关闭工作项详情 |
| `1050002003` | 回复评论无效 | 清除回复对象后重试 |
| `1050002006` | @ 用户不是当前项目成员 | 刷新项目成员列表 |
| `1050002008` | 评论含原始 HTML | 改用 Markdown 文本 |
| `1050002009` | 评论正文无效 | 检查正文非空且不超过 10000 字符 |
| `1050002010` | 单条评论 @ 超过 20 人 | 减少被 @ 用户后重试 |

Bean Validation、认证、权限和通用系统错误使用全局错误码。前端应以 `code != 0` 作为失败判断，并保留 `msg` 作为最终提示。

## 17. 前端实现检查清单

- [ ] 所有路径使用 `/admin-api` 前缀和管理后台通用请求头。
- [ ] 项目/分类变化时重新加载 Schema。
- [ ] 控件由 `fieldType` 决定，选项值提交 option id。
- [ ] 创建和完整更新区分 `customFieldValues: null` 与数组快照。
- [ ] 表格/看板快捷操作使用 `update-field`，自定义字段使用 `custom:<fieldId>`。
- [ ] 动态查询同时只提交 `conditions` 或 `conditionGroups` 之一。
- [ ] 自定义字段查询/分组始终提交 `projectId`。
- [ ] 动态条件总数不超过 20，空值查询不提交 `value`。
- [ ] 分页排序只使用工作项主表属性，不提交自定义字段。
- [ ] 分组值在前端翻译，多选分组总数不与分页 `total` 强行相等。
- [ ] 普通时间使用毫秒时间戳，动态时间条件使用格式化字符串。
- [ ] 详情允许回显禁用历史字段/选项，但新写入只使用 Schema 中启用项。
- [ ] 修改成功后允许动态列表短暂未刷新；展示优先使用 `action`，不要自行反向解析文本。
- [ ] 详情页统一时间线按 `type` 渲染，并用 `type + sourceId` 作为列表键。
- [ ] 统一动态只翻到 `pageNo * pageSize <= 200` 的范围，超出后禁用继续加载。
- [ ] 发表评论只提交工作项编号与评论字段，不提交目标类型或项目编号。

## 18. 项目工时接口

工时是独立实体。所有接口使用 `/admin-api` 前缀，并按本文第 3 节返回
`{ code, data, msg }`。工作项响应中的 `estimatedHours`、`actualHours` 是后端维护的只读物化合计；创建、完整更新和字段更新工作项均不定义、不写入这些字段，也不再定义或依赖废弃的 `estimatedWorkload`。

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/admin-api/project/worklog/create` | 创建预估或实际工时 |
| PUT | `/admin-api/project/worklog/update` | 更新工时数、类别、描述、负责人或工作日期 |
| DELETE | `/admin-api/project/worklog/delete?id={id}` | 逻辑删除工时 |
| GET | `/admin-api/project/worklog/get?id={id}` | 查询单条工时 |
| POST | `/admin-api/project/worklog/page` | 分页查询项目工时 |
| POST | `/admin-api/project/worklog/summary` | 查询项目工时总计和单维度分组 |

创建请求字段为 `workItemId,type,hours,category,description,ownerUserId,workDate`。`type` 取
`ESTIMATED` 或 `ACTUAL`；实际工时必须填写 `workDate`，预估工时可不填写。`hours` 范围为
`0..99999999.99`，最多两位小数。`category` 从 `project_worklog_category` 字典获取。

### 18.1 工作项范围

`POST /admin-api/project/worklog/page` 和
`POST /admin-api/project/worklog/summary` 使用完全相同的范围字段：

| 字段 | 类型 | 规则 |
|---|---|---|
| `workItemId` | Long | 可选。不传时保持原有项目级查询。 |
| `workItemScope` | String | 仅可与 `workItemId` 一起传入；值仅为 `SELF`、`DESCENDANTS` 或 `SELF_AND_DESCENDANTS`。 |

| 请求组合 | 服务端范围 |
|---|---|
| 未传 `workItemId`，未传 `workItemScope` | 当前项目的全部可见工时，保持旧语义。 |
| 传 `workItemId`，未传 `workItemScope` | `SELF`，仅该根工作项。 |
| 传 `workItemId`，`workItemScope=SELF` | 仅该根工作项。 |
| 传 `workItemId`，`workItemScope=DESCENDANTS` | 根项的全部递归子项，不包含根项。 |
| 传 `workItemId`，`workItemScope=SELF_AND_DESCENDANTS` | 根项本身及全部递归子项。 |
| 未传 `workItemId` 但传 `workItemScope` | 非法请求，返回标准错误包络。 |

服务端先一次性解析范围内、当前项目且当前租户可见的工作项，再应用其余筛选、排序、分页、`total` 和汇总。前端不得逐个查询子项后合并结果。相同筛选条件下，分页记录、`page.total` 与汇总使用同一数据口径。

不存在或跨项目的根项不会泄露数据。`DESCENDANTS` 无后代时，分页返回空 `list` 与 `total = 0`；汇总返回 `estimatedHours = 0.00`、`actualHours = 0.00` 和空 `groups`。

### 18.2 工时分页

分页和汇总共享以下筛选字段：
`projectId,sprintId,workItemId,workItemScope,type,category,ownerUserId,workDateStart,workDateEnd`。
分页额外需要 `pageNo`、`pageSize`，并按 `workDate DESC, id DESC` 返回。

```http
POST /admin-api/project/worklog/page
Content-Type: application/json
```

```json
{
  "projectId": 1024,
  "workItemId": 1001,
  "workItemScope": "DESCENDANTS",
  "type": "ACTUAL",
  "workDateStart": "2026-07-01",
  "workDateEnd": "2026-07-31",
  "pageNo": 1,
  "pageSize": 20
}
```

```json
{
  "code": 0,
  "msg": "",
  "data": {
    "list": [
      {
        "id": 5001,
        "projectId": 1024,
        "workItemId": 1003,
        "workItemNo": "WI-000003",
        "workItemTitle": "实现接口",
        "type": "ACTUAL",
        "hours": 2.50,
        "category": "DEVELOPMENT",
        "description": "接口实现",
        "ownerUserId": 1,
        "ownerUserName": "张三",
        "workDate": "2026-07-28",
        "createTime": 1785196800000,
        "updateTime": 1785196800000
      }
    ],
    "total": 1
  }
}
```

### 18.3 工时汇总

汇总请求不含分页字段，可选 `groupBy`：`WORK_ITEM`、`OWNER`、`CATEGORY`、`SPRINT`。不传
`groupBy` 时仍返回总计，`groups` 为 `[]`。每个分组和总计都分别返回预估与实际工时；`value` 是分组原始值。`WORK_ITEM` 返回“工作项编号 - 标题”、`OWNER` 返回负责人昵称、`SPRINT` 返回迭代标题作为 `displayName`；`CATEGORY` 和空/失效关联值的 `displayName` 可能为 `null`，前端按工时类别字典显示。

```http
POST /admin-api/project/worklog/summary
Content-Type: application/json
```

```json
{
  "projectId": 1024,
  "workItemId": 1001,
  "workItemScope": "DESCENDANTS",
  "type": "ACTUAL",
  "workDateStart": "2026-07-01",
  "workDateEnd": "2026-07-31",
  "groupBy": "WORK_ITEM"
}
```

```json
{
  "code": 0,
  "msg": "",
  "data": {
    "estimatedHours": 0.00,
    "actualHours": 6.50,
    "groupBy": "WORK_ITEM",
    "groups": [
      {
        "value": "1003",
        "displayName": "WI-000003 - 实现接口",
        "estimatedHours": 0.00,
        "actualHours": 6.50
      }
    ]
  }
}
```

### 18.4 工作项只读合计

工作项 `get` 和 `page` 响应直接返回持久化的只读 `estimatedHours`、`actualHours`。工时创建、编辑、删除与这两个合计在同一事务内同步；成功后重新读取工作项或调用工时汇总即可获得更新后的值。带范围、日期、类别、负责人或迭代筛选的汇总仍基于工时明细，不应用工作项合计替代筛选后的统计。

通过 `GET /admin-api/project/project/my-permissions?projectId={projectId}` 获取项目权限。普通成员在拥有 `WORKLOG_CREATE` 且工时 `ownerUserId` 为当前用户时可编辑、删除自己的记录；拥有 `WORKLOG_MANAGE` 时可维护项目内任意记录。按钮还需同时满足管理后台系统权限：编辑为 `project:worklog:update`，删除为 `project:worklog:delete`；后端仍会再次授权校验。
工作项存在工时时不能跨项目移动，删除工作项会同时逻辑删除其工时。
