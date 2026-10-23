# 技术设计：DevOps 应用成员管理

## 1. 结论摘要

- 新增一张租户业务表 `dev_application_member`，只存「成员行」(`app_id`, `user_id`)，**不存 `member_type`**。
- `memberType` 由 `dev_application.owner_user_id` 派生：`userId == owner_user_id` → `OWNER`，否则 `PARTICIPANT`。
  - 好处：拥有者只有一个事实来源，`list` 天然与 `owner_user_id` 一致，不存在两处数据打架的问题。
  - 因此「拥有者是否物化进成员表」对契约无影响：物化了就直接用该行（保留 `id`/`createTime`），没物化就实时合成一行（`id=null`）。
- 转移拥有者 = 更新 `dev_application.owner_user_id` + 确保原拥有者在成员表里有一行（不存在则插入/恢复）。目标成员的既有行无需改写，派生后自动变 `OWNER`。
- 权限沿用 `devops:application:query/update`；服务层对四个方法统一执行 `请求者 == owner_user_id` 校验。

## 2. 数据模型

```text
dev_application_member
  id          bigint  auto_increment  PK
  app_id      bigint  NOT NULL        -- 应用编号
  user_id     bigint  NOT NULL        -- 成员用户编号
  creator/create_time/updater/update_time/deleted/tenant_id   -- 审计 + 逻辑删除 + 租户
  UNIQUE KEY uk_tenant_app_user (tenant_id, app_id, user_id)
  KEY        idx_tenant_app     (tenant_id, app_id)
```

- 沿用 `dev_application_env` 的 `uk_tenant_*` / `idx_tenant_*` 命名风格。
- `deleted` 使用 MyBatis-Plus `@TableLogic`；唯一键不含 `deleted`，因此重新添加同一成员走「恢复」而非「删除+插入」，避免唯一键冲突（见 database-guidelines「Common Mistakes」）。

## 3. 分层与文件边界

```text
yudao-module-devops-api
  enums/ErrorCodeConstants.java                 + 5 个 APPLICATION_MEMBER_* 错误码
  enums/ApplicationMemberTypeEnum.java          新增（OWNER / PARTICIPANT）

yudao-module-devops-server
  dal/dataobject/application/ApplicationMemberDO.java          新增
  dal/mysql/application/ApplicationMemberMapper.java           新增
  service/application/ApplicationMemberService.java            新增
  service/application/ApplicationMemberServiceImpl.java        新增
  controller/admin/application/ApplicationMemberController.java 新增
  controller/admin/application/vo/ApplicationMemberRespVO.java  新增
  controller/admin/application/vo/ApplicationMemberAddReqVO.java 新增
  controller/admin/application/vo/ApplicationMemberTransferOwnerReqVO.java 新增

sql/mysql/devops.sql                             + dev_application_member 建表
sql/mysql/devops_incremental_application_member.sql  新增（CREATE TABLE IF NOT EXISTS）
```

不修改 `ApplicationController` / `ApplicationService`：成员管理是独立子资源，单独 controller 更贴合 `/devops/application/member` 前缀与前端契约。

## 4. 接口契约

| 方法 | 路径 | 权限 | Service |
|---|---|---|---|
| GET | `/devops/application/member/list?appId=` | `devops:application:query` | `getApplicationMemberList(appId, operatorUserId)` |
| POST | `/devops/application/member/add` | `devops:application:update` | `addApplicationMembers(reqVO, operatorUserId)` |
| DELETE | `/devops/application/member/remove?appId=&userId=` | `devops:application:update` | `removeApplicationMember(appId, userId, operatorUserId)` |
| PUT | `/devops/application/member/transfer-owner` | `devops:application:update` | `transferApplicationOwner(reqVO, operatorUserId)` |

- `operatorUserId` 由 controller 用 `SecurityFrameworkUtils.getLoginUserId()` 传入（沿用 `submitApplicationReleaseBranch` 的既有模式）。
- `list` 标记 `@TransMethodResult`，昵称/头像由 `ApplicationMemberRespVO.userId` 上的 `@Trans(AUTO_TRANS, AdminUserApi.PREFIX)` 批量填充。
- 返回：`list` → `CommonResult<List<ApplicationMemberRespVO>>`；其余 → `CommonResult<Boolean>`。

## 5. 关键流程

### 5.1 拥有者校验（四个方法统一入口）

```java
ApplicationDO application = applicationMapper.selectById(appId);
if (application == null) throw exception(APPLICATION_NOT_EXISTS);
if (!Objects.equals(application.getOwnerUserId(), operatorUserId))
    throw exception(APPLICATION_MEMBER_OWNER_ONLY);
```

### 5.2 列表

1. 拥有者校验。
2. `selectListByAppId(appId)`，Mapper 内固定 `ORDER BY create_time ASC, id ASC`。
3. 遍历成员行：`userId == owner_user_id` 的行 → `OWNER`，放到结果首位；其余 → `PARTICIPANT`。
4. 若遍历完没有拥有者行，则在首位合成 `{id: null, userId: owner_user_id, memberType: OWNER, createTime: application.createTime}`。
5. 结果 = `[拥有者] + 参与者（已按 createTime 升序）`。

### 5.3 添加参与者（幂等）

1. 拥有者校验。
2. `userIds` 去重（`LinkedHashSet`）并剔除 `owner_user_id`（拥有者不能作为参与者）。
3. `adminUserApi.validateUserList(userIds).checkError()` 校验用户存在。
4. 一次 `IN` 查询取出这些用户在**含逻辑删除**下的现有行。
5. 逐用户：不存在 → `insert`；存在且已逻辑删除 → `restoreById`；存在且有效 → 跳过。
6. 新插入行仅写 `app_id` / `user_id`，类型由拥有者关系派生，固定为 `PARTICIPANT`。

### 5.4 移除参与者

1. 拥有者校验。
2. `userId == owner_user_id` → `APPLICATION_MEMBER_OWNER_CANNOT_REMOVE`。
3. 按 `appId + userId` 查有效成员行，不存在 → `APPLICATION_MEMBER_NOT_EXISTS`。
4. `deleteById`（逻辑删除）。

### 5.5 转移拥有者（单事务）

```java
@Transactional(rollbackFor = Exception.class)
```

1. 拥有者校验（请求者必须是当前拥有者）。
2. `targetUserId == owner_user_id` → `APPLICATION_MEMBER_TRANSFER_SELF`。
3. 目标必须有有效成员行 → 否则 `APPLICATION_MEMBER_TRANSFER_TARGET_NOT_MEMBER`。
4. `ensureMemberRow(application, oldOwnerUserId)`：原拥有者若没有成员行则插入（含恢复历史删除行），使其派生为 `PARTICIPANT`。
5. `UPDATE dev_application SET owner_user_id = targetUserId WHERE id = appId`。
6. 目标既有成员行保持不变；派生后其 `memberType` 变为 `OWNER`，`id`/`createTime` 不丢。

> 副作用（有意为之）：`updateApplication` 若直接改 `owner_user_id`，列表派生结果同样自动一致，无需额外同步代码。

## 6. 校验与错误矩阵

| 条件 | 结果 |
|---|---|
| 应用不存在 | `APPLICATION_NOT_EXISTS` |
| 请求者不是应用拥有者 | `APPLICATION_MEMBER_OWNER_ONLY` |
| 移除拥有者 | `APPLICATION_MEMBER_OWNER_CANNOT_REMOVE` |
| 移除的成员不存在 | `APPLICATION_MEMBER_NOT_EXISTS` |
| 转移目标不是成员 | `APPLICATION_MEMBER_TRANSFER_TARGET_NOT_MEMBER` |
| 转移给当前拥有者 | `APPLICATION_MEMBER_TRANSFER_SELF` |
| 添加时 `userIds` 为空 | Bean Validation（`@NotEmpty`） |
| 添加的 userId 不存在 | `adminUserApi.validateUserList` 抛出的既有用户错误 |
| 添加的 userId 已是成员 / 是拥有者 | 跳过，返回成功（幂等） |
| 缺少系统权限 | Spring Security 拒绝，不进 Service |

错误码（`1_011_000_010` ~ `014`，接在应用段 `1_011_000_00x` 之后）：

```text
APPLICATION_MEMBER_NOT_EXISTS                   1_011_000_010  应用成员不存在
APPLICATION_MEMBER_OWNER_CANNOT_REMOVE          1_011_000_011  应用拥有者不能被移除
APPLICATION_MEMBER_OWNER_ONLY                   1_011_000_012  只有应用拥有者可以管理成员
APPLICATION_MEMBER_TRANSFER_TARGET_NOT_MEMBER   1_011_000_013  转移目标必须是应用成员
APPLICATION_MEMBER_TRANSFER_SELF                1_011_000_014  转移目标不能是当前拥有者
```

## 7. Mapper 契约

```java
List<ApplicationMemberDO> selectListByAppId(Long appId);          // ORDER BY create_time ASC, id ASC
ApplicationMemberDO selectByAppIdAndUserId(Long appId, Long userId);
List<ApplicationMemberDO> selectListByTenantIdAndAppIdAndUserIdsIncludingDeleted(tenantId, appId, userIds);
ApplicationMemberDO selectByTenantIdAndAppIdAndUserIdIncludingDeleted(tenantId, appId, userId);
int restoreById(Long id, Long tenantId, LocalDateTime updateTime);
```

`including-deleted` 查询与 `restoreById` 采用 `@Select` / `@Update` 手写 SQL，显式带 `tenant_id`，与 `ApplicationEnvMapper` / `ProjectMemberMapper` 保持一致。

## 8. 兼容 / 回滚

- 只新增表、接口与错误码，不改既有接口签名，前端可独立上线。
- 回滚：删除 `ApplicationMemberController` 及其 VO/Service/Mapper/DO 即可；`dev_application_member` 表可保留（不影响既有链路），必要时再 DROP。
- `ApplicationMemberTypeEnum` 为新增枚举，不修改既有枚举。

## 9. 备选方案与取舍

- **存储 `member_type` 列**：需要与 `owner_user_id` 双写并保持一致，`transfer-owner` 要改两行；一旦 `updateApplication` 改了拥有者就会出现两份真相。弃用。
- **完全虚拟拥有者（成员表只存参与者）**：`transfer-owner` 后目标成员行需要删除，前端会看到 `id` 由数字变 null、`createTime` 丢失。改为「目标行保留 + 原拥有者补行」，兼顾单一事实来源与行标识稳定。
