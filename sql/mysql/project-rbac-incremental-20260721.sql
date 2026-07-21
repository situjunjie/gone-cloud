-- Project RBAC incremental migration (2026-07-21)
-- Prerequisite: the existing project schema and system menu/dict tables are available.
-- The application lazily initializes tenant-global ADMIN/PARTICIPANT role templates.

-- -----------------------------------------------------------------------------
-- 1. Role, permission, and member-role tables
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `proj_project_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '项目角色编号',
  `scope` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色范围：GLOBAL、PROJECT',
  `project_id` bigint NOT NULL DEFAULT 0 COMMENT '项目编号，全局模板固定为 0',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色编码，创建后不可修改',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色名称',
  `builtin_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NONE' COMMENT '内置类型：ADMIN、PARTICIPANT、NONE',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
  `remark` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_tenant_scope_project_code` (`tenant_id`, `scope`, `project_id`, `code`) USING BTREE,
  KEY `idx_tenant_project_builtin` (`tenant_id`, `project_id`, `builtin_type`) USING BTREE
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目角色及租户角色模板表';

CREATE TABLE IF NOT EXISTS `proj_project_role_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '角色权限编号',
  `role_id` bigint NOT NULL COMMENT '角色编号',
  `permission_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '固定项目权限编码',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_tenant_role_permission` (`tenant_id`, `role_id`, `permission_code`) USING BTREE,
  KEY `idx_tenant_permission_code` (`tenant_id`, `permission_code`) USING BTREE
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目角色权限表';

CREATE TABLE IF NOT EXISTS `proj_project_member_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '成员角色关系编号',
  `project_id` bigint NOT NULL COMMENT '项目编号',
  `member_id` bigint NOT NULL COMMENT '项目成员编号',
  `role_id` bigint NOT NULL COMMENT '项目角色编号',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_tenant_member_role` (`tenant_id`, `member_id`, `role_id`) USING BTREE,
  KEY `idx_tenant_project_role` (`tenant_id`, `project_id`, `role_id`) USING BTREE
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目成员角色关系表';

-- -----------------------------------------------------------------------------
-- 2. Remove the obsolete single-role member model (idempotent)
-- -----------------------------------------------------------------------------
SET @project_schema_name := DATABASE();

SET @project_drop_role_index_sql := IF(
  EXISTS (
    SELECT 1 FROM `information_schema`.`statistics`
    WHERE `table_schema` = @project_schema_name
      AND `table_name` = 'proj_project_member'
      AND `index_name` = 'idx_tenant_project_role'
  ),
  'ALTER TABLE `proj_project_member` DROP INDEX `idx_tenant_project_role`',
  'SELECT 1'
);
PREPARE project_drop_role_index_stmt FROM @project_drop_role_index_sql;
EXECUTE project_drop_role_index_stmt;
DEALLOCATE PREPARE project_drop_role_index_stmt;

SET @project_drop_role_column_sql := IF(
  EXISTS (
    SELECT 1 FROM `information_schema`.`columns`
    WHERE `table_schema` = @project_schema_name
      AND `table_name` = 'proj_project_member'
      AND `column_name` = 'role'
  ),
  'ALTER TABLE `proj_project_member` DROP COLUMN `role`',
  'SELECT 1'
);
PREPARE project_drop_role_column_stmt FROM @project_drop_role_column_sql;
EXECUTE project_drop_role_column_stmt;
DEALLOCATE PREPARE project_drop_role_column_stmt;

-- -----------------------------------------------------------------------------
-- 3. Remove obsolete dictionaries and keep project visibility private-only
-- -----------------------------------------------------------------------------
DELETE FROM `system_dict_data`
WHERE `dict_type` = 'project_member_role';

DELETE FROM `system_dict_type`
WHERE `type` = 'project_member_role';

DELETE FROM `system_dict_data`
WHERE `dict_type` = 'project_visibility' AND `value` = 'PUBLIC';

UPDATE `system_dict_type`
SET `name` = '项目公开性',
    `remark` = '项目表 visibility：当前仅支持 PRIVATE 私密',
    `updater` = '1',
    `update_time` = NOW()
WHERE `type` = 'project_visibility';

-- -----------------------------------------------------------------------------
-- 4. Add system permissions for project/global role governance
-- -----------------------------------------------------------------------------
SET @project_menu_id := (
  SELECT `id` FROM `system_menu`
  WHERE `parent_id` = 0 AND `path` = '/project' AND `deleted` = b'0'
  ORDER BY `id` DESC LIMIT 1
);

SET @project_project_menu_id := (
  SELECT `id` FROM `system_menu`
  WHERE `parent_id` = @project_menu_id AND `path` = 'project' AND `deleted` = b'0'
  ORDER BY `id` DESC LIMIT 1
);

INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '项目角色管理', 'project:project:role', 3, 6, @project_project_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE @project_project_menu_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `parent_id` = @project_project_menu_id
      AND `permission` = 'project:project:role' AND `deleted` = b'0'
  );

INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '全局角色模板查询', 'project:global-role:query', 3, 7, @project_project_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE @project_project_menu_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `parent_id` = @project_project_menu_id
      AND `permission` = 'project:global-role:query' AND `deleted` = b'0'
  );

INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '全局角色模板管理', 'project:global-role:manage', 3, 8, @project_project_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE @project_project_menu_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `parent_id` = @project_project_menu_id
      AND `permission` = 'project:global-role:manage' AND `deleted` = b'0'
  );

-- Verification queries
SELECT `table_name`
FROM `information_schema`.`tables`
WHERE `table_schema` = DATABASE()
  AND `table_name` IN (
    'proj_project_role',
    'proj_project_role_permission',
    'proj_project_member_role'
  )
ORDER BY `table_name`;

SELECT `id`, `name`, `permission`, `parent_id`
FROM `system_menu`
WHERE `permission` IN (
  'project:project:role',
  'project:global-role:query',
  'project:global-role:manage'
)
  AND `deleted` = b'0'
ORDER BY `permission`;
