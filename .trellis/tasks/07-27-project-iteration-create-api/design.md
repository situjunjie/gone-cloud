# 项目迭代创建接口技术设计

## Boundary

- 新增 `sprint` 管理端 HTTP、VO、Service 和 Convert 层，复用既有 `ProjectSprintDO` 与 `ProjectSprintMapper`。
- 复用 `ProjectService.validateProjectUsable` 校验项目存在且未归档。
- 通过 `ProjectMemberService` 暴露的成员校验能力验证负责人属于目标项目，避免 Sprint Service 直接跨域访问成员 Mapper。
- 本次只实现创建，不实现迭代读写全套 CRUD。

## HTTP Contract

`POST /project/sprint/create`

权限：

```text
system:  project:sprint:create
project: SPRINT_CREATE(projectId)
```

请求：

```json
{
  "projectId": 1024,
  "title": "第 1 迭代",
  "ownerUserId": 1001,
  "startDate": "2026-07-27",
  "endDate": "2026-08-09",
  "capacityHours": 7.5,
  "description": "完成核心流程联调"
}
```

- `projectId`、`title`、`ownerUserId` 必填。
- `title` 非空且最长 50；`description` 最长 500。
- `startDate`、`endDate` 可分别为空；均存在时 `endDate >= startDate`。
- `capacityHours` 可空，范围 `0.0 ~ 999999999.9`，最多 1 位小数。
- 返回 `CommonResult<Long>`，data 为新迭代编号。

## Persistence Mapping

| API field | DO / column | Mapping |
|---|---|---|
| `projectId` | `projectId` / `project_id` | direct |
| `title` | `title` / `title` | direct |
| `ownerUserId` | `ownerUserId` / `owner_user_id` | direct |
| `startDate` | `plannedStartTime` / `planned_start_time` | `LocalDate.atStartOfDay()` |
| `endDate` | `plannedEndTime` / `planned_end_time` | `LocalDate.atStartOfDay()` |
| `capacityHours` | `capacityHours` / `capacity_hours` | `decimal(10,1)` |
| `description` | `description` / `description` | `varchar(500)` |

- 初始 `status` 固定为 `NOT_STARTED`。
- `actualStartTime`、`actualEndTime` 创建时保持空值。
- 同步 `sql/mysql/project.sql`，并新增幂等的 2026-07-27 增量脚本，补列及系统按钮权限。

## Validation And Errors

- Bean Validation 处理必填、长度、容量上下限和 1 位小数约束。
- Service 处理跨字段日期关系、项目可用性、负责人项目成员关系。
- 日期倒置使用新的 Sprint 业务错误码；负责人不是项目成员使用新的 Sprint 业务错误码。
- Controller 使用双层 `@PreAuthorize`，先校验系统权限，再以请求 `projectId` 校验 `SPRINT_CREATE`。

## RBAC Compatibility

- 将 `SPRINT_CREATE` 加入固定项目权限目录，但 `participantDefault=false`、`participantRequired=false`。
- 既有 ADMIN 不存权限行，按契约自动获得新增固定权限。
- 既有参与人和自定义角色不会自动获得该权限；自定义角色可通过现有角色权限管理接口显式授权。
- `project-menu.sql` 与增量脚本增加 `project:sprint:create` 系统权限按钮。

## Rollback

- Java 代码可直接回滚；新增 nullable 数据列对旧版本代码无影响。
- 回滚应用版本时可保留 `capacity_hours`、`description` 和权限菜单记录，避免破坏已写入数据。

