# 技术设计：工作项字段变更动态

## 1. 结论摘要

现有 `@LogRecord`（`bizlog-sdk 3.0.6`）可以满足“工作项活动动态”的 MVP，但不能单独满足严格的、逐字段可检索且强一致的审计账本。

推荐复用方式：

1. 继续使用 `@LogRecord` 的 AOP、操作者/请求上下文、统一 `system_operate_log` 持久化和现有 `OperateLogApi` 查询通道。
2. 在 project 模块的工作项更新事务内，基于更新前后的工作项快照生成工作项专用变更摘要，并通过 `LogRecordContext` 注入注解模板。
3. 将字段变更以一次请求一条动态记录，`action` 存可直接展示的摘要，`extra` 存版本化的紧凑 `changes[]` JSON（若最终只需要文本动态，可不启用结构化 `extra`）。
4. 在 project 模块增加按工作项 ID 查询的受权限保护分页接口，内部复用 `OperateLogApi` 的 `type + bizId` 精确查询。

如果需求升级为“每个字段独立行、完整保存超长旧值/新值、写入必须与业务事务同进同退、不可丢失/不可乱序”，应改为工作项专用变更表或 outbox/event 方案；不要继续扩展 `system_operate_log.action/extra`。

## 2. 代码证据与适配性评估

### 2.1 已具备的能力

- `@LogRecord` 支持 `type`、`subType`、`bizNo`、成功模板、`extra`、`condition`，并支持通过 `LogRecordContext.putVariable` 注入运行时值。
- SDK 的 `DiffParseFunction` 支持在上下文放入旧对象后执行 `_DIFF`；CRM/System 已用 `DiffLogField` 和 `_DIFF` 记录普通对象更新。
- `YudaoOperateLogConfiguration` 将 mzt 服务替换为项目自己的 `LogRecordServiceImpl`；该实现补全用户、请求、trace 信息后，通过 `OperateLogCommonApi#createOperateLogAsync` 异步写入统一操作日志。
- system `OperateLogMapper` 已支持 `type + bizId` 精确查询，`OperateLogApi` 已作为跨模块查询契约，CRM 已有同类 controller 适配模式。
- `system_operate_log` 已包含 `user_id`、`type`、`sub_type`、`biz_id`、`action`、`extra`、`create_time` 等动态展示所需字段。

### 2.2 不能直接复用的部分

- 工作项快捷更新的请求是动态 `fields[]`，其中既有固定字段，也有 `custom:<fieldId>`；请求 VO 没有固定属性，无法直接套用 `_DIFF{#updateReqVO}` 和 `@DiffLogField`。
- 自定义字段值按字段类型落在多列/选项关系表中，且 `updateValues` 采用局部物理替换；日志必须比较规范化后的前后快照，否则会把选项顺序变化、重复写入或同值覆盖误判为变更。
- 当前 `LogRecordServiceImpl#queryLog/queryLogByBizNo` 明确抛出 `UnsupportedOperationException`，查询必须走 system API/Mapper，不能调用 mzt 服务内置查询。
- `LogRecordServiceImpl` 使用异步 RPC；`@EnableLogRecord` 默认 `joinTransaction=false`，因此日志是最终一致，快速查询可能暂时看不到记录，且业务事务提交失败与日志插入时序没有强保证。
- `system_operate_log.action`/`extra` 长度有限（MySQL 分别为 2000 字符），不适合保存任意长度描述的完整前后值。

## 3. 推荐数据流

```text
PUT /project/work-item/update-field
  -> Controller: 现有工作项更新权限
  -> ProjectWorkItemServiceImpl.updateWorkItemField (事务)
       -> 读取 oldWorkItem + oldCustomSnapshot
       -> 解析/校验/执行 built-in 与 custom 更新
       -> 读取 newCustomSnapshot 或由规范化结果构造 new snapshot
       -> 计算 effective changes[]（同值不记录，多选按集合比较）
       -> LogRecordContext.putVariable(changeSummary/changeExtra/hasChanges)
  -> @LogRecord 在方法成功返回后解析模板
  -> LogRecordServiceImpl -> 异步 OperateLogCommonApi -> system_operate_log
  -> GET /project/work-item/change-log/page?workItemId=...
       -> Controller 校验 WORK_ITEM_QUERY
       -> OperateLogApi(type=PROJECT_WORK_ITEM, bizId=id)
       -> 映射为工作项动态 RespVO
```

## 4. 写入设计

### 4.1 注解契约

在 project 的 `LogRecordConstants`（建议放入 project-api，供 controller 映射复用）集中定义：

- `PROJECT_WORK_ITEM_TYPE = "项目工作项"`
- `PROJECT_WORK_ITEM_UPDATE_SUB_TYPE = "更新工作项"`
- 成功模板引用 `{{#workItemChange.summary}}`
- `bizNo = "{{#updateReqVO.id}}"`
- `condition = "{{#workItemChange.changed}}"`
- `extra = "{{#workItemChange.extra}}"`

不要把动态字段名直接拼到注解常量中，也不要把请求 `fields[]` 直接作为 `_DIFF` 输入。

### 4.2 快照和变更计算

- 固定字段：使用 `ProjectWorkItemUpdateFieldEnum` 的字段元数据，比较 `oldWorkItem` 与应用请求后的 `updatedWorkItem`；沿用服务当前的“移除未变化字段”规则。
- 自定义字段：在更新前、更新后分别通过现有批量读取能力得到规范化值；字段名来自 `WorkItemFieldDO.name`，单选/多选显示来自选项名，用户类型按批量 `AdminUserApi` 解析昵称。
- 空值、数字、时间、布尔、用户 ID、单选/多选必须在同一规范化层比较；多选按无序集合比较，避免仅排序变化产生动态。
- 只把真正变化的项放入 `changes[]`；没有有效变化时 `hasChanges=false`，注解不写日志。
- `changeSummary` 面向活动流展示，建议格式为“更新了工作项【标题】: 【优先级】从【低】修改为【高】；…”。
- `changeExtra` 使用 `schemaVersion=1`，至少包含 `field/code/name/oldValue/newValue`；显示值与原始值分开时使用 `oldDisplay/newDisplay`。长文本必须截断或脱敏，不能突破统一日志列长度。

### 4.3 更新入口

确认同时覆盖 `updateWorkItemField` 和现有 `updateWorkItem`，二者复用同一变更计算器，避免完整编辑接口绕过历史记录。创建、删除不在本次范围；更新引起的项目迁移属于字段变化，随更新动态记录。

### 4.4 事务和异常策略

- 保持现有业务事务边界，不将日志失败抛回工作项更新；这与当前共享框架的非 join-transaction 行为一致。
- 接受异步落库的短暂可见性延迟；查询接口和前端刷新不能假设写入立即可见。
- 若要求“业务提交后必有日志”，应新增 after-commit/outbox 机制并配套重试、幂等键、补偿查询，不建议仅修改一个模块的注解。

## 5. 查询设计

### 5.1 接口

建议新增：

```http
GET /admin-api/project/work-item/change-log/page
  ?workItemId=1024&pageNo=1&pageSize=20
```

Controller 使用：

- `project:work-item:query`
- `@projectSecurity.hasWorkItemPermission(#workItemId, 'WORK_ITEM_QUERY')`

Service/Controller 通过 `OperateLogApi#getOperateLogPage` 传入固定 `type=PROJECT_WORK_ITEM`、`bizId=workItemId`，防止调用方借该接口查询其他模块日志。

### 5.2 响应

建议返回 `PageResult<WorkItemChangeLogRespVO>`，至少包括：

- `id`, `userId`, `userName`, `createTime`
- `subType`, `action`
- `changes`（能解析 `extra.schemaVersion=1` 时返回；旧数据或解析失败时为空）

排序沿用 system mapper 的 `id DESC`。接口分页，不采用 CRM 操作日志的“不分页默认全量”做法，避免工作项高频更新造成无界响应。

### 5.3 索引和多租户

- 现有日志 mapper 已能完成正确性查询；如果工作项动态量明显增长，新增 `system_operate_log(type, biz_id, create_time, id)` 复合索引。
- schema 变更必须同步项目维护的各数据库脚本；不要只改 MySQL 而遗漏其他引擎。
- system 日志表已有 `tenant_id`，查询仍必须在当前租户上下文中执行，并用工作项权限做第二层业务隔离。

## 6. 共享框架兼容性

- 不改变 `YudaoOperateLogConfiguration`、`LogRecordServiceImpl` 的既有行为，不修改 CRM/System/DevOps 注解。
- project 只新增自己的 type/subType、变更快照和查询适配。
- 只有在确认需要 after-commit、结构化大字段或严格顺序后，才另立共享框架改造；该改造应单独任务评审。

## 7. 风险、发布和回滚

| 风险 | 处理 |
|---|---|
| 日志 RPC/解析失败影响业务 | 保持当前非 join-transaction，记录错误日志，不阻断更新 |
| 日志异步延迟 | 接口文档标明最终一致；前端允许空列表后重试/刷新 |
| action/extra 超长 | 统一截断并保留 `truncated` 标记；严格审计场景转专用表 |
| 自定义字段 schema/option 已改名 | 动态写入时固化字段名和显示值，不在查询时重新解析当前 schema |
| 历史旧日志无结构化 extra | 查询返回 action，`changes=[]`，不做反向文本解析 |
| type/bizId 查询变慢 | 观察日志量后增加复合索引，按数据库引擎补脚本 |

默认不做历史回填；上线前的变更不应伪造为新动态。

## 8. 专用变更表的升级条件

出现以下任一要求时，应放弃“仅复用操作日志”的方案：每个字段必须独立分页/筛选；保存完整超长文本；严格与业务事务原子提交；需要按字段、旧值、新值、版本做统计或回放；需要保证高并发更新的业务顺序。此时建议 `proj_work_item_change_log`（`TenantBaseDO`）按字段行存储，或使用事务 outbox，再由查询接口直接读取。
