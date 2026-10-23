# 实施计划：DevOps 应用成员管理

## 1. 改动清单（按顺序）

1. [x] `yudao-module-devops-api`：`ErrorCodeConstants` 增加 5 个 `APPLICATION_MEMBER_*` 错误码；新增 `ApplicationMemberTypeEnum`。
2. [x] `dal/dataobject/application/ApplicationMemberDO.java`（`TenantBaseDO` + `@KeySequence("dev_application_member_seq")`）。
3. [x] `dal/mysql/application/ApplicationMemberMapper.java`（`BaseMapperX` + 命名查询 + `including-deleted` + `restoreById`）。
4. [x] 三个 VO：`ApplicationMemberRespVO`（`@Trans` 昵称/头像）、`ApplicationMemberAddReqVO`、`ApplicationMemberTransferOwnerReqVO`。
5. [x] `ApplicationMemberService` + `ApplicationMemberServiceImpl`（拥有者校验、列表合成、幂等添加、移除保护、转移事务）。
6. [x] `ApplicationMemberController`（4 个 endpoint、权限注解、`getLoginUserId()`、`@TransMethodResult`）。
7. [x] SQL：`sql/mysql/devops.sql` 增加建表；新增 `sql/mysql/devops_incremental_application_member.sql`。
8. [x] 测试：`ApplicationMemberServiceImplTest`（`BaseMockitoUnitTest`）。
9. [x] 「我参与的应用分页」：复用 `/devops/application/page`，新增 `mine` 参数 + `memberUserId`（隐藏）过滤；`ApplicationMapperTest` 断言 EXISTS 过滤 SQL。

## 2. 测试清单

- 列表：拥有者未物化 → 首位合成 `id=null` 的 OWNER 行；拥有者已物化 → 复用该行；参与者按 createTime 升序；拥有者排首位。
- 添加：跳过有效成员、跳过拥有者、恢复逻辑删除行、插入新行；`validateUserList` 被调用；空 userIds 由 Bean Validation 拦截。
- 移除：移除拥有者 → `APPLICATION_MEMBER_OWNER_CANNOT_REMOVE`；成员不存在 → `APPLICATION_MEMBER_NOT_EXISTS`；成功逻辑删除。
- 转移：请求者非拥有者 → `APPLICATION_MEMBER_OWNER_ONLY`；目标非成员 → `APPLICATION_MEMBER_TRANSFER_TARGET_NOT_MEMBER`；转移给自己 → `APPLICATION_MEMBER_TRANSFER_SELF`；成功时 `owner_user_id` 被更新且原拥有者行被补齐。
- 拥有者校验：应用不存在 → `APPLICATION_NOT_EXISTS`。

## 3. 验证命令

```bash
mvn -pl yudao-module-devops/yudao-module-devops-server -am -DskipTests compile
mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dtest=ApplicationMemberServiceImplTest test
mvn -pl yudao-module-devops/yudao-module-devops-server -am test
git diff --check
```

## 4. 风险点与回滚点

- 风险文件：`ErrorCodeConstants`（共享枚举，只新增不改动）、`sql/mysql/devops.sql`（只新增表）。
- 第一回滚点：删除成员管理 controller/service/mapper/DO/VO，既有应用接口不受影响。
- 第二回滚点：`dev_application_member` 表可保留或 DROP，均不影响既有链路。
- 唯一键不含 `deleted`，必须用「恢复」而不是「删除+插入」，否则重复添加同一成员会撞唯一键。

## 5. 质量门禁

- [x] 四个接口的 HTTP 契约与前端给出的字段名/类型完全一致。
- [x] 服务端拥有者校验覆盖全部 4 个方法。
- [x] 列表始终包含且仅包含一个 OWNER 行，且 `userId == dev_application.owner_user_id`。
- [x] 转移拥有者为单事务，失败整体回滚。
- [x] 测试与 `git diff --check` 通过。
