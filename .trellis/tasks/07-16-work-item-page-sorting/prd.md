# 工作项分页支持参数排序

## Goal

为项目工作项分页接口 `getWorkItemPage` 增加由请求参数控制的排序能力，使调用方可以按工作项主表字段指定升序或降序，同时保持现有默认顺序兼容。

## Background

- `ProjectWorkItemPageReqVO` 当前继承普通 `PageParam`，没有暴露仓库统一的 `sortingFields` 参数。
- `ProjectWorkItemMapper.selectPage` 当前固定执行 `id DESC`。
- 公共框架已有 `SortablePageParam`、`SortingField` 与 `BaseMapperX` 的排序分页支持，参数格式为字段名加 `asc` / `desc`。

## Requirements

- `ProjectWorkItemPageReqVO` 使用公共可排序分页参数，接受有序的 `sortingFields` 列表。
- 每个排序项使用工作项主表的 Java 驼峰字段名和 `asc` / `desc` 排序方向；多个排序项按请求列表顺序生效。
- 未传或传空 `sortingFields` 时继续按 `id DESC` 排序，避免改变现有分页结果。
- 排序实现复用公共 MyBatis 分页能力，不在 Controller 或 Service 中重复构造排序 SQL。
- 保持现有筛选条件、条件组、自定义字段装配及响应结构不变。

## Acceptance Criteria

- [x] `getWorkItemPage` 请求可提交 `sortingFields`，并按指定字段和方向生成排序分页查询。
- [x] 多个排序字段保持请求顺序。
- [x] 未指定排序参数时，查询仍包含 `id DESC` 默认排序。
- [x] 现有工作项 mapper 与 service 测试保持通过，并新增聚焦测试覆盖参数排序和默认排序。
- [x] `yudao-module-project-server` 的相关测试及编译通过。

## Out Of Scope

- 自定义字段值排序。
- 前端页面排序交互改造。
- 修改公共排序参数或公共 MyBatis 排序机制。
