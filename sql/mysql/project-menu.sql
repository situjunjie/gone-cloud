-- Project menu and permission bootstrap data.
-- Import this file after the `system_menu` table is available.
--
-- MenuDO fields:
--   type: 1 directory, 2 menu, 3 button
--   status: 0 enabled
--   visible / keep_alive / always_show: b'1' enabled

-- ----------------------------
-- Project directory
-- ----------------------------
INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '项目管理', '', 1, 85, 0, '/project', 'ep:folder-opened', NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM `system_menu` WHERE `parent_id` = 0 AND `path` = '/project' AND `deleted` = b'0'
);
SET @project_menu_id := (
  SELECT `id` FROM `system_menu`
  WHERE `parent_id` = 0 AND `path` = '/project' AND `deleted` = b'0'
  ORDER BY `id` DESC LIMIT 1
);

-- ----------------------------
-- Page menus
-- ----------------------------
INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '项目列表', 'project:project:query', 2, 1, @project_menu_id, 'project', 'ep:folder', 'project/project/index', 'ProjectProject', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM `system_menu` WHERE `parent_id` = @project_menu_id AND `path` = 'project' AND `deleted` = b'0'
);
SET @project_project_menu_id := (
  SELECT `id` FROM `system_menu`
  WHERE `parent_id` = @project_menu_id AND `path` = 'project' AND `deleted` = b'0'
  ORDER BY `id` DESC LIMIT 1
);

INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '工作项管理', 'project:work-item:query', 2, 2, @project_menu_id, 'work-item', 'ep:tickets', 'project/work-item/index', 'ProjectWorkItem', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM `system_menu` WHERE `parent_id` = @project_menu_id AND `path` = 'work-item' AND `deleted` = b'0'
);
SET @project_work_item_menu_id := (
  SELECT `id` FROM `system_menu`
  WHERE `parent_id` = @project_menu_id AND `path` = 'work-item' AND `deleted` = b'0'
  ORDER BY `id` DESC LIMIT 1
);

-- ----------------------------
-- Project permissions
-- ----------------------------
INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '项目查询', 'project:project:query', 3, 1, @project_project_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `parent_id` = @project_project_menu_id AND `permission` = 'project:project:query' AND `deleted` = b'0');

INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '项目新增', 'project:project:create', 3, 2, @project_project_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `parent_id` = @project_project_menu_id AND `permission` = 'project:project:create' AND `deleted` = b'0');

INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '项目修改', 'project:project:update', 3, 3, @project_project_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `parent_id` = @project_project_menu_id AND `permission` = 'project:project:update' AND `deleted` = b'0');

INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '项目归档', 'project:project:archive', 3, 4, @project_project_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `parent_id` = @project_project_menu_id AND `permission` = 'project:project:archive' AND `deleted` = b'0');

INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '项目成员管理', 'project:project:member', 3, 5, @project_project_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `parent_id` = @project_project_menu_id AND `permission` = 'project:project:member' AND `deleted` = b'0');

INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '项目角色管理', 'project:project:role', 3, 6, @project_project_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `parent_id` = @project_project_menu_id AND `permission` = 'project:project:role' AND `deleted` = b'0');

INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '全局角色模板查询', 'project:global-role:query', 3, 7, @project_project_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `parent_id` = @project_project_menu_id AND `permission` = 'project:global-role:query' AND `deleted` = b'0');

INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '全局角色模板管理', 'project:global-role:manage', 3, 8, @project_project_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `parent_id` = @project_project_menu_id AND `permission` = 'project:global-role:manage' AND `deleted` = b'0');

INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '迭代新增', 'project:sprint:create', 3, 9, @project_project_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `parent_id` = @project_project_menu_id AND `permission` = 'project:sprint:create' AND `deleted` = b'0');

-- ----------------------------
-- Work item permissions
-- ----------------------------
INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '工作项查询', 'project:work-item:query', 3, 1, @project_work_item_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `parent_id` = @project_work_item_menu_id AND `permission` = 'project:work-item:query' AND `deleted` = b'0');

INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '工作项新增', 'project:work-item:create', 3, 2, @project_work_item_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `parent_id` = @project_work_item_menu_id AND `permission` = 'project:work-item:create' AND `deleted` = b'0');

INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '工作项修改', 'project:work-item:update', 3, 3, @project_work_item_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `parent_id` = @project_work_item_menu_id AND `permission` = 'project:work-item:update' AND `deleted` = b'0');

INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '工作项删除', 'project:work-item:delete', 3, 4, @project_work_item_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `parent_id` = @project_work_item_menu_id AND `permission` = 'project:work-item:delete' AND `deleted` = b'0');

INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '全局工作项字段管理', 'project:work-item-field:global-manage', 3, 5, @project_work_item_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `parent_id` = @project_work_item_menu_id AND `permission` = 'project:work-item-field:global-manage' AND `deleted` = b'0');
