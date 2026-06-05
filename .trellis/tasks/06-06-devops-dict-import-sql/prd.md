# DevOps Dict Import SQL

## Goal

Provide a standalone SQL file for importing DevOps dictionary types and data derived from `sql/mysql/devops.sql`.

## Requirements

- Create a SQL file under `sql/mysql/`.
- Use plain multiple `INSERT INTO ... VALUES ...;` statements.
- Do not use `INSERT ... SELECT`, derived tables, or `UNION ALL`.
- Follow the table columns represented by `DictTypeDO` and `DictDataDO`:
  - `system_dict_type`
  - `system_dict_data`
- Include DevOps-specific dictionaries inferred from `devops.sql`.
- Keep common status fields reusable through existing `common_status`; do not duplicate it.

## Acceptance Criteria

- [x] SQL file contains dictionary type inserts.
- [x] SQL file contains dictionary data inserts.
- [x] SQL file uses multiple direct `INSERT` statements.
- [x] SQL file does not hard-code IDs.
- [x] SQL file is readable and suitable for direct import.

## Out of Scope

- No Java code changes.
- No schema changes to existing DevOps tables.
- No automatic duplicate-safe stored procedure or migration framework.

## Technical Notes

- Source schema: `sql/mysql/devops.sql`
- Dict type model: `yudao-module-system/yudao-module-system-server/src/main/java/cn/iocoder/yudao/module/system/dal/dataobject/dict/DictTypeDO.java`
- Dict data model: `yudao-module-system/yudao-module-system-server/src/main/java/cn/iocoder/yudao/module/system/dal/dataobject/dict/DictDataDO.java`
