# Database Guidelines

This codebase uses MyBatis-Plus through Yudao framework helpers. The dominant pattern is `DO` entities in `dal/dataobject`, mapper interfaces in `dal/mysql`, and SQL-free query construction with `LambdaQueryWrapperX`.

## Persistence Model

Use the existing stack:

- Data objects usually extend `BaseDO` for common audit fields.
- Mappers extend `BaseMapperX<T>`.
- Query logic lives in mapper default methods, not scattered through services.
- Database scripts are maintained under `sql/<engine>/`.

Examples:

- [ImRtcCallDO.java](/Users/situjunjie/projects/gone-cloud/yudao-module-im/yudao-module-im-server/src/main/java/cn/iocoder/yudao/module/im/dal/dataobject/rtc/ImRtcCallDO.java:1) extends `BaseDO`.
- [ImRtcCallMapper.java](/Users/situjunjie/projects/gone-cloud/yudao-module-im/yudao-module-im-server/src/main/java/cn/iocoder/yudao/module/im/dal/mysql/rtc/ImRtcCallMapper.java:1) extends `BaseMapperX<ImRtcCallDO>` and exposes domain-specific query/update helpers.
- Multi-engine bootstrap SQL lives under [sql/mysql](/Users/situjunjie/projects/gone-cloud/sql/mysql), [sql/postgresql](/Users/situjunjie/projects/gone-cloud/sql/postgresql), [sql/oracle](/Users/situjunjie/projects/gone-cloud/sql/oracle), and other sibling directories.

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
- Java persistence classes use `<DomainName>DO`.
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

- Skipping `BaseDO` on new persisted entities and losing standard audit columns.
- Putting cross-record write orchestration in mapper methods instead of the service/controller layer.
- Forgetting that this repository carries multiple database engines, so schema changes need a broader scan than one SQL file.
