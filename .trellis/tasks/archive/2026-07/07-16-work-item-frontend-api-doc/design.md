# 工作项前端接口对接文档：设计

## 1. 交付物

在 `yudao-module-project/yudao-module-project-server/docs/work-item-frontend-api.md` 新增一份前端对接手册。文档与 owning module 同目录维护，接口变更时可随代码一并评审。

同时修正 `ProjectWorkItemPageReqVO.Condition` 的 Swagger 操作符和用户令牌描述，使生成式 API 文档与实际行为一致；不修改接口运行逻辑。

## 2. 文档边界

文档覆盖以下管理后台接口，统一使用 `/admin-api` 前缀：

1. `GET /project/work-item/schema`
2. `POST /project/work-item/create`
3. `GET /project/work-item/get`
4. `PUT /project/work-item/update`
5. `POST /project/work-item/page`
6. `POST /project/work-item/group-count`
7. `PUT /project/work-item/update-field`

删除、字段配置管理和查询视图 CRUD 不属于本次主文档范围，只在必要处说明其与当前契约的关系。

## 3. 内容结构

### 通用契约

- `/admin-api` 前缀、认证和权限。
- `CommonResult<T>` 与 `PageResult<T>` 返回包络。
- Long 编号、时间戳和动态条件时间字符串的格式差异。
- 工作项分类、优先级、状态、自定义字段类型和 scope 枚举。

### Schema 与写入

- Schema 的全局/项目字段合并、分类过滤、启用过滤和排序。
- 自定义字段类型到 JSON 值形态的映射。
- 创建与完整更新的 `customFieldValues` 完整快照语义。
- 详情中的 `customFields`、`value` 与 `selectedOptions` 回显语义。
- 快捷更新的 `custom:<fieldId>` 局部 Patch 和 JSON `null` 清空规则。

### 查询与聚合

- 快捷过滤参数、分页、排序。
- 固定字段和自定义字段编码表。
- 固定/自定义字段操作符矩阵。
- `conditions` 平铺 AND 与 `conditionGroups` 组内 AND/组间 OR 的互斥规则。
- `value`、`valueEnd`、`values` 使用规则和 `me/currentUser/self` 令牌。
- group-count 与 page 共用筛选条件、分组原始值、空值组、多选重复进入不同组及非分页语义。

### 联调指南

- 推荐调用顺序与 Schema 刷新时机。
- 完整可执行 JSON 示例。
- 业务错误码和前端处理建议。
- 前端实现检查清单。

## 4. 证据优先级

文档结论按以下优先级取证：

1. 当前 Controller、VO、枚举、Service、Mapper/XML。
2. 当前自动化测试。
3. `.trellis/spec/backend/database-guidelines.md` 中已落地 code-spec。
4. 已归档任务的 PRD/design 仅作设计背景，不覆盖当前源码。

## 5. 兼容与风险

- 文档描述当前行为，不设计新协议。
- Swagger 修正只补全实际已有的操作符/令牌，不改变请求结构。
- 自定义分组返回原始值，前端需要用 Schema、系统用户或固定枚举完成显示翻译。
- 查询动态时间值是字符串，而普通请求/响应中的 `LocalDateTime` 使用毫秒时间戳；文档必须明确区分。
- 分组请求继承分页字段，但 `pageNo`、`pageSize` 和 `sortingFields` 不参与聚合结果。

## 6. 验证

- 逐项搜索 Controller 路由、权限、VO 字段和枚举值。
- 对照条件标准化与 Mapper SQL 核验过滤/分组语义。
- 运行 project server 全量测试，保证 Swagger 注解调整不影响编译及现有行为。
- 运行文档残留搜索和 `git diff --check`。

## 7. 回滚

本任务无数据库和运行行为变更。回滚新增 Markdown 及 Swagger 描述调整即可。
