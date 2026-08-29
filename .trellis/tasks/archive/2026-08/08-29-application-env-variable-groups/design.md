# 技术设计

## 边界与数据流

- `ApplicationEnvDO` 增加 `name`、`variableGroupId`；对应 Save/Resp VO、MapStruct 转换和 `dev_application_env` 表字段。
- `VariableGroupDO`、Save/Resp/Page VO 删除 `applicationEnvId`。
- `VariableGroupMapper` 增加按应用查询变量组的命名查询，按 `app_id` 过滤并按 id 排序；环境作用域生效查询通过应用环境 ID 先读取关联变量组，再按变量组 ID 读取。
- `VariableGroupService` 增加 `getVariableGroupSimpleList(Long appId)`，校验应用存在并返回精简 VO（id、name、scope、appId、status、remark，变量 JSON 不下发）。
- `VariableGroupController` 暴露 `GET /devops/variable-group/simple-list?appId=`，复用 query 权限。

## 兼容与迁移

- 迁移脚本为 `dev_application_env` 添加 `name`、`variable_group_id`，并删除 `dev_variable_group.application_env_id`；现有环境变量组数据需要在迁移时根据旧关联回填对应应用环境的 `variable_group_id`，无法唯一回填的记录保留为待人工处理。
- 应用环境名称为空时，响应可继续通过环境翻译字段提供基础环境名称作为展示兜底。
- 应用级/全局变量组的 `appId` 哨兵值和现有唯一性规则保持不变。

## 风险

- 删除旧列属于破坏性 schema 变更，必须先执行回填再删列，并在发布前备份。
- 单个变量组关联意味着同一环境不能同时挂载多个环境级变量组；这是本次需求明确的约束。
