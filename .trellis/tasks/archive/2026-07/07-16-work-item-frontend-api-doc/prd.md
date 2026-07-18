# 工作项前端接口对接文档

## Goal

核验项目工作项的复杂分页查询、单字段分组统计、快捷更新和运行时 Schema 能力是否已经完整落地，并输出一份前端可直接据此开发和联调的详细接口文档。

## Background

- 工作项复杂条件分页、复杂条件单字段分组、运行时自定义字段 Schema 和自定义字段快捷更新已分别实施或进入收尾阶段。
- 前端即将开始对接，需要以当前 Controller、请求/响应 VO、枚举、校验逻辑和测试为准形成统一契约，不能仅根据历史任务描述推断。
- 当前工作区存在既有 `.trellis/.template-hashes.json` 修改，本任务不得纳入或覆盖。

## Requirements

- 核对四类能力的实际实现、URL、HTTP 方法、权限、请求与响应模型，并指出任何尚未完成或不适合前端依赖的部分。
- 文档覆盖复杂分页查询、条件组逻辑、操作符和值格式、分页排序、单字段分组统计、运行时 Schema、快捷更新及自定义字段局部 Patch。
- 为每个接口提供完整 JSON 请求/响应示例，并解释固定字段与 `custom:<fieldId>` 的编码关系。
- 记录字段类型、枚举值、空值语义、时间格式、单选/多选值格式、`me` 等动态值、全局/项目级字段合并规则。
- 记录前端推荐调用顺序、Schema 缓存/刷新边界、错误处理、权限要求和兼容限制。
- 文档内容必须逐项由源码、测试或已落地 code-spec 验证；发现实现缺口时先明确列出，不把未实现行为写成可用契约。
- 文档应放在项目内适合长期维护和前后端共享的位置，并遵循现有文档组织方式。

## Confirmed Facts

- `POST /admin-api/project/work-item/page` 已实现快捷条件、`conditions` 平铺 AND、`conditionGroups` 组内 AND/组间 OR、自定义字段过滤、最多 20 个动态条件及多字段排序。
- `POST /admin-api/project/work-item/group-count` 继承分页请求结构并复用同一条件标准化和 Wrapper 构造，支持固定字段、自定义标量/选项及空值分组。
- `GET /admin-api/project/work-item/schema` 已按项目合并租户全局与项目字段，并过滤禁用/分类不适用字段及禁用选项。
- `PUT /admin-api/project/work-item/update-field` 已支持内置字段与 `custom:<fieldId>` 混合批量更新；自定义字段只替换请求目标，未指定历史值保持不变。
- 四项能力均使用现有权限和 `CommonResult` 返回协议；当前 project server 测试覆盖了条件标准化、分页 SQL、聚合 SQL、Schema 过滤及快捷更新局部写入。
- 项目当前没有统一业务模块 API Markdown 目录；建议在 project server 内新增 `docs/work-item-frontend-api.md`，使文档与接口代码同模块维护。
- 用户已确认采用端到端范围，文档同时覆盖 Schema、创建、详情、完整更新、复杂分页、单字段分组统计和快捷更新。
- 当前 `ProjectWorkItemPageReqVO.Condition.operator` Swagger 描述遗漏实际已支持的 `is_empty`、`is_not_empty`，用户令牌描述也遗漏 `self`；文档任务应同步修正这些注解，避免 Swagger 与 Markdown 冲突。

## Acceptance Criteria

- [x] 四类能力均完成代码级核验，并给出“可对接/存在缺口”的明确结论。
- [x] 每个接口包含方法、路径、权限、参数、请求体、响应体、字段解释和示例。
- [x] 复杂过滤、排序、分组、自定义字段 Schema 与快捷更新的关键边界均有明确说明。
- [x] 文档中的字段名、枚举、错误行为和示例与当前代码一致。
- [x] 文档经残留搜索和相关测试/编译结果交叉验证。
- [x] 最终文档路径清晰，前端无需阅读 Java 源码即可开始实现。
- [x] 端到端流程覆盖 Schema、创建/完整编辑、详情回显、查询/聚合和快捷更新。

## Out Of Scope

- 本任务默认不新增或改变接口业务行为；若核验发现缺口，先记录并与用户确认是否扩展为代码修复。
- 不编写前端页面或 API 客户端代码。
- 不修改数据库结构。
