# DevOps 应用成员管理

## Goal

为 `yudao-module-devops` 的应用（application）增加成员管理后端接口，前端页面已按下列契约设计，后端按此实现即可。

统一前缀 `/devops/application/member`，权限沿用现有 `devops:application:query` / `devops:application:update`，**服务端必须额外校验「请求者 == 应用拥有者」**（`dev_application.owner_user_id`）。

## Background

- 应用实体 `dev_application` 已有 `owner_user_id` 字段作为拥有者唯一事实来源。
- 目前没有应用成员表，也没有成员管理接口；`dev_application_env` 等关联表已给出可参考的租户表 + 逻辑删除 + 恢复（restore）模式。
- 项目模块已有成员管理（`proj_project_member` + `ProjectMemberServiceImpl`），可作为命名/事务/校验风格的参考，但应用成员只有「拥有者 / 参与者」两类，不需要角色体系。
- 前端页面消费 `memberType: 'OWNER' | 'PARTICIPANT'`，并接受拥有者行 `id` 为空。

## Requirements

### ① 成员列表

```text
GET /devops/application/member/list?appId=1024
→ ApplicationMemberVO[]
{
  id?: number,          // 成员行 id，可为空（拥有者若未物化）
  appId: number,
  userId: number,
  userName?: string,    // 昵称
  userAvatar?: string,
  memberType: 'OWNER' | 'PARTICIPANT',
  createTime?: string
}
```

- 必须包含拥有者那一行（`memberType='OWNER'`），且与 `dev_application.owner_user_id` 一致。
- 排序：拥有者优先，其余按加入时间升序。

### ② 添加参与者

```text
POST /devops/application/member/add
{ appId, userIds: number[] }     // 支持批量
```

- 幂等：已存在的 `userId` 直接跳过，不报错。
- 新成员固定 `PARTICIPANT`。

### ③ 移除参与者

```text
DELETE /devops/application/member/remove?appId=1024&userId=2
```

- 用 `appId + userId` 定位，不是成员行 id（这样后端无论是否把拥有者物化进成员表，契约都不变）。
- 移除拥有者必须返回业务错误。

### ④ 转移拥有者

```text
PUT /devops/application/member/transfer-owner
{ appId, userId }
```

- 必须同一事务完成：目标 → `OWNER`；原拥有者 → `PARTICIPANT`；`dev_application.owner_user_id` 同步。
- 目标必须是已有成员。

## Constraints

- 权限沿用 `devops:application:query`（列表）与 `devops:application:update`（增删改），不新增菜单/权限点。
- 服务端在权限之外必须再校验请求者就是该应用拥有者，非拥有者一律业务报错。
- 成员表为租户业务表，必须继承 `TenantBaseDO`，逻辑删除。
- 表名使用 DevOps 短前缀 `dev_`；错误码落在 `yudao-module-devops-api` 的 `ErrorCodeConstants`，沿用 `1_011_000_0xx` 段。
- 昵称/头像通过 Easy-Trans（`AdminUserApi.PREFIX`）在响应层转换，不在服务层逐行 RPC。

## Acceptance Criteria

- [x] 四个接口按上述 HTTP 契约可用，返回结构与字段名一致。
- [x] 列表始终包含拥有者行；拥有者未物化时 `id` 为空；排序为「拥有者优先，其余按 `createTime` 升序」。
- [x] 添加成员幂等、固定 `PARTICIPANT`、支持批量、跳过拥有者自身、恢复逻辑删除的历史行。
- [x] 移除按 `appId + userId` 定位；移除拥有者返回业务错误；成员不存在返回业务错误。
- [x] 转移拥有者在同一事务内完成：目标成为拥有者、原拥有者成为参与者、`owner_user_id` 同步；目标非成员返回业务错误。
- [x] 非拥有者调用任一接口返回业务错误（即使拥有 `devops:application:update`）。
- [x] `sql/mysql/devops.sql` 与增量迁移脚本包含 `dev_application_member`。
- [x] 新增聚焦单元测试，覆盖列表组装、幂等添加、移除保护、转移事务语义。
- [x] `mvn -pl yudao-module-devops/yudao-module-devops-server -am test` 通过，`git diff --check` 干净。

## Out Of Scope

- 不实现前端页面。
- 不引入角色/权限细分（只有拥有者与参与者）。
- 不改动 `dev_application` 的建应用/改应用链路（拥有者变更走 `transfer-owner`）。
- 不做成员数量上限、成员搜索、分页。
- 不补录历史数据（列表对拥有者行做实时合成，无需回填）。
