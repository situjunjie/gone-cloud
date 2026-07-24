# 实施计划：工作项字段变更动态

任务已进入实现阶段，以下清单记录实际执行结果。

## 1. 进入实现前的确认

- [x] 同时覆盖 `update-field` 和完整 `update`。
- [x] 一次请求记录一条动态，内部包含多个字段变化。
- [x] 使用可读标签并截断超长文本，定位为活动动态。
- [x] 接受异步最终一致及短暂查询延迟。
- [x] 不做历史回填。

## 2. 推荐实现顺序

1. [x] 在 project-api 增加日志 type/subType/模板常量。
2. [x] 在 project-server 增加工作项变更快照/比较组件，覆盖固定字段、自定义字段、枚举、用户、选项、多选集合和长文本策略。
3. [x] 在完整更新和快捷更新入口接入 `@LogRecord` 与 `LogRecordContext`，无有效变更时不记录动态。
4. [x] 增加按工作项 ID 查询的 ReqVO/RespVO、Service/Controller，复用 `OperateLogApi`，并添加双层权限校验和分页。
5. [x] 本次不新增索引；沿用现有 `type + bizId` 正确性查询，待生产数据量和 Explain 证明需要后再单独增加多数据库脚本。

## 3. 测试清单

- 纯比较测试：固定字段新增/修改/清空；自定义文本、数字、时间、布尔、用户、单选、多选；同值和多选重排不产生变更。
- 服务测试：混合内置/自定义字段只写一次日志上下文；校验失败、事务回滚不产生成功动态；更新完整接口（若纳入）与快捷更新结果一致。
- 查询测试：固定 type + workItemId；无权限、跨租户、分页、倒序；旧日志无 extra 时保持兼容。
- 集成测试：验证 `OperateLogApi` RPC 映射、用户昵称转换、异步日志最终可见，以及复合索引 SQL（若新增）。
- 质量命令：
  - `mvn -pl yudao-module-project/yudao-module-project-server -am -DskipTests compile`
  - `mvn -pl yudao-module-project/yudao-module-project-server -am test`
  - `git diff --check`

## 4. 风险点与回滚点

- 风险文件：`ProjectWorkItemServiceImpl`、project 工作项 service test、project controller VO、project-api 日志常量、system 操作日志 SQL。
- 第一回滚点：只移除 project 的 `@LogRecord`/日志上下文接入，保留查询代码但返回空动态，业务更新不受影响。
- 第二回滚点：移除新增查询 endpoint 和 type 映射；既有 system 操作日志模块不回滚、不改动。
- SQL 索引回滚必须提供各数据库对应的 drop index 脚本或运维变更说明。

## 5. 实现后质量门禁

- 运行 `trellis-check`，确认 spec、数据流、权限、租户、事务和兼容性检查通过。
- 检查 action/extra 长度边界、日志失败降级和异步延迟文档。
- 确认没有把 project 权限校验放到 system 模块，也没有让 project 直接访问 system mapper。
- `mvn -pl yudao-module-project/yudao-module-project-server -am -Dtest=WorkItemChangeLogServiceImplTest,ProjectWorkItemServiceImplTest,ProjectMethodSecurityTest -Dsurefire.failIfNoSpecifiedTests=false test`：通过，24 个测试。
- `mvn -pl yudao-module-project/yudao-module-project-server -am test`：通过，116 个测试。
- `git diff --check`：通过。
- 未执行提交；按用户授权边界保留工作区变更。
