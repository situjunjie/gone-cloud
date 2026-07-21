# 项目成员角色权限管理实施计划

## Preconditions

- [ ] 用户已审核并批准 `prd.md`、`design.md`。
- [ ] 使用 `task.py start` 将任务切换到 `in_progress`。
- [ ] 实现前加载 `trellis-before-dev` 并完整读取 backend 必需规范。

## Implementation Checklist

1. [ ] 定义领域枚举和错误契约
   - 新增固定项目权限、角色 scope、内置角色类型枚举。
   - 扩充 `ErrorCodeConstants`，覆盖内置角色、权限、成员角色、跨项目和私有边界。
   - 删除旧 `ProjectMemberRoleEnum` 的业务依赖。

2. [ ] 更新 MySQL 和菜单初始化契约
   - 在 `sql/mysql/project.sql` 新增角色、角色权限、成员角色表并移除成员单值 `role`。
   - 在 `sql/mysql/project-menu.sql` 新增全局角色模板和项目角色治理系统权限。
   - 搜索其他数据库脚本；仅在已有 project 表时同步修改对应引擎。

3. [ ] 实现角色持久层
   - 新增 `ProjectRoleDO`、`ProjectRolePermissionDO`、`ProjectMemberRoleDO` 及 Mapper。
   - 实现按租户/scope/project 查询、逻辑删除恢复、差量关系更新和成员引用计数。
   - 修改 `ProjectMemberDO`、Mapper 和批量角色查询。

4. [ ] 实现全局模板和项目角色服务
   - 实现租户内置角色幂等初始化及参与人默认权限。
   - 实现全局/项目自定义角色 CRUD 和权限分配约束。
   - 实现创建项目时角色权限快照复制。
   - 实现固定权限目录、全局角色和项目角色 API/VO/Convert。

5. [ ] 重构项目成员与项目创建流程
   - 成员新增和更新改为 `roleIds`，支持默认参与人和非空约束。
   - 成员列表批量返回角色集合。
   - 项目创建使用当前登录用户初始化创建人，并处理不同负责人。
   - 实现负责人转移、负责人管理员保护、角色引用删除保护。
   - 强制项目可见性为 `PRIVATE`。

6. [ ] 实现项目权限服务
   - 实现成员校验、内置管理员判定、角色权限并集和固定权限校验。
   - 新增成员授权快照、角色权限集合两个 Redis Cache，key 包含 tenant/project/user 或 tenant/role，TTL 为 30 分钟。
   - 在成员与角色授权写操作上配置事务提交后的精确缓存驱逐，并覆盖 negative snapshot。
   - 新增 `projectSecurity` SpEL Bean，支持 projectId、workItemId、fieldId、memberId、roleId 的安全解析方法。
   - 实现项目列表及跨项目工作项查询的数据库级授权过滤。
   - 不为系统超级管理员增加项目层旁路，缓存异常不得放行。

7. [ ] 接入现有项目与工作项入口
   - Controller 使用 `@ss && @projectSecurity` 的 `@PreAuthorize` 表达式接入直接项目和 ID-only 资源权限。
   - Project Service 接入查询、更新、归档、公告、成员、角色和负责人校验。
   - Work Item Service 接入 CRUD、字段更新、分页、分组和跨项目移动校验。
   - Work Item Field Service 按真实字段 scope/project 接入查询和管理权限。
   - 保持租户全局字段、个人查询视图仅走系统授权；验证其执行工作项查询时仍受项目过滤。

8. [ ] 添加测试与接口文档
   - 新增角色/授权 Mapper 和 Service 测试。
   - 新增项目创建快照、成员多角色、负责人保护、项目列表过滤测试。
   - 扩展工作项和字段测试，覆盖允许、拒绝和跨项目场景。
   - 添加 Redis Cache 命中/精确驱逐/事务回滚测试和 Method Security 表达式测试。
   - 更新 Swagger VO 注解和项目后端 API 文档。

9. [ ] 全量检查与清理
   - 搜索残留单值 `role`、`OWNER` 和旧枚举引用。
   - 检查所有项目资源入口都有系统层和项目层对应校验。
   - 运行 `trellis-check`，根据结果修复后再提交。

## Validation Commands

```bash
rg -n "ProjectMemberRoleEnum|OWNER|private String role" yudao-module-project sql/mysql/project*.sql
mvn -pl yudao-module-project/yudao-module-project-server -am -DskipTests compile
mvn -pl yudao-module-project/yudao-module-project-server -am test
```

若全模块测试受仓库既有环境问题阻塞，至少运行新增测试类的 `-Dtest=... -Dsurefire.failIfNoSpecifiedTests=false test`，并记录未运行范围和原因。

## Review Gates

- [ ] 数据模型能够表达租户全局、项目快照、成员多角色且无跨项目关联。
- [ ] 管理员直接全权限，参与人最低权限和默认权限均有测试。
- [ ] 项目创建事务原子地写入项目、角色快照、创建人和负责人。
- [ ] ID-only 资源操作以数据库真实归属项目鉴权。
- [ ] 高频权限读取命中规范化 Redis 缓存，所有授权写操作仅在事务提交后精确驱逐相关 key。
- [ ] Controller 项目权限统一使用 `@PreAuthorize` 的 `@ss && @projectSecurity` 表达式，复合规则由 Service 二次校验。
- [ ] 项目分页和跨项目工作项查询在数据库层过滤私有项目。
- [ ] 系统超级管理员未成为成员时没有项目层旁路。
- [ ] 所有错误使用模块错误码和标准 `ServiceException`。

## Rollback Points

- Schema gate：新表和成员表变更完成后先编译持久层，失败时在继续服务改造前回退该阶段。
- Core gate：角色/成员服务测试通过后再接入所有业务入口，便于隔离核心模型问题。
- Authorization gate：逐类接入 Project、WorkItem、Field，任一类回归失败时只回退该类接入，不破坏已验证核心。
- 最终回滚必须同时恢复应用版本和匹配的项目数据库结构；禁止仅回滚其中一侧。
