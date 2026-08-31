# 新增应用环境单独更新接口

## Goal

为 DevOps 应用提供按单个应用环境关系更新配置的接口，避免调用方为修改一个环境而提交整组环境列表。

## Background

当前仅有 `PUT /devops/application/update-envs`，该接口接收整个环境列表并执行替换；没有以应用环境关系编号为目标的单条更新接口。现有单条配置字段和校验逻辑已在 `ApplicationServiceImpl.updateApplicationEnvs` 中使用。

## Requirements

- 新增管理后台接口 `PUT /devops/application/update-env`，请求必须包含应用环境关系编号 `id`，并支持更新名称、变量组、显示顺序、状态和备注。
- 单条更新必须校验关系存在；请求不得改变关系所属应用或底层环境。
- 变量组必须继续遵守现有应用级变量组范围校验；非法变量组返回现有业务错误码。
- 保持现有 `devops:application:update` 权限标识和 `CommonResult<Boolean>` 响应约定。
- 保留现有批量替换接口的行为不变。

## Out Of Scope

- 不新增数据库字段或迁移。
- 不允许通过该接口修改 `appId`、`envId`、当前快照或流水线数据。
- 不修改前端调用和批量替换接口契约。

## Acceptance Criteria

- [x] OpenAPI/Controller 暴露 `PUT /devops/application/update-env`，成功返回 `{"code":0,"data":true}`。
- [x] 给定存在的应用环境关系，提交合法配置后仅该关系的可编辑字段被更新。
- [x] 关系不存在时返回 `APPLICATION_ENV_NOT_EXISTS`，且不执行持久化更新。
- [x] 变量组不存在、非应用级或属于其他应用时返回 `VARIABLE_GROUP_SCOPE_INVALID`，且不执行持久化更新。
- [x] 现有 `update-envs` 相关测试继续通过，并新增单条更新 Service 测试覆盖成功和失败路径。
