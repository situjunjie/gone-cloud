-- Existing-database migration for tenant-global work item fields.
-- Run once before deploying code that writes GLOBAL field definitions.

ALTER TABLE `proj_work_item_field`
  ADD COLUMN `scope` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
    NOT NULL DEFAULT 'PROJECT' COMMENT '生效范围：GLOBAL 租户内全局、PROJECT 指定项目' AFTER `id`;

ALTER TABLE `proj_work_item_field`
  DROP INDEX `uk_tenant_project_code`,
  DROP INDEX `idx_tenant_project_enabled_sort`,
  ADD UNIQUE KEY `uk_tenant_scope_project_code` (`tenant_id`, `scope`, `project_id`, `code`) USING BTREE,
  ADD KEY `idx_tenant_scope_project_enabled_sort` (`tenant_id`, `scope`, `project_id`, `enabled`, `sort`) USING BTREE;
