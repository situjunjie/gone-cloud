-- Project worklog incremental migration (2026-07-28).

CREATE TABLE IF NOT EXISTS `proj_worklog` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '工时编号',
  `project_id` bigint NOT NULL COMMENT '归属项目编号',
  `work_item_id` bigint NOT NULL COMMENT '工作项编号',
  `type` varchar(32) NOT NULL COMMENT '工时类型：ESTIMATED、ACTUAL',
  `hours` decimal(10,2) NOT NULL COMMENT '工时数（小时）',
  `category` varchar(32) NOT NULL COMMENT '工作类别',
  `description` varchar(1000) DEFAULT NULL COMMENT '工作描述',
  `owner_user_id` bigint NOT NULL COMMENT '工时负责人用户编号',
  `work_date` date DEFAULT NULL COMMENT '工作日期，实际工时必填',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_project_work_date_type` (`tenant_id`, `project_id`, `work_date`, `type`),
  KEY `idx_tenant_work_item_type` (`tenant_id`, `work_item_id`, `type`),
  KEY `idx_tenant_project_owner_date` (`tenant_id`, `project_id`, `owner_user_id`, `work_date`),
  KEY `idx_tenant_project_category` (`tenant_id`, `project_id`, `category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目工时表';

ALTER TABLE `proj_work_item` DROP COLUMN IF EXISTS `estimated_workload`;

INSERT INTO `system_dict_type` (`name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `deleted_time`)
SELECT '项目工时工作类别', 'project_worklog_category', 0, '项目工时工作类别', '1', NOW(), '1', NOW(), b'0', NULL
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_type` WHERE `type` = 'project_worklog_category' AND `deleted` = b'0');

INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT categories.sort, categories.label, categories.value, 'project_worklog_category', 0, categories.color_type, '', '', '1', NOW(), '1', NOW(), b'0'
FROM (
  SELECT 10 sort, '设计' label, 'DESIGN' value, 'primary' color_type UNION ALL
  SELECT 20, '研发', 'DEVELOPMENT', 'success' UNION ALL
  SELECT 30, '测试', 'TESTING', 'warning' UNION ALL
  SELECT 40, '文档', 'DOCUMENTATION', 'info' UNION ALL
  SELECT 50, '其他', 'OTHER', 'default'
) categories
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` d WHERE d.`dict_type` = 'project_worklog_category'
  AND d.`value` = categories.value AND d.`deleted` = b'0');

-- Existing project participant roles are snapshots. Add the new default permissions explicitly.
INSERT INTO `proj_project_role_permission`
(`project_id`, `role_id`, `permission_code`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT r.`project_id`, r.`id`, permissions.permission_code, '1', NOW(), '1', NOW(), b'0', r.`tenant_id`
FROM `proj_project_role` r
JOIN (SELECT 'WORKLOG_QUERY' permission_code UNION ALL SELECT 'WORKLOG_CREATE') permissions
WHERE r.`builtin_type` = 'PARTICIPANT' AND r.`deleted` = b'0'
  AND NOT EXISTS (SELECT 1 FROM `proj_project_role_permission` p
    WHERE p.`tenant_id` = r.`tenant_id` AND p.`role_id` = r.`id`
      AND p.`permission_code` = permissions.permission_code AND p.`deleted` = b'0')
ON DUPLICATE KEY UPDATE `deleted` = b'0', `updater` = '1', `update_time` = NOW();

SET @project_menu_id := (
  SELECT `id` FROM `system_menu` WHERE `parent_id` = 0 AND `path` = '/project' AND `deleted` = b'0'
  ORDER BY `id` DESC LIMIT 1
);
SET @project_work_item_menu_id := (
  SELECT `id` FROM `system_menu` WHERE `parent_id` = @project_menu_id AND `path` = 'work-item' AND `deleted` = b'0'
  ORDER BY `id` DESC LIMIT 1
);

-- Earlier drafts created a visible worklog page although no frontend page exists. Preserve its menu ids
-- by converting the page into the query button and moving its child buttons under the work-item menu.
SET @legacy_project_worklog_menu_id := (
  SELECT `id` FROM `system_menu` WHERE `parent_id` = @project_menu_id AND `path` = 'worklog' AND `deleted` = b'0'
  ORDER BY `id` DESC LIMIT 1
);
UPDATE `system_menu`
SET `deleted` = b'1', `updater` = '1', `update_time` = NOW()
WHERE @legacy_project_worklog_menu_id IS NOT NULL AND `parent_id` = @project_work_item_menu_id
  AND `permission` IN ('project:worklog:query', 'project:worklog:create', 'project:worklog:update', 'project:worklog:delete')
  AND `deleted` = b'0';
UPDATE `system_menu`
SET `deleted` = b'1', `updater` = '1', `update_time` = NOW()
WHERE `parent_id` = @legacy_project_worklog_menu_id AND `permission` = 'project:worklog:query' AND `deleted` = b'0';
UPDATE `system_menu`
SET `parent_id` = @project_work_item_menu_id, `sort` = CASE `permission`
    WHEN 'project:worklog:create' THEN 7 WHEN 'project:worklog:update' THEN 8 ELSE 9 END,
    `updater` = '1', `update_time` = NOW()
WHERE @project_work_item_menu_id IS NOT NULL AND `parent_id` = @legacy_project_worklog_menu_id
  AND `permission` IN ('project:worklog:create', 'project:worklog:update', 'project:worklog:delete')
  AND `deleted` = b'0';
UPDATE `system_menu`
SET `name` = '工时查询', `type` = 3, `sort` = 6, `parent_id` = @project_work_item_menu_id,
    `path` = '', `icon` = '', `component` = '', `component_name` = NULL,
    `updater` = '1', `update_time` = NOW()
WHERE @project_work_item_menu_id IS NOT NULL AND `id` = @legacy_project_worklog_menu_id;

INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT permissions.name, permissions.permission, 3, permissions.sort, @project_work_item_menu_id, '', '', '', NULL,
       0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
FROM (
  SELECT '工时查询' name, 'project:worklog:query' permission, 6 sort UNION ALL
  SELECT '工时新增', 'project:worklog:create', 7 UNION ALL
  SELECT '工时修改', 'project:worklog:update', 8 UNION ALL
  SELECT '工时删除', 'project:worklog:delete', 9
) permissions
WHERE @project_work_item_menu_id IS NOT NULL AND NOT EXISTS (
  SELECT 1 FROM `system_menu` m WHERE m.`parent_id` = @project_work_item_menu_id
    AND m.`permission` = permissions.permission AND m.`deleted` = b'0'
);
