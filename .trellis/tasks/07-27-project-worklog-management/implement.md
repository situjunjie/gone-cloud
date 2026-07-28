# 项目工时管理实施计划

## Preconditions

- `prd.md` 和 `design.md` 已完成收敛，无阻塞问题。
- 用户审阅最终规划摘要并明确批准后，才运行 `task.py start`。
- Phase 2 开始前由实现代理加载清单中的 backend、数据库、权限、工作项变更日志、跨层和复用规范。

## Implementation Steps

1. SQL、枚举与错误契约
   - 新增 `proj_worklog` 全量和日期化增量 DDL，删除 `estimated_workload`。
   - 新增 `project_worklog_category` 字典和五个字典项。
   - 增加系统菜单权限、项目 `WORKLOG_QUERY/CREATE/MANAGE` 权限及既有参与人默认权限迁移。
   - 增加工时类型、汇总分组枚举和模块错误码。

2. 持久化与聚合
   - 新增 `WorklogDO`、Mapper、XML 和总计/分组结果对象。
   - 实现统一筛选、分页、单工作项/批量工作项合计、项目总计、单维度分组和迭代实际工时聚合。
   - 检查所有动态分组列仅来自服务端枚举，并覆盖逻辑删除、租户和项目条件。

3. Service 生命周期
   - 实现创建、查询、更新、逻辑删除、工作项级联删除和存在性查询。
   - 接入工作项真实项目解析、项目可写校验、成员资格、系统字典、条件日期和负责人/管理者规则。
   - 为工时操作增加必要的操作日志，不把新来源并入统一工作项动态。

4. HTTP 与方法安全
   - 新增工时 Controller、请求/响应 VO、Bean Validation 和 OpenAPI 描述。
   - 扩展 `ProjectSecurityFrameworkService`，按持久化工时或工作项解析项目权限。
   - 接入系统菜单权限与 `WORKLOG_QUERY/CREATE/MANAGE` 项目权限，并增加 Easy-Trans 负责人昵称转换。

5. 工作项和迭代集成
   - 清理 `estimatedWorkload` 的数据库、DO、VO、查询、快捷更新、Service、变更日志与接口文档引用。
   - 为工作项详情和分页批量装配 `estimatedHours/actualHours`。
   - 全量和快捷跨项目更新均阻止带工时工作项移动。
   - 工作项删除事务内逻辑级联删除工时。
   - 迭代列表用实际工时聚合替换 `MOCK_USED_HOURS`。

6. Focused tests
   - VO：小时精度/范围、实际工时日期必填、日期范围和枚举校验。
   - Mapper：分页筛选、总计、四种单维度分组、空迭代、逻辑删除、批量工作项和迭代聚合。
   - Service：普通本人登记、管理者代填、成员校验、不可变字段、所有权修改/删除、字典失败和级联删除回滚。
   - Security：系统权限短路、项目权限拒绝、持久化资源归属、查询和管理成功路径。
   - Work item / sprint：旧字段无残留、移动阻断、删除级联、分页批量合计和 `usedHours` 零值/真实值。

7. Documentation and residual review
   - 更新 `docs/work-item-frontend-api.md`，补充工时接口契约和移除旧字段。
   - 全仓搜索 `estimatedWorkload|estimated_workload|MOCK_USED_HOURS`，确认无过期业务引用。
   - 检查全量 SQL、增量 SQL、权限目录、字典和 Java 常量一致。

## Validation Commands

```bash
mvn -pl yudao-module-project/yudao-module-project-server -am test
mvn -pl yudao-server -am -DskipTests package
git diff --check
```

必要时对新增 Mapper 测试单独运行：

```bash
mvn -pl yudao-module-project/yudao-module-project-server -Dtest='*Worklog*Test,*ProjectWorkItemServiceImplTest,*ProjectSprintServiceImplTest' test
```

## Risk And Rollback Points

- `estimated_workload` 删除面跨 SQL、DO、VO、动态查询、快捷更新和变更日志，必须以残留搜索收口。
- 项目角色是权限快照；只改枚举会导致既有参与人缺少工时权限，增量 SQL 必须幂等补齐。
- 汇总与明细若各自拼条件会产生口径漂移，必须共享受控筛选构造。
- 工作项分页和迭代列表只能批量聚合，禁止逐工作项/逐迭代查询。
- 删除旧列是不可逆 DDL；旧应用回滚前需要恢复列。当前项目未上线且无需迁移历史数据。
- 工作项删除与工时级联必须处于同一事务，避免半删除。

## Review Gates Before Activation

- [x] Goal、范围、验收标准和所有产品决策已写入 PRD。
- [x] 数据模型、API、权限、聚合、工作项生命周期和回滚方案已明确。
- [x] PRD 无阻塞问题，复杂任务的 `design.md`、`implement.md` 已创建。
- [x] `implement.jsonl`、`check.jsonl` 已配置真实规范条目。
- [x] 用户明确批准最新规划摘要。
- [x] 批准后运行 `task.py start`，进入实现阶段。

## Execution Status (2026-07-28)

- 已完成 SQL、工时实体、明细/汇总 API、双层权限、工作项聚合与生命周期、迭代实际工时、文档和测试实现。
- 最终审查移除了无前端实现的可见工时菜单，将四项系统按钮权限挂到工作项菜单，并为旧菜单节点提供幂等转换逻辑。
- 最终审查补充 `WorklogControllerMethodSecurityTest`，覆盖系统权限短路、项目权限拒绝、创建/管理权限、持久化工时归属和查询路径。
- 汇总请求不再继承分页字段；项目 RBAC 规范已同步工时权限、资源归属、迁移和迭代实际工时契约。
- 聚焦测试：45 个通过；项目模块 reactor：185 个通过，0 失败、0 错误；`yudao-server` reactor 跳过测试打包通过；`git diff --check` 通过。
- 未在真实 MySQL 环境执行增量迁移。
