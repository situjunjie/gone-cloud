# 项目成员、角色与权限前端接口文档

## 1. 业务背景

- 当前只支持私有项目，用户必须加入项目后才能查看或操作项目数据。
- 每个项目固定包含“管理员”和“参与人”两个内置角色，内置角色不能删除或改名。
- 管理员拥有全部项目权限，管理员权限不能修改。
- 参与人默认拥有项目查询、成员查询、工作项查询/创建/修改和项目字段查询权限；其中项目查询、工作项查询不能移除。
- 可以创建自定义角色，并为一个成员分配多个角色。成员有效权限为所有角色权限的并集。
- 全局角色模板适用于后续新项目。创建项目时复制一份角色和权限快照，之后项目角色与全局模板互不影响。
- 同一用户在不同项目中可以拥有不同角色。
- 项目成员和项目角色只能由项目内置管理员治理，同时还需要对应系统权限。

## 2. 通用协议

接口前缀：`/admin-api`

认证头、租户头和系统权限判断沿用管理后台现有请求封装。

所有接口返回 `CommonResult<T>`：

```json
{
  "code": 0,
  "msg": "",
  "data": {}
}
```

- `code = 0` 表示成功。
- `code != 0` 时使用 `msg` 展示业务提示，不继续读取成功数据。
- Long 编号在 JavaScript 安全整数范围内返回 `number`，超出范围时返回 `string`；前端类型使用
  `string | number`，比较、缓存和路由传参时统一转为字符串，禁止使用 `Number` 强制转换雪花编号。
- 下文为便于阅读使用较小的 `number` 示例，线上雪花编号通常会以 `string` 返回。
- `LocalDateTime` 使用 Unix 毫秒时间戳。

## 3. 权限说明

### 3.1 系统权限

| 系统权限 | 用途 |
|---|---|
| `project:project:create` | 创建项目 |
| `project:project:query` | 查询项目、成员和当前用户项目权限 |
| `project:project:update` | 修改项目、转移负责人 |
| `project:project:archive` | 归档项目 |
| `project:project:member` | 增加、移除成员及调整成员角色 |
| `project:project:role` | 查询和维护项目角色 |
| `project:global-role:query` | 查询固定权限目录和全局角色模板 |
| `project:global-role:manage` | 维护全局角色模板及授权 |

项目成员治理和项目角色治理还要求当前用户的 `admin = true`。系统权限不能代替项目管理员身份。

### 3.2 固定项目权限

项目权限由后端固定定义，前端不能新增权限编码。

| 编码 | 名称 | 参与人默认 | 参与人必需 |
|---|---|---:|---:|
| `PROJECT_QUERY` | 查看项目资料 | 是 | 是 |
| `PROJECT_UPDATE` | 修改项目资料 | 否 | 否 |
| `PROJECT_ARCHIVE` | 归档项目 | 否 | 否 |
| `MEMBER_QUERY` | 查看项目成员 | 是 | 否 |
| `WORK_ITEM_QUERY` | 查询工作项 | 是 | 是 |
| `WORK_ITEM_CREATE` | 创建工作项 | 是 | 否 |
| `WORK_ITEM_UPDATE` | 修改工作项 | 是 | 否 |
| `WORK_ITEM_DELETE` | 删除工作项 | 否 | 否 |
| `WORK_ITEM_FIELD_QUERY` | 查询项目字段 | 是 | 否 |
| `WORK_ITEM_FIELD_MANAGE` | 管理项目字段 | 否 | 否 |

成员和角色治理不属于可分配项目权限，前端使用 `admin` 字段判断项目管理员身份。

## 4. 接口总览

### 4.1 当前用户与项目成员

| 功能 | 方法 | 路径 | 权限 |
|---|---|---|---|
| 当前用户项目权限 | GET | `/project/project/my-permissions` | 系统项目查询 + `PROJECT_QUERY` |
| 项目成员列表 | GET | `/project/project/member-list` | 系统项目查询 + `MEMBER_QUERY` |
| 新增项目成员 | POST | `/project/project/member/add` | 系统成员管理 + 项目管理员 |
| 修改成员角色 | PUT | `/project/project/member/update-role` | 系统成员管理 + 项目管理员 |
| 移除项目成员 | DELETE | `/project/project/member/remove` | 系统成员管理 + 项目管理员 |

### 4.2 项目角色

| 功能 | 方法 | 路径 | 权限 |
|---|---|---|---|
| 项目角色列表 | GET | `/project/role/list` | 系统项目角色管理 + 项目管理员 |
| 创建项目角色 | POST | `/project/role/create` | 系统项目角色管理 + 项目管理员 |
| 更新项目角色 | PUT | `/project/role/update` | 系统项目角色管理 + 项目管理员 |
| 删除项目角色 | DELETE | `/project/role/delete` | 系统项目角色管理 + 项目管理员 |
| 分配项目角色权限 | PUT | `/project/role/assign-permissions` | 系统项目角色管理 + 项目管理员 |

### 4.3 全局角色模板

| 功能 | 方法 | 路径 | 权限 |
|---|---|---|---|
| 固定权限目录 | GET | `/project/global-role/permission-list` | 系统全局角色查询 |
| 全局角色列表 | GET | `/project/global-role/list` | 系统全局角色查询 |
| 创建全局角色 | POST | `/project/global-role/create` | 系统全局角色管理 |
| 更新全局角色 | PUT | `/project/global-role/update` | 系统全局角色管理 |
| 删除全局角色 | DELETE | `/project/global-role/delete` | 系统全局角色管理 |
| 分配全局角色权限 | PUT | `/project/global-role/assign-permissions` | 系统全局角色管理 |

## 5. 公共数据结构

### 5.1 ProjectRole

```json
{
  "id": 1201,
  "scope": "PROJECT",
  "projectId": 1024,
  "code": "DEVELOPER",
  "name": "开发人员",
  "builtinType": "NONE",
  "sort": 20,
  "remark": "负责工作项开发",
  "permissionCodes": [
    "PROJECT_QUERY",
    "WORK_ITEM_QUERY",
    "WORK_ITEM_UPDATE"
  ],
  "createTime": 1784620800000
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | Long | 角色编号，成员授权时提交该值 |
| `scope` | String | `GLOBAL` 全局模板；`PROJECT` 项目角色 |
| `projectId` | Long | 全局模板为 `0`；项目角色为实际项目编号 |
| `code` | String | 角色编码，创建后不能修改 |
| `name` | String | 角色名称 |
| `builtinType` | String | `ADMIN`、`PARTICIPANT`、`NONE` |
| `sort` | Integer | 排序值 |
| `remark` | String | 备注，可为空 |
| `permissionCodes` | String[] | 角色拥有的固定项目权限 |
| `createTime` | Long | 创建时间 |

内置管理员的 `permissionCodes` 始终返回全部固定项目权限。

### 5.2 ProjectMember

```json
{
  "id": 5001,
  "projectId": 1024,
  "userId": 100,
  "userName": "张三",
  "userAvatar": "https://www.iocoder.cn/1.png",
  "roles": [
    {
      "id": 1201,
      "scope": "PROJECT",
      "projectId": 1024,
      "code": "DEVELOPER",
      "name": "开发人员",
      "builtinType": "NONE",
      "sort": 20,
      "remark": null,
      "permissionCodes": ["PROJECT_QUERY", "WORK_ITEM_QUERY"],
      "createTime": 1784620800000
    }
  ],
  "createTime": 1784620800000
}
```

`roles` 始终为数组，不再使用旧的单值 `role` 字段。

## 6. 当前用户项目权限

进入项目后调用，用于控制项目内按钮和治理入口。

### 6.1 请求

```http
GET /admin-api/project/project/my-permissions?projectId=1024
```

### 6.2 响应

```json
{
  "code": 0,
  "msg": "",
  "data": {
    "admin": true,
    "permissionCodes": [
      "PROJECT_QUERY",
      "PROJECT_UPDATE",
      "PROJECT_ARCHIVE",
      "MEMBER_QUERY",
      "WORK_ITEM_QUERY",
      "WORK_ITEM_CREATE",
      "WORK_ITEM_UPDATE",
      "WORK_ITEM_DELETE",
      "WORK_ITEM_FIELD_QUERY",
      "WORK_ITEM_FIELD_MANAGE"
    ]
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `admin` | Boolean | 是否拥有项目内置管理员角色 |
| `permissionCodes` | String[] | 当前用户所有项目角色的权限并集 |

前端显示成员/角色治理入口时，同时判断：

- `admin === true`
- 当前登录用户具有对应系统权限 `project:project:member` 或 `project:project:role`

## 7. 固定权限目录

### 7.1 请求

```http
GET /admin-api/project/global-role/permission-list
```

### 7.2 响应

```json
{
  "code": 0,
  "msg": "",
  "data": [
    {"code": "PROJECT_QUERY", "name": "查看项目资料"},
    {"code": "PROJECT_UPDATE", "name": "修改项目资料"},
    {"code": "PROJECT_ARCHIVE", "name": "归档项目"},
    {"code": "MEMBER_QUERY", "name": "查看项目成员"},
    {"code": "WORK_ITEM_QUERY", "name": "查询工作项"},
    {"code": "WORK_ITEM_CREATE", "name": "创建工作项"},
    {"code": "WORK_ITEM_UPDATE", "name": "修改工作项"},
    {"code": "WORK_ITEM_DELETE", "name": "删除工作项"},
    {"code": "WORK_ITEM_FIELD_QUERY", "name": "查询项目字段"},
    {"code": "WORK_ITEM_FIELD_MANAGE", "name": "管理项目字段"}
  ]
}
```

权限目录固定，项目角色和全局角色使用同一份编码。

## 8. 全局角色模板接口

### 8.1 查询列表

```http
GET /admin-api/project/global-role/list
```

响应 `data` 为 `ProjectRole[]`，包含内置管理员、参与人和全局自定义角色。

### 8.2 创建自定义角色

```http
POST /admin-api/project/global-role/create
Content-Type: application/json
```

```json
{
  "code": "DEVELOPER",
  "name": "开发人员",
  "sort": 20,
  "remark": "开发角色模板"
}
```

| 字段 | 类型 | 必填 | 约束 |
|---|---|---:|---|
| `code` | String | 是 | 大写字母开头，只允许大写字母、数字、下划线，最长 64 |
| `name` | String | 是 | 最长 128 |
| `sort` | Integer | 否 | 默认 `0` |
| `remark` | String | 否 | 最长 512 |

成功响应 `data` 为新角色编号。

### 8.3 更新自定义角色

```http
PUT /admin-api/project/global-role/update
Content-Type: application/json
```

```json
{
  "id": 1201,
  "name": "研发人员",
  "sort": 20,
  "remark": "更新后的名称"
}
```

角色编码不能修改；内置角色不能更新。成功响应 `data = true`。

### 8.4 删除自定义角色

```http
DELETE /admin-api/project/global-role/delete?id=1201
```

内置角色不能删除。成功响应 `data = true`。

### 8.5 分配权限

```http
PUT /admin-api/project/global-role/assign-permissions
Content-Type: application/json
```

```json
{
  "roleId": 1201,
  "permissionCodes": [
    "PROJECT_QUERY",
    "WORK_ITEM_QUERY",
    "WORK_ITEM_CREATE",
    "WORK_ITEM_UPDATE"
  ]
}
```

- 自定义角色允许提交空数组，表示该角色没有项目权限。
- 参与人必须包含 `PROJECT_QUERY`、`WORK_ITEM_QUERY`。
- 管理员不允许调用授权接口。
- 修改全局模板只影响之后创建的项目。

## 9. 项目角色接口

### 9.1 查询项目角色

```http
GET /admin-api/project/role/list?projectId=1024
```

响应 `data` 为当前项目的 `ProjectRole[]`。

### 9.2 创建项目角色

```http
POST /admin-api/project/role/create
Content-Type: application/json
```

```json
{
  "projectId": 1024,
  "code": "TESTER",
  "name": "测试人员",
  "sort": 30,
  "remark": "负责测试"
}
```

字段约束与全局角色创建一致，`projectId` 必填。成功响应 `data` 为新角色编号。

### 9.3 更新项目角色

```http
PUT /admin-api/project/role/update
Content-Type: application/json
```

```json
{
  "id": 1301,
  "name": "质量保障",
  "sort": 30,
  "remark": "测试和质量保障"
}
```

角色编码不能修改；内置角色不能更新。成功响应 `data = true`。

### 9.4 删除项目角色

```http
DELETE /admin-api/project/role/delete?id=1301
```

- 内置角色不能删除。
- 角色仍分配给任一成员时不能删除，前端应先调整相关成员角色。

成功响应 `data = true`。

### 9.5 分配项目角色权限

```http
PUT /admin-api/project/role/assign-permissions
Content-Type: application/json
```

```json
{
  "roleId": 1301,
  "permissionCodes": [
    "PROJECT_QUERY",
    "MEMBER_QUERY",
    "WORK_ITEM_QUERY"
  ]
}
```

约束与全局角色授权一致。修改只影响当前项目。

## 10. 项目成员接口

### 10.1 查询成员列表

```http
GET /admin-api/project/project/member-list?projectId=1024
```

响应 `data` 为 `ProjectMember[]`。

### 10.2 新增成员

指定角色：

```http
POST /admin-api/project/project/member/add
Content-Type: application/json
```

```json
{
  "projectId": 1024,
  "userId": 100,
  "roleIds": [1301, 1302]
}
```

不传或传空 `roleIds` 时，成员默认分配参与人角色：

```json
{
  "projectId": 1024,
  "userId": 100
}
```

成功响应 `data` 为项目成员编号，不是用户编号。

### 10.3 修改成员角色

```http
PUT /admin-api/project/project/member/update-role
Content-Type: application/json
```

```json
{
  "id": 5001,
  "roleIds": [1301, 1302]
}
```

- `id` 是项目成员编号。
- `roleIds` 必须至少包含一个当前项目角色。
- 当前负责人必须保留管理员角色。

成功响应 `data = true`。

### 10.4 移除成员

```http
DELETE /admin-api/project/project/member/remove?id=5001
```

当前负责人不能移除，需要先转移负责人。成功响应 `data = true`。

## 11. 项目创建与负责人规则

### 11.1 创建项目

```http
POST /admin-api/project/project/create
Content-Type: application/json
```

```json
{
  "coverUrl": null,
  "name": "研发管理平台",
  "visibility": "PRIVATE",
  "code": "RD-PLATFORM",
  "description": "研发项目",
  "announcement": null,
  "ownerUserId": 100,
  "sort": 0
}
```

- `visibility` 只能传 `PRIVATE`。
- 创建人由当前登录用户确定，不需要提交创建人字段。
- 创建人自动获得管理员和参与人角色。
- 负责人不同于创建人时，负责人自动加入项目并获得管理员角色。
- 新项目继承当前全局角色模板快照。

成功响应 `data` 为项目编号。

### 11.2 更新项目与转移负责人

```http
PUT /admin-api/project/project/update
Content-Type: application/json
```

请求字段与创建接口一致，额外提交项目 `id`：

```json
{
  "id": 1024,
  "coverUrl": null,
  "name": "研发管理平台",
  "visibility": "PRIVATE",
  "code": "RD-PLATFORM",
  "description": "研发项目",
  "announcement": null,
  "ownerUserId": 101,
  "sort": 0
}
```

修改 `ownerUserId` 即转移负责人：

- 操作人必须是项目内置管理员，并具有系统项目修改权限。
- 新负责人自动加入项目并获得管理员角色。
- 原负责人保留已有角色，不会自动移除管理员角色。

成功响应 `data = true`。

### 11.3 项目分页

```http
GET /admin-api/project/project/page?pageNo=1&pageSize=10
```

项目分页只返回当前用户已经加入的私有项目，不返回其他项目。

## 12. 工作项和项目字段权限

现有工作项接口请求/响应结构不变，详细载荷见 [项目工作项前端接口文档](./work-item-frontend-api.md)。

后端会额外校验下列项目权限：

| 操作 | 项目权限 |
|---|---|
| 查询工作项、分页、分组统计 | `WORK_ITEM_QUERY` |
| 创建工作项 | `WORK_ITEM_CREATE` |
| 修改工作项 | `WORK_ITEM_UPDATE` |
| 删除工作项 | `WORK_ITEM_DELETE` |
| 查询项目字段 Schema/列表 | `WORK_ITEM_FIELD_QUERY` |
| 创建、修改项目字段及选项 | `WORK_ITEM_FIELD_MANAGE` |

工作项从项目 A 移动到项目 B 时，同时要求项目 A 的 `WORK_ITEM_UPDATE` 和项目 B 的 `WORK_ITEM_CREATE`。

## 13. 常见业务错误

| code | msg | 前端处理建议 |
|---:|---|---|
| `1050000004` | 项目成员不存在 | 刷新成员列表 |
| `1050000005` | 项目成员已存在 | 提示无需重复添加 |
| `1050000006` | 项目负责人不能直接移除 | 提示先转移负责人 |
| `1050000009` | 当前仅支持私有项目 | 将公开性固定为 `PRIVATE` |
| `1050000010` | 项目角色不存在 | 刷新角色列表 |
| `1050000011` | 项目角色编码已存在 | 提示修改角色编码 |
| `1050000012` | 内置角色不可修改或删除 | 禁用内置角色编辑/删除入口 |
| `1050000013` | 管理员角色权限不可修改 | 管理员权限只读展示 |
| `1050000014` | 项目权限编码无效 | 刷新固定权限目录或检查前端常量 |
| `1050000015` | 参与人角色必须保留权限 | 恢复必需权限选项 |
| `1050000016` | 项目角色仍有关联成员，不能删除 | 先调整成员角色 |
| `1050000017` | 项目成员至少需要一个角色 | 阻止提交空角色集合 |
| `1050000018` | 项目角色作用域或归属项目不匹配 | 刷新当前项目角色列表 |
| `1050000019` | 仅项目管理员可执行该操作 | 隐藏治理入口并提示无权限 |
| `1050000020` | 缺少项目权限 | 刷新当前用户项目权限 |
| `1050000021` | 项目负责人必须保留管理员角色 | 保留负责人管理员角色 |

系统权限或项目权限未通过时，沿用管理后台统一无权限处理。
