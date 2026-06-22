# Implementation Plan

## Ordered Checklist

- [ ] 更新任务文档，确认收藏模型、接口范围和幂等语义。
- [ ] 新增 `UserFavoriteDO`、`UserFavoriteMapper`、`UserFavoriteService` 和实现类。
- [ ] 新增管理后台 `UserFavoriteController` 及相关请求/响应 VO。
- [ ] 在 system 模块错误码、SQL 脚本、单测表结构和清理脚本中加入新表。
- [ ] 为“查询/添加/取消/恢复”补充聚焦单测。
- [ ] 运行 system 模块测试并修复回归。

## Validation Commands

- `mvn -pl yudao-module-system/yudao-module-system-server -am -Dtest='UserFavoriteServiceImplTest' -Dsurefire.failIfNoSpecifiedTests=false test`

## Risky Files

- `yudao-module-system/yudao-module-system-server/src/test/resources/sql/create_tables.sql`
- `sql/mysql/ruoyi-vue-pro.sql`
- `sql/postgresql/ruoyi-vue-pro.sql`
- `sql/oracle/ruoyi-vue-pro.sql`
- `sql/sqlserver/ruoyi-vue-pro.sql`
- `sql/kingbase/ruoyi-vue-pro.sql`
- `sql/opengauss/ruoyi-vue-pro.sql`
- `sql/dm/ruoyi-vue-pro-dm8.sql`

## Review Gates Before Start

- 收藏接口确认只操作当前登录用户。
- 收藏模型确认使用 `bizType + bizId`，不绑定 DevOps 具体表。
- “重复添加”和“取消后再次添加”都具备幂等行为。
- SQL 与单测表结构同步，不只在 H2 测试环境可用。
