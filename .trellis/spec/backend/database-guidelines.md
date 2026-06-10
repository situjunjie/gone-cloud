# Database Guidelines

This codebase uses MyBatis-Plus through Yudao framework helpers. The dominant pattern is `DO` entities in `dal/dataobject`, mapper interfaces in `dal/mysql`, and SQL-free query construction with `LambdaQueryWrapperX`.

## Persistence Model

Use the existing stack:

- Business data objects in tenant-scoped modules should extend `TenantBaseDO`, which already inherits `BaseDO` and adds `tenantId`.
- Only platform/core entities that are intentionally outside tenant isolation should extend `BaseDO` directly, typically together with explicit tenant-ignore semantics.
- Mappers extend `BaseMapperX<T>`.
- Query logic lives in mapper default methods, not scattered through services.
- Database scripts are maintained under `sql/<engine>/`.

Examples:

- [TenantBaseDO.java](/Users/situjunjie/projects/gone-cloud/yudao-framework/yudao-spring-boot-starter-biz-tenant/src/main/java/cn/iocoder/yudao/framework/tenant/core/db/TenantBaseDO.java:1) is the tenant-scoped base type and adds `tenantId` on top of `BaseDO`.
- [IotProductDO.java](/Users/situjunjie/projects/gone-cloud/yudao-module-iot/yudao-module-iot-server/src/main/java/cn/iocoder/yudao/module/iot/dal/dataobject/product/IotProductDO.java:1) extends `TenantBaseDO`.
- [AdminUserDO.java](/Users/situjunjie/projects/gone-cloud/yudao-module-system/yudao-module-system-server/src/main/java/cn/iocoder/yudao/module/system/dal/dataobject/user/AdminUserDO.java:1) extends `TenantBaseDO`.
- [TenantDO.java](/Users/situjunjie/projects/gone-cloud/yudao-module-system/yudao-module-system-server/src/main/java/cn/iocoder/yudao/module/system/dal/dataobject/tenant/TenantDO.java:1) extends `BaseDO` and is marked `@TenantIgnore` because it is part of the platform's core tenant system itself.
- [ImRtcCallMapper.java](/Users/situjunjie/projects/gone-cloud/yudao-module-im/yudao-module-im-server/src/main/java/cn/iocoder/yudao/module/im/dal/mysql/rtc/ImRtcCallMapper.java:1) extends `BaseMapperX<ImRtcCallDO>` and exposes domain-specific query/update helpers.
- Multi-engine bootstrap SQL lives under [sql/mysql](/Users/situjunjie/projects/gone-cloud/sql/mysql), [sql/postgresql](/Users/situjunjie/projects/gone-cloud/sql/postgresql), [sql/oracle](/Users/situjunjie/projects/gone-cloud/sql/oracle), and other sibling directories.

## Tenant Boundary Rule

For new business modules and business entities:

- Default to `TenantBaseDO`.
- Treat `tenantId` as part of the entity contract, not an optional add-on.
- Use direct `BaseDO` only when the data is platform-global, system-core, or explicitly excluded from tenant filtering by design.

This matters for new domain modeling work: if the feature serves tenant business data, the draft entity list should assume tenant scope from the beginning.

## Query Patterns

Preferred mapper style:

1. Define small, named default methods on the mapper.
2. Use `LambdaQueryWrapperX` and `eqIfPresent`, `betweenIfPresent`, `orderByDesc`, and similar helpers for conditional filters.
3. Use `selectPage(reqVO, wrapper)` for paginated admin listings.
4. Use targeted update conditions for optimistic state transitions.

Example patterns from [ImRtcCallMapper.java](/Users/situjunjie/projects/gone-cloud/yudao-module-im/yudao-module-im-server/src/main/java/cn/iocoder/yudao/module/im/dal/mysql/rtc/ImRtcCallMapper.java:1):

- `selectByRoom(String room)` for direct key lookup
- `selectPage(ImRtcCallManagerPageReqVO reqVO)` for page queries
- `updateByIdAndStatus(...)` for guarded state changes

## Transactions

Transactions are declared at service or controller orchestration boundaries when one operation spans multiple writes. When a module already uses `@Transactional(rollbackFor = Exception.class)`, follow that local pattern rather than moving transaction control into mapper code.

Examples:

- [MesWmStockTakingTaskResultController.java](/Users/situjunjie/projects/gone-cloud/yudao-module-mes/yudao-module-mes-server/src/main/java/cn/iocoder/yudao/module/mes/controller/admin/wm/stocktaking/task/MesWmStockTakingTaskResultController.java:83)
- [MesWmStockTakingTaskResultController.java](/Users/situjunjie/projects/gone-cloud/yudao-module-mes/yudao-module-mes-server/src/main/java/cn/iocoder/yudao/module/mes/controller/admin/wm/stocktaking/task/MesWmStockTakingTaskResultController.java:91)

## Naming Conventions

- Tables and columns follow the SQL scripts already shipped with the scaffold; do not invent a second naming scheme per module.
- Java persistence classes use `<DomainName>DO` based on the business aggregate inside the module package. Do not repeat the module name as a class prefix; for example use `ApplicationDO` under `cn.iocoder.yudao.module.devops...`, not `DevopsApplicationDO`.
- Mapper interfaces use `<DomainName>Mapper`.
- Request objects for list pages usually use `<DomainName>PageReqVO`.

## SQL Script Conventions

- Keep vendor-specific schema/bootstrap files under the matching `sql/<engine>/` directory.
- If a change is schema-affecting, update each supported engine only if the project already maintains that object there. Do not add one-off SQL only for MySQL if the same table is cross-engine in this repo.
- Reuse existing helper docs under `sql/tools/` when schema conversion matters.

## Wrong vs Correct

### Wrong

- Writing ad hoc query logic in services with raw mapper wrapper code repeated in multiple places
- Returning huge unfiltered lists when surrounding modules expose paged admin listings
- Using bare `updateById` for state transitions that should check current status

### Correct

- Add a named mapper default method with `LambdaQueryWrapperX`
- Use `selectPage(reqVO, wrapper)` for admin screens
- Guard updates with predicates like `eq(id)` and `eq(status, oldStatus)`

## Common Mistakes

- Using `BaseDO` directly for new tenant business entities and accidentally dropping `tenantId`.
- Skipping `BaseDO`/`TenantBaseDO` entirely and losing standard audit columns.
- Putting cross-record write orchestration in mapper methods instead of the service/controller layer.
- Forgetting that this repository carries multiple database engines, so schema changes need a broader scan than one SQL file.
- Replacing logical-delete relation rows with `delete + insert` when the business unique key does not include `deleted`.
  Use differential update instead: update active rows in place, restore logically deleted rows when the same business key is requested again, insert only truly new rows, and logically delete only rows removed from the submitted set. This preserves relation IDs referenced by downstream tables and avoids unique-key collisions.

## Scenario: Secret Fields Stored Through MyBatis

### 1. Scope / Trigger

- Trigger: adding database columns that store credentials, tokens, passwords, private keys, webhook secrets, or external-system access material.
- Scope: backend `DO` classes, MyBatis-Plus mappings, SQL comments, and runtime configuration for modules under `yudao-module-*`.

### 2. Signatures

- Java DO annotation:
  ```java
  @TableName(value = "xxx_table", autoResultMap = true)
  public class XxxDO extends TenantBaseDO {
      @TableField(typeHandler = EncryptTypeHandler.class)
      @ToString.Exclude
      private String accessToken;
  }
  ```
- Required import: `cn.iocoder.yudao.framework.mybatis.core.type.EncryptTypeHandler`.
- Required config key: `mybatis-plus.encryptor.password`.

### 3. Contracts

- Request VOs may accept the raw secret when creating or rotating the credential.
- Response VOs must not expose the raw secret; return a mask field such as `tokenMask` when UI display is needed.
- SQL column comments should state that the value is encrypted, for example `访问令牌，加密存储`.
- Runtime environments that read/write encrypted fields must define `mybatis-plus.encryptor.password`; missing values fail at persistence time.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| Create request omits a required token/password | Throw a module `ErrorCodeConstants` business error |
| Update request omits token/password intentionally | Preserve the existing encrypted value when local product semantics allow it |
| Response VO includes raw token/password | Reject in review; expose only a mask or derived metadata |
| `@TableField(typeHandler = EncryptTypeHandler.class)` is used without `autoResultMap = true` | Fix the DO mapping before merging |
| `mybatis-plus.encryptor.password` is missing in the active runtime config | Add the config key; do not bypass encryption |

### 5. Good / Base / Bad Cases

- Good: access token is encrypted by `EncryptTypeHandler`, excluded from `toString()`, hidden from response VOs, and paired with a `tokenMask`.
- Base: an existing module already has `mybatis-plus.encryptor.password` in shared application config; the new DO only needs the type handler annotations.
- Bad: raw access tokens are stored as plain `String` fields and returned by MapStruct into a response VO.

### 6. Tests Required

- Compile the affected module so MyBatis annotations and MapStruct mappings are checked.
- For service tests, assert create-time missing-secret validation, update-time preserve-secret behavior, and that response conversion has no raw secret field.
- For DB tests, assert inserting and selecting through the mapper returns the decrypted value while the raw DB value is encrypted when an embedded database is available.

### 7. Wrong vs Correct

#### Wrong

```java
@TableName("devops_repository_provider")
private String accessToken;
```

#### Correct

```java
@TableName(value = "devops_repository_provider", autoResultMap = true)
private String tokenMask;

@TableField(typeHandler = EncryptTypeHandler.class)
@ToString.Exclude
private String accessToken;
```
