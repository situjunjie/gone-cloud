# 应用环境关联变量组与简易列表接口

## Goal

为应用环境提供可识别的环境名称和变量组关联，并提供按应用查询变量组的简易列表接口，供管理端选择和配置环境变量。

## Confirmed Facts

- `ApplicationEnvDO` 当前仅保存 `appId`、`envId`、排序、快照、状态和备注。
- `ApplicationEnvRespVO` 已有 `envName` 响应字段，但来源是 `EnvironmentDO.envName` 的翻译，不是应用环境自身字段。
- `VariableGroupDO` 当前保存 `applicationEnvId`，应用环境作用域变量组通过该字段参与唯一性校验和生效变量计算。
- 变量组管理已有分页接口，尚无 simple-list 接口。
- 应用环境由应用的 `/update-envs` 批量替换接口维护。

## Requirements

1. 应用环境实体增加环境名称字段。
2. 应用环境实体增加变量组 ID 关联字段。
3. 变量组实体移除应用环境 ID 关联，应用环境作用域变量组改由应用环境记录关联。
4. 调整变量组新增、修改、唯一性校验、分页/响应及生效变量逻辑以匹配新的关联方向。
5. 提供按应用 ID 查询应用变量组的 simple-list 接口，返回前端下拉选择所需的精简字段。
6. 应用环境新增/更新请求和响应应能读写新增字段；现有应用环境更新流程保持可用。

## Acceptance Criteria

- 应用环境读写链路可持久化并返回环境名称和变量组 ID。
- 变量组数据模型不再依赖 `applicationEnvId`，应用环境作用域变量组可通过应用环境的 `variableGroupId` 正确解析。
- 按应用 ID 调用 simple-list 接口返回该应用可用变量组，且权限、租户隔离和排序符合现有管理接口约定。
- 全量后端编译通过，相关服务/控制器测试或已有测试通过。

## Out Of Scope

- 不改变全局变量组和应用级变量组的业务语义。
- 不新增变量组批量复制、排序或复杂筛选能力。

## Technical Notes

- 环境名称采用应用级自定义展示名称，`envId` 继续作为基础环境引用。
- 每个应用环境关联一个变量组，字段名为 `variableGroupId`。
- 变量组 `APPLICATION_ENV` 作用域改为通过 `dev_application_env.variable_group_id` 反查；全局和应用级作用域保持原语义。
- 需要同步更新 MySQL DevOps 建表/迁移脚本；其他数据库仅在已有 DevOps 表结构中存在时同步。
