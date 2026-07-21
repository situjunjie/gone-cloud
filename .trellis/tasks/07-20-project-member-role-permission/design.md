# 项目成员角色权限管理技术设计

## 1. Scope And Constraints

本设计在 `yudao-module-project` 内实现租户全局角色模板、项目角色快照、成员多角色和项目级鉴权。权限目录由 Java 枚举固定，角色及授权关系存储在项目模块数据库中。

约束：

- 保留现有系统权限和 `@ss.hasPermission` 作为第一层鉴权。
- 项目级鉴权不信任客户端单独提交的 `projectId`；对于工作项、字段等资源，先加载资源并以数据库中的归属项目校验。
- 当前仅支持私有项目，系统超级管理员不绕过项目成员边界。
- 当前无有效业务数据，不提供旧 `role` 字段的数据回填和兼容读取。
- 本任务不拆子任务：表结构、角色服务和现有接口鉴权强依赖同一套事务与契约，单任务分阶段实施更易保持原子性。

## 2. Domain Model

### 2.1 Fixed Permission Catalog

新增 `ProjectPermissionEnum`，固定以下项目权限：

| Code | Meaning | Participant Default | Participant Required |
|---|---|---:|---:|
| `PROJECT_QUERY` | 查看项目资料 | yes | yes |
| `PROJECT_UPDATE` | 修改项目资料 | no | no |
| `PROJECT_ARCHIVE` | 归档项目 | no | no |
| `MEMBER_QUERY` | 查看成员基本信息 | yes | no |
| `WORK_ITEM_QUERY` | 查询工作项 | yes | yes |
| `WORK_ITEM_CREATE` | 创建工作项 | yes | no |
| `WORK_ITEM_UPDATE` | 修改工作项 | yes | no |
| `WORK_ITEM_DELETE` | 删除工作项 | no | no |
| `WORK_ITEM_FIELD_QUERY` | 查询项目字段 | yes | no |
| `WORK_ITEM_FIELD_MANAGE` | 管理项目字段 | no | no |

枚举同时提供编码校验、参与人默认集合和不可移除集合。成员、角色、授权和负责人治理不进入该目录，由内置管理员规则直接控制。

### 2.2 Role Scope And Type

- `ProjectRoleScopeEnum`: `GLOBAL`、`PROJECT`。
- `ProjectRoleBuiltinTypeEnum`: `ADMIN`、`PARTICIPANT`、`NONE`。
- 内置编码固定为 `ADMIN`、`PARTICIPANT`；自定义角色编码在所属范围内唯一且创建后不可修改。
- `GLOBAL` 角色使用 `project_id = 0`；`PROJECT` 角色必须使用真实项目编号。

### 2.3 Persistence

#### `proj_project_role`

| Column | Purpose |
|---|---|
| `id` | 角色编号 |
| `scope` | `GLOBAL` 或 `PROJECT` |
| `project_id` | 全局为 0，项目角色为项目编号 |
| `code` / `name` | 角色稳定编码和展示名称 |
| `builtin_type` | `ADMIN`、`PARTICIPANT`、`NONE` |
| `sort` / `remark` | 展示与说明 |
| standard tenant/audit/delete columns | 租户、审计、逻辑删除 |

自然键为 `(tenant_id, scope, project_id, code)`。删除后再次创建同自然键时恢复逻辑删除记录，避免唯一键冲突。

#### `proj_project_role_permission`

保存 `role_id + permission_code`。管理员不保存关系行，其“全部权限”由服务规则直接判定。参与人和自定义角色使用差量更新，复用被逻辑删除的关系行。

#### `proj_project_member_role`

保存 `project_id + member_id + role_id`，支持成员多角色。写入前验证成员、角色和关系中的 `project_id` 同属当前租户及项目。关系更新采用差量新增、恢复和逻辑删除。

#### `proj_project_member`

保留项目成员身份和 `(tenant_id, project_id, user_id)` 唯一约束，删除单值 `role` 字段。成员响应通过批量查询返回角色列表，避免逐成员查询。

### 2.4 Tenant Template Bootstrap

`ProjectRoleService#ensureTenantBuiltinRoles` 在全局角色首次查询/维护和项目创建前执行：

1. 确保当前租户存在全局 `ADMIN`、`PARTICIPANT`；首次创建参与人时写入完整默认权限集合。
2. 对已存在的参与人只补齐 `PROJECT_QUERY`、`WORK_ITEM_QUERY` 两项必需权限，不恢复用户已主动移除的其他默认权限；管理员不写授权关系。
3. 依赖自然键唯一索引处理并发初始化；遇到重复键后重新读取，不产生重复内置角色。

不从 `system` 模块反向调用项目模块，避免新增跨模块循环依赖。

## 3. Core Invariants

- 管理员、参与人内置角色不可删除、改名或改编码。
- 管理员权限不可写入或修改，鉴权时直接允许全部项目权限。
- 参与人授权必须包含 `PROJECT_QUERY`、`WORK_ITEM_QUERY`。
- 自定义角色和参与人只能分配固定项目权限目录中的编码。
- 成员至少拥有一个角色；普通成员未指定角色时使用参与人。
- 删除被成员使用的项目自定义角色时拒绝操作。
- 当前负责人必须是成员并拥有管理员角色；移除成员或管理员角色前验证其不是当前负责人。
- 多角色只做允许权限并集，不存在拒绝或优先级。

## 4. Services

### 4.1 `ProjectRoleService`

负责全局和项目角色 CRUD、内置角色初始化、项目快照复制、角色权限差量更新以及角色删除前的成员引用校验。所有入口显式验证 scope、projectId、tenantId 和角色类型。

项目快照复制返回全局角色编号到项目角色编号的映射，供权限复制和初始成员角色分配使用。复制结果不保留源角色引用，后续完全独立。

### 4.2 `ProjectMemberService`

从现有 `ProjectServiceImpl` 中拆出成员职责，负责成员新增/恢复、角色集合更新、负责人保护、成员删除和成员角色批量查询。普通成员 `roleIds` 为空时解析为项目参与人；更新接口要求非空集合。

### 4.3 `ProjectPermissionService`

提供：

- `validateMember(projectId, userId)`
- `validatePermission(projectId, userId, permission)`
- `validateAdmin(projectId, userId)`
- `getEffectivePermissions(projectId, userId)`
- `getAccessibleProjectIds(userId, permission)` 或等价 Mapper 查询能力

判断顺序：校验项目存在和私有边界，读取成员及角色；存在内置管理员则允许任意固定项目权限，否则计算角色权限并集。服务不对系统超级管理员做特殊处理。

### 4.4 Permission Cache

项目权限属于高频读、低频写数据，首版接入现有 Spring Cache + Redis。缓存按规范化关系拆分，避免角色授权变化时扫描并删除整个项目的用户缓存：

| Cache | Key | Value | TTL | Eviction |
|---|---|---|---|---|
| `project:member_auth#30m` | `tenantId:projectId:userId` | 成员编号、角色编号集合、是否内置管理员；非成员使用显式 negative snapshot | 30 min | 成员新增/恢复/删除、成员角色更新、负责人初始化角色变化 |
| `project:role_permissions#30m` | `tenantId:roleId` | 该参与人/自定义角色的项目权限编码集合 | 30 min | 角色授权更新、角色删除/恢复 |

权限判断先读取成员授权快照；管理员直接允许，其他成员再读取各角色权限集合并取并集。通常成员只有 1 到 3 个角色，规范化缓存避免了“一个角色权限变化时批量驱逐所有成员有效权限”的放大操作。

实现约束：

- 新增项目模块 `RedisKeyConstants`，所有 key 显式包含 `tenantId`，不依赖线程上下文隐式隔离。
- 缓存查询放入独立 `ProjectPermissionCacheService` Bean，避免同类内部调用绕过 Spring Cache AOP。
- 项目模块现有 Redis starter 和 `TimeoutRedisCacheManager` 直接复用，不新增缓存依赖。
- 系统层 `@ss` 保持框架现有的 1 分钟本地 `LoadingCache`，项目模块不重复缓存系统角色/权限；本节 Redis 缓存只服务项目 RBAC。
- 现有 `RedisCacheManager` 已启用 transaction-aware，事务内 `@CacheEvict` 在提交后生效；回滚不驱逐正确缓存，也不会让未提交数据提前进入缓存。
- 精确驱逐是正确性的主机制，TTL 只用于异常恢复和孤儿键回收。
- 权限检查 fail closed：未知权限、缓存/数据库加载失败不得返回允许；基础设施异常按标准错误链路失败，不降级为放行。
- 项目列表和跨项目工作项分页仍使用数据库 `EXISTS` 过滤，不逐项目读取缓存，避免 N+1。

### 4.5 `ProjectSecurityFrameworkService`

新增 `@Component("projectSecurity")` 的安全适配 Bean，读取当前登录用户并委托 `ProjectPermissionService`：

- `hasPermission(Long projectId, String permissionCode)`
- `isAdmin(Long projectId)`
- `hasWorkItemPermission(Long workItemId, String permissionCode)`
- `hasFieldPermission(Long fieldId, String permissionCode)`
- `isAdminByMemberId(Long memberId)`
- `isAdminByRoleId(Long roleId)`

`has*` 对未登录、非成员、未知权限和不存在资源返回 `false`；基础设施异常不吞掉。按资源编号的方法必须从数据库解析真实项目，不能接受客户端附带的项目编号替代资源归属。

## 5. Transaction Flows

### 5.1 Create Project

在一个事务内：

1. 校验系统层已由 Controller 放行、负责人用户有效、可见性为 `PRIVATE`。
2. 确保当前租户全局内置角色存在。
3. 创建项目记录。
4. 复制当前租户全部全局角色及权限为项目快照。
5. 创建或恢复当前用户成员，分配项目管理员和参与人。
6. 负责人不同于当前用户时，创建或恢复负责人成员，仅分配管理员。

任一步失败时回滚项目、角色、权限和成员关系。

### 5.2 Transfer Owner

项目更新仍可携带 `ownerUserId`。普通资料修改要求 `PROJECT_UPDATE`；检测到负责人变化时额外要求内置管理员。新负责人不存在则创建成员，并确保其拥有管理员；旧负责人角色保持不变。

### 5.3 Update Member Roles

调用者必须通过系统成员管理权限和项目内置管理员校验。服务验证目标成员及全部角色同属项目、角色集合非空；目标是当前负责人时必须保留管理员。关系更新采用差量方式，避免唯一键和逻辑删除冲突。

## 6. API Contracts

### 6.1 Fixed Permission And Global Template

建议新增 `ProjectGlobalRoleController`：

- `GET /project/global-role/permission-list`
- `GET /project/global-role/list`
- `POST /project/global-role/create`
- `PUT /project/global-role/update`
- `DELETE /project/global-role/delete`
- `PUT /project/global-role/assign-permissions`

系统权限建议使用 `project:global-role:query` 和 `project:global-role:manage`。这些接口不要求项目成员身份。

### 6.2 Project Roles

建议新增 `ProjectRoleController`：

- `GET /project/role/list?projectId=`
- `POST /project/role/create`
- `PUT /project/role/update`
- `DELETE /project/role/delete?id=`
- `PUT /project/role/assign-permissions`

所有维护接口要求系统 `project:project:role` 权限和项目内置管理员；列表用于治理页面，同样限制为内置管理员。

### 6.3 Project Members

保留现有成员路径，调整契约：

- `POST /project/project/member/add`: `roleIds` 可空，空时默认参与人。
- `PUT /project/project/member/update-role`: 改为非空 `roleIds` 集合。
- 成员列表响应返回 `roles[]`，移除单值 `role`。

成员维护要求系统 `project:project:member` 和项目内置管理员；成员列表要求系统项目查询和项目 `MEMBER_QUERY`。

## 7. Two-Layer Authorization Map

| Operation | System Permission | Project Rule |
|---|---|---|
| 创建项目 | `project:project:create` | none |
| 查看项目/分页 | `project:project:query` | `PROJECT_QUERY`; page filters membership |
| 修改项目/公告 | `project:project:update` | `PROJECT_UPDATE` |
| 变更负责人 | `project:project:update` | built-in admin |
| 归档项目 | `project:project:archive` | `PROJECT_ARCHIVE` |
| 查看成员 | `project:project:query` | `MEMBER_QUERY` |
| 成员治理 | `project:project:member` | built-in admin |
| 角色治理 | `project:project:role` | built-in admin |
| 工作项查询/创建/修改/删除 | existing matching permission | matching `WORK_ITEM_*` |
| 项目字段查询 | `project:work-item:query` | `WORK_ITEM_FIELD_QUERY` |
| 项目字段管理 | `project:work-item:update` | `WORK_ITEM_FIELD_MANAGE` |

租户全局工作项字段和个人查询视图没有单一 `projectId`，保持现有系统级授权。查询视图最终执行工作项分页时，仍由工作项查询应用项目过滤。

### 7.1 `@PreAuthorize` Usage

项目权限通过命名 Bean 直接接入 Spring Security SpEL，与现有 `@ss` 使用同一套 `@PreAuthorize`：

```java
@PreAuthorize("@ss.hasPermission('project:work-item:create')"
        + " and @projectSecurity.hasPermission(#createReqVO.projectId, 'WORK_ITEM_CREATE')")
```

项目管理员专属操作：

```java
@PreAuthorize("@ss.hasPermission('project:project:member')"
        + " and @projectSecurity.isAdmin(#addReqVO.projectId)")
```

只有资源编号的操作使用资源解析方法：

```java
@PreAuthorize("@ss.hasPermission('project:work-item:delete')"
        + " and @projectSecurity.hasWorkItemPermission(#id, 'WORK_ITEM_DELETE')")
```

规则：

- `and` 保证系统权限先失败时不执行项目查询，并同时满足双层鉴权。
- 能从参数直接获得项目编号的简单入口，以注解作为权限边界；Service 继续验证项目/资源存在、租户归属和领域约束。
- ID-only、资源跨项目移动、负责人变更等复合入口，注解负责入口拦截，Service 仍按数据库真实归属校验源项目和目标项目，防止内部调用或状态变化绕过。
- 项目分页和未指定项目的跨项目工作项查询无法用单个 `projectId` 表达，Controller 只做系统 `@PreAuthorize`，Service/Mapper 强制追加成员和权限过滤。
- 不新增自定义 AOP 注解或修改框架 `PermissionEvaluator`；命名 Bean 的 SpEL 方式与现有 `@ss` 最接近，也便于逐接口审查。
- 根 Maven 配置已启用 `-parameters`，SpEL 可使用 `#createReqVO.projectId` 等真实参数名；测试仍需覆盖重构改名导致的表达式失效。

## 8. Resource Resolution And Queries

- ID-only get/update/delete 必须先加载资源，再按资源真实 `projectId` 校验，防止跨项目编号拼接。
- 工作项从项目 A 移动到项目 B 时，同时要求 A 的 `WORK_ITEM_UPDATE` 和 B 的 `WORK_ITEM_CREATE`。
- 未指定 `projectId` 的跨项目工作项分页和分组统计，通过 Mapper `EXISTS`/关联查询过滤到当前用户具有 `WORK_ITEM_QUERY` 的项目，不在内存中先查全量再过滤。
- 项目分页通过成员关系 `EXISTS` 过滤当前用户，不给超级管理员开旁路。
- 项目字段选项操作先解析所属字段；全局字段走系统权限，项目字段走真实项目的字段权限。
- 同一请求中注解和 Service 重复触发相同项目权限读取时命中 Redis；资源实体仍以数据库读取结果为准，不缓存可变业务对象来规避一次归属查询。

## 9. Error And Validation Contracts

在 `ErrorCodeConstants` 增加角色不存在/重复、内置角色不可修改删除、权限编码非法、参与人必需权限缺失、角色仍被成员使用、成员角色为空、跨项目角色、非项目管理员、项目权限不足、负责人角色保护及仅支持私有项目等业务错误。

Controller 使用组合后的系统级与项目级 `@PreAuthorize`；项目归属、角色约束、跨项目变化和其他领域错误仍由 Service 使用标准 `ServiceException` 返回。

## 10. Compatibility, Rollout And Rollback

- 更新 `sql/mysql/project.sql` 和 `project-menu.sql`；不提供旧成员角色数据迁移脚本。
- 删除 `ProjectMemberRoleEnum` 及成员单值角色 API，属于有意的后端契约变更；当前无有效数据和配套前端，可直接切换。
- 新权限校验接入所有现有项目、工作项和项目字段入口，避免只保护新管理 API。
- 回滚需同时还原代码与项目表结构；上线前数据库备份仍是必要操作，即使当前确认无有效业务数据。

## 11. Test Strategy

- Mapper/DB 测试：租户与项目隔离、自然键、逻辑删除恢复、成员项目过滤、跨项目工作项权限过滤。
- Service 测试：模板初始化和快照、内置角色约束、成员多角色并集、负责人保护、角色引用删除、双层边界中的项目层行为。
- Controller/安全测试：系统权限缺失时由 `@PreAuthorize` 拒绝，系统权限通过但项目权限不足时由业务层拒绝。
- 缓存测试：成员快照和角色权限命中、negative snapshot、tenant key 隔离，以及成员/授权修改在事务提交后精确驱逐、回滚不驱逐。
- Method Security 测试：加载真实 Spring Security 配置，验证 `@ss && @projectSecurity`、管理员表达式、ID-only 资源解析和系统权限短路。
- 回归测试：项目创建/更新/分页，工作项 CRUD/分页/分组，项目字段及选项操作。
