# 工作项字段全局与项目作用域：技术设计

## 1. 边界与不变量

- `GLOBAL` 是租户内全局，`WorkItemFieldDO` 继续继承 `TenantBaseDO`，不使用 `@TenantIgnore`。
- 字段身份仍是稳定 `fieldId`；字段值、选项和查询视图引用不迁移、不复制。
- `scope`、`projectId`、`code`、`fieldType` 创建后不可修改。
- 一个项目的有效字段集合为“当前租户全局字段 + 当前项目字段”，其中 `code` 唯一且不存在覆盖关系。
- 全局选择字段的选项由租户统一维护，项目不能覆盖或追加局部选项。

## 2. 数据模型

### 2.1 字段作用域

新增 `WorkItemFieldScopeEnum`：

- `GLOBAL`
- `PROJECT`

`proj_work_item_field` 新增 `scope varchar(32) NOT NULL DEFAULT 'PROJECT'`。为兼容 MySQL 唯一键并避免 `NULL` 可重复语义，持久化层使用 `project_id = 0` 表示全局字段；API 响应中的全局字段 `projectId` 返回 `null`。

现有记录保持原 `project_id`，并迁移为 `scope = 'PROJECT'`。新增约束：

- 唯一键改为 `(tenant_id, scope, project_id, code)`，保证同一全局范围或同一项目内编码唯一。
- 服务层额外执行跨作用域冲突校验：创建项目字段时检查同编码全局字段；创建全局字段时检查租户内任一项目同编码字段。
- 列表索引覆盖 `(tenant_id, scope, project_id, enabled, sort)`。

MySQL 无条件唯一索引，无法仅用一个索引表达“全局编码与任一项目编码互斥，但不同项目编码可重复”。跨作用域互斥由服务事务内校验，数据库唯一键仍兜住同范围并发冲突。

### 2.2 DDL 交付

- 更新 `sql/mysql/project.sql`，保证新环境直接得到最终结构。
- 新增独立增量 SQL，按“新增 scope -> 回填 PROJECT -> 替换索引”的顺序迁移已有环境。
- 项目模块当前只在 MySQL 脚本维护这些表，不创建其他数据库引擎的孤立表定义。

## 3. API 与权限

### 3.1 项目字段接口

保留现有 `/project/work-item-field/*` 写接口和 `project:work-item:update` 权限，所有写操作强制目标字段为 `PROJECT`。现有创建请求无需新增必填参数，服务固定写入 `PROJECT`，保持调用兼容。

### 3.2 全局字段接口

新增 `/project/work-item-field/global/*`：

- `POST /create`
- `PUT /update`
- `PUT /update-enabled`
- `POST /option/create`
- `PUT /option/update`
- `PUT /option/update-enabled`

全部要求 `project:work-item-field:global-manage` 权限或 `tenant_admin` 角色。全局创建请求不接收项目绑定，服务固定写入 `GLOBAL` 和持久化哨兵 `project_id = 0`。项目接口不能操作全局字段，全局接口也不能操作项目字段。

字段列表继续使用 `GET /project/work-item-field/list?projectId=...`，返回全局和当前项目的有效字段定义；响应新增 `scope`，全局字段 `projectId = null`。读取权限继续使用 `project:work-item:query`。

`sql/mysql/project-menu.sql` 新增“全局工作项字段管理”按钮权限，允许把能力委派给其他角色；`tenant_admin` 由控制器安全表达式直接允许，避免新菜单尚未同步进存量租户套餐时失去默认管理能力。

## 4. 服务与 Mapper

### 4.1 字段配置

`WorkItemFieldMapper` 增加：

- 按租户当前上下文查询全局字段编码。
- 查询任意项目是否存在指定编码。
- 查询“全局 + 指定项目”的有效字段列表。

`WorkItemFieldServiceImpl` 将项目/全局入口归一到带期望作用域的内部方法。更新字段、启停字段和维护选项前都校验实际字段作用域与入口一致。

列表排序使用 `sort ASC, id ASC`，不额外按作用域分组，保证管理员设置的排序值统一决定展示顺序；相同排序时用 id 保持稳定。

### 4.2 字段值

`WorkItemCustomFieldValueServiceImpl` 使用统一的 `isEffectiveForProject(field, projectId)` 判断：

- 全局字段对当前租户任意项目有效。
- 项目字段仅在 `field.projectId == projectId` 时有效。

必填字段加载改为“全局 + 当前项目”。新建工作项和自定义字段写操作继续通过现有 `validateRequired = true` 路径校验，因此自然实现存量延迟补齐；不提交 `customFieldValues` 的核心字段完整更新保持现状，不触发补齐。

### 4.3 查询

动态筛选仍要求请求包含单一 `projectId`。解析 `custom:<fieldId>` 后使用同一有效范围判断，全局字段允许用于任一项目，项目字段只允许用于所属项目。实际 SQL 继续以 `fieldId` 查询类型化值表，无需改变查询 SQL 结构。

## 5. 兼容与回滚

- 旧项目字段写接口和请求体保持可用，所有旧字段迁移后仍为 `PROJECT`。
- 字段值表、字段选项表和查询视图 JSON 无结构变更。
- 回滚代码前不得创建新的全局字段；若已经创建，回滚需要先停用全局入口并处理 `project_id = 0` 数据，避免旧代码将其当作普通项目字段。
- 增量 SQL 的索引替换必须与应用发布协调；代码发布前完成列和索引迁移。

## 6. 测试策略

- 字段服务：项目/全局创建、编码冲突、作用域越权、合并列表。
- 字段值服务：全局字段写入、其他项目字段拒绝、全局与项目必填合并、存量延迟补齐路径。
- 查询服务：全局字段筛选通过、其他项目字段筛选拒绝。
- 既有自定义字段与 Mapper 测试继续通过。
