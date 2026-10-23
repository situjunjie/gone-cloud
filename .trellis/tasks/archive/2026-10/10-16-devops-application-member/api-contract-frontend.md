# 应用成员管理 —— 前端对接说明

> 后端已实现完成，以下是可直接对接的接口契约。
> 网关前缀：`/admin-api`；应用成员模块路径：`/devops/application/member`
> 即完整地址为 `/admin-api/devops/application/member/...`

## 0. 通用约定

**统一响应包装**（所有接口一致）：

```json
{ "code": 0, "msg": "", "data": ... }
```

- `code === 0` 表示成功，其余为业务/系统错误，`msg` 可直接展示给用户。
- 常见非业务错误码：`401` 账号未登录、`403` 没有该操作权限、`400` 请求参数不正确。

**ID 序列化**：所有 `Long` 类型字段（`id` / `appId` / `userId`）遵循全平台无损 JSON 契约 ——
安全整数范围内是 **number**，超出 `2^53-1` 时是 **string**。
前端类型请统一声明为 `number | string`，不要做 `typeof === 'number'` 的强假设。

**时间字段**：`createTime` 是 **epoch 毫秒数字（number）**，不是字符串。
`dayjs(createTime)` 可直接使用；不要按 ISO 字符串解析。

**权限与身份**：

- 权限沿用现有 `devops:application:query`（列表）与 `devops:application:update`（增/删/改），**没有新增菜单权限点**。
- 服务端额外强制校验「**请求者 == 该应用拥有者**」（`dev_application.owner_user_id`）。
  即使用户拥有 `devops:application:update`，只要不是该应用的拥有者，所有写操作都会返回
  `1011000012 只有应用拥有者可以管理成员`。
- 因此前端应仅在 `当前登录用户 id === 应用详情.ownerUserId` 时展示成员管理入口/操作按钮，
  非拥有者进入该页面时只能只读展示（列表接口同样受拥有者校验，非拥有者连列表都拿不到）。

---

## 1. 成员列表

```http
GET /admin-api/devops/application/member/list?appId=1024
```

权限：`devops:application:query`

**响应 `data`**：`ApplicationMemberVO[]`（非分页，一次返回全部成员）

```ts
interface ApplicationMemberVO {
  id?: number | string;      // 成员行 id；【拥有者若未物化时为 null】
  appId: number | string;
  userId: number | string;
  userName?: string;         // 昵称，后端通过 Easy-Trans 填充
  userAvatar?: string;       // 头像
  memberType: 'OWNER' | 'PARTICIPANT';
  createTime?: number;       // epoch 毫秒
}
```

**响应示例**：

```json
{
  "code": 0,
  "msg": "",
  "data": [
    {
      "id": null,
      "appId": 1024,
      "userId": 1,
      "userName": "张三",
      "userAvatar": "https://www.iocoder.cn/1.png",
      "memberType": "OWNER",
      "createTime": 1767196800000
    },
    {
      "id": 200,
      "appId": 1024,
      "userId": 2,
      "userName": "李四",
      "userAvatar": null,
      "memberType": "PARTICIPANT",
      "createTime": 1767283200000
    }
  ]
}
```

**契约要点（前端必须遵守）**：

1. **列表一定包含且仅包含一行 `memberType === 'OWNER'`**，其 `userId` 恒等于
   `应用详情接口返回的 ownerUserId`。不要自己再合成拥有者行。
2. **排序已由后端保证**：`[拥有者] + 参与者（按 createTime 升序）`。前端**不要再排序**，
   否则会把拥有者挤下去。
3. **拥有者行的 `id` 可能为 `null`**（拥有者未被物化进成员表时）。
   → **列表渲染的 `key` 请用 `userId`（或 `${memberType}-${userId}`），不要用 `id`**。
   → 拥有者行也不要有「移除」按钮。
4. `userName` / `userAvatar` 在用户被删除等异常情况下可能为 `null`，请做兜底展示。

---

## 2. 添加参与者（支持批量）

```http
POST /admin-api/devops/application/member/add
Content-Type: application/json

{ "appId": 1024, "userIds": [2, 3, 4] }
```

权限：`devops:application:update`

**请求体**：

| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| `appId` | `number` | 是 | 应用编号 |
| `userIds` | `number[]` | 是 | 非空数组；**单次最多 100 个** |

**响应 `data`**：`true`

**契约要点**：

1. **幂等**：已在成员列表中的 `userId` 直接跳过，**不报错**，接口整体仍返回成功。
   前端无需先做去重/过滤，可放心重复提交。
2. 新成员类型**固定为 `PARTICIPANT`**，请求中不需要也不能传成员类型。
3. 若 `userIds` 中包含**拥有者自己**，该 id 会被静默忽略（拥有者不能作为参与者），不报错。
4. 请求数组内部重复的 `userId` 会自动去重。
5. 若某个 `userId` 在系统中不存在，接口会返回业务错误（用户校验错误），
   此时**整批都不生效**（同一事务）。建议前端用「选人组件」保证 id 有效性。
6. 调用成功后建议**重新拉取列表**，不要在前端本地拼装新行（`createTime`/`id` 由后端生成）。

---

## 3. 移除参与者

```http
DELETE /admin-api/devops/application/member/remove?appId=1024&userId=2
```

权限：`devops:application:update`

**参数**（query，均为必填）：

| 参数 | 类型 | 说明 |
|---|---|---|
| `appId` | `number` | 应用编号 |
| `userId` | `number` | 要移除的**用户编号** |

**响应 `data`**：`true`

**契约要点**：

1. **用 `appId + userId` 定位，不是成员行 `id`**。这样后端无论是否把拥有者物化进成员表，
   契约都不变。前端请不要传 `id`。
2. **移除拥有者必然失败**，返回 `1011000011 应用拥有者不能被移除`。
   → 拥有者行不要渲染移除按钮。
3. 要移除的用户不在成员列表中 → `1011000010 应用成员不存在`。
4. 这是逻辑删除，被移除的用户之后再次 `add` 会恢复同一行，不会报重复。

---

## 4. 转移拥有者

```http
PUT /admin-api/devops/application/member/transfer-owner
Content-Type: application/json

{ "appId": 1024, "userId": 2 }
```

权限：`devops:application:update`

**请求体**：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `appId` | `number` | 是 | 应用编号 |
| `userId` | `number` | 是 | **新拥有者**的用户编号 |

**响应 `data`**：`true`

**契约要点**：

1. **目标必须是已有成员**（即已出现在列表接口返回中）。否则返回
   `1011000013 转移目标必须是应用成员`。
   → 前端交互建议：转移入口只从成员列表的**参与者行**上发起，或选人时只允许选列表内成员。
2. 转移给当前拥有者 → `1011000014 转移目标不能是当前拥有者`。
3. 后端在**同一事务**内完成：目标 → `OWNER`、原拥有者 → `PARTICIPANT`、
   `dev_application.owner_user_id` 同步更新。
   → 转移成功后**必须重新拉取成员列表**：拥有者行会变，且原拥有者会变成参与者行。
   → 若页面上同时展示「应用详情」的负责人字段，也请一并刷新。
4. 转移成功后，**当前登录用户很可能不再是拥有者**，后续所有成员写操作都会返回
   `1011000012`。前端应在转移成功后重新判断当前用户是否为拥有者，并相应收起操作按钮。

---

## 5. 我参与的应用分页（复用应用分页接口）

```http
GET /admin-api/devops/application/page?mine=true&pageNo=1&pageSize=10
```

权限：`devops:application:query`

**没有新增接口**，直接在既有应用分页接口上增加一个 `mine` 参数，**响应结构与原接口完全一致**
（`PageResult<ApplicationRespVO>`）。

**新增请求参数**：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `mine` | `boolean` | 否 | `true` 时只返回「当前登录用户参与的应用」；不传或 `false` 时保持原有全量分页行为 |

`mine` 可与其他既有参数（`appKey` / `name` / `status` / `repoProviderType` / `favori` / `createTime` 等）**自由组合**。

**「参与」的口径**：当前用户是该应用的 **拥有者（OWNER）或参与者（PARTICIPANT）**，两者取并集。

> 注意：拥有者不一定在成员表里有行（见 §1 第 3 点），所以后端用
> `owner_user_id = 当前用户 OR 存在成员行` 的并集过滤，保证自己创建/负责的应用一定出现在结果里。

**响应示例**：

```json
{
  "code": 0,
  "msg": "",
  "data": {
    "list": [
      {
        "id": 1024,
        "appKey": "gone-cloud",
        "name": "Gone Cloud",
        "icon": null,
        "repoProviderType": "GITLAB",
        "ownerUserId": 1,
        "ownerUserName": "张三",
        "ownerUserAvatar": null,
        "status": 0,
        "createTime": 1767196800000
      }
    ],
    "total": 1
  }
}
```

**契约要点**：

1. 响应字段与 `/admin-api/devops/application/page` 完全相同，**前端可直接复用同一套类型定义与列表组件**。
2. `mine` 的过滤主体**永远由后端取当前登录用户**，前端无法（也不需要）指定用户 id。
   即使请求里带了内部的 `memberUserId` 参数，也会被后端用登录用户覆盖（未开启 `mine` 时直接置空）。
3. 不传 `mine` 时行为与改动前**完全一致**，既有调用方无需修改。
4. 排序仍为 `id DESC`，与既有分页一致。
5. 分页参数沿用全局约定：`pageNo` 默认 1、`pageSize` 默认 10；`pageSize = -1` 表示不分页返回全部。

---

## 6. 错误码速查

| code | msg | 触发场景 |
|---|---|---|
| `0` | 成功 | — |
| `1011000000` | 应用不存在 | `appId` 对应应用不存在或已删除 |
| `1011000010` | 应用成员不存在 | 移除的用户不在成员列表中 |
| `1011000011` | 应用拥有者不能被移除 | 尝试移除 `OWNER` 行 |
| `1011000012` | 只有应用拥有者可以管理成员 | 请求者不是该应用拥有者（**最常见**） |
| `1011000013` | 转移目标必须是应用成员 | 转移目标不是成员 |
| `1011000014` | 转移目标不能是当前拥有者 | 转移给自己 |
| `401` | 账号未登录 | token 失效 |
| `403` | 没有该操作权限 | 缺少 `devops:application:query` / `devops:application:update` |
| `400` | 请求参数不正确 | 缺少必填字段、`userIds` 为空、超过 100 个 |

---

## 7. 前端实现清单

- [ ] 4 个成员接口的 TS 类型定义，`Long` 字段用 `number | string`。
- [ ] 列表 key 用 `userId`，不用 `id`（拥有者行 `id` 可能为 null）。
- [ ] 列表不做前端排序，直接按后端返回顺序渲染。
- [ ] 拥有者行不渲染「移除」按钮；「转移拥有者」只对参与者行开放。
- [ ] 判断当前用户是否为拥有者（对比应用详情的 `ownerUserId`），非拥有者隐藏所有操作按钮。
- [ ] 添加/移除/转移成功后统一重新拉取列表（转移还需刷新应用详情）。
- [ ] `createTime` 用 `dayjs(createTime)` 格式化，不要当字符串解析。
- [ ] `1011000012` 单独提示文案（例如「只有应用拥有者可以管理成员」），避免用户困惑。
- [ ] 「我参与的应用」列表复用应用分页的请求/响应类型，只多传一个 `mine=true`。
