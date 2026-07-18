# 项目工作项自定义字段 Schema 接口

## Goal

为工作项创建/编辑页面提供运行时 Schema 查询接口，使调用方在提交
`ProjectWorkItemSaveReqVO.customFieldValues` 前，能够获知当前项目和工作项分类实际可填写的自定义字段、字段类型、必填规则及选择项。

## Background

- 工作项创建接口接收 `List<WorkItemCustomFieldValueReqVO>`，但当前 `ProjectWorkItemController` 没有面向工作项录入场景的 Schema 接口。
- 自定义字段存在 `GLOBAL` 与 `PROJECT` 两种 scope；当前 `WorkItemFieldMapper#selectListByEffectiveProjectId`
  已定义二者对指定项目的有效范围和统一排序规则。
- 现有 `/project/work-item-field/list` 是字段配置管理列表，会返回禁用字段和禁用选项，不适合作为新值写入的运行时 Schema。
- 字段可通过 `applicableCategory` 限定适用的工作项分类；创建工作项时 `category` 为必填字段。

## Requirements

### API 契约

- 在 `ProjectWorkItemController` 新增 `GET /project/work-item/schema`。
- 请求参数包含必填的 `projectId`，以及可选的 `category`；传入 `category` 时必须属于
  `ProjectWorkItemCategoryEnum`。
- 接口复用 `project:work-item:query` 权限。
- 响应复用 `WorkItemFieldRespVO` 列表，使调用方可按 `fieldId` 构造
  `WorkItemCustomFieldValueReqVO`，并获得 scope、code、name、fieldType、required、sort、configJson 和 options。

### Schema 语义

- 合并租户全局字段与当前项目字段，scope 判定必须复用现有有效项目规则。
- 仅返回启用字段，禁用字段不得用于新的工作项字段值写入。
- 传入 `category` 时，仅返回适用于全部分类或适用于该分类的字段；未传入时返回项目下全部启用字段。
- 选择类型字段只返回启用选项；禁用选项保留给历史值回显和字段管理接口，但不能出现在新值 Schema 中。
- 字段保持 `sort`、`id` 升序，选项保持 `sort`、`id` 升序。
- 查询前校验项目可用性，行为与现有字段列表接口一致。

### 兼容性

- 不改变 `/project/work-item-field/list` 与 `/global/list` 的管理语义。
- 不修改现有工作项创建请求结构、字段/选项存储结构和 scope 唯一性规则。
- 不新增数据库表或迁移脚本。

## Acceptance Criteria

- [x] 调用 `/project/work-item/schema?projectId=<id>` 可获得全局与项目级启用字段的合并列表。
- [x] 传入合法 `category` 后，只返回适用于该分类或适用于全部分类的字段。
- [x] 禁用字段和禁用选项不会出现在 Schema 响应中。
- [x] 返回字段和选项的排序稳定，并包含构造 `WorkItemCustomFieldValueReqVO` 所需元数据。
- [x] 非法分类由参数校验拒绝，不可用项目沿用现有项目业务校验。
- [x] 原字段管理列表仍能返回禁用字段和禁用选项，历史管理/回显语义不受影响。
- [x] `WorkItemFieldServiceImplTest` 覆盖 scope 合并、分类过滤和启用过滤，project server 聚焦测试通过。

## Out of Scope

- 内置工作项字段的完整表单 Schema。
- 前端页面改造或 API 客户端封装。
- 字段覆盖、同编码优先级合并；现有规则已禁止项目字段与全局字段编码冲突。
- 为历史工作项回显过滤禁用字段或选项。
