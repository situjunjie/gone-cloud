-- DevOps menu and permission bootstrap data.
-- Import this file after the `system_menu` table is available.
--
-- MenuDO fields:
--   type: 1 directory, 2 menu, 3 button
--   status: 0 enabled
--   visible / keep_alive / always_show: b'1' enabled

-- ----------------------------
-- DevOps directory
-- ----------------------------
INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 'DevOps', '', 1, 80, 0, '/devops', 'ep:connection', NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM `system_menu` WHERE `parent_id` = 0 AND `path` = '/devops' AND `deleted` = b'0'
);
SET @devops_menu_id := (
  SELECT `id` FROM `system_menu`
  WHERE `parent_id` = 0 AND `path` = '/devops' AND `deleted` = b'0'
  ORDER BY `id` DESC LIMIT 1
);

-- ----------------------------
-- Page menus
-- ----------------------------
INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 'DevOps 应用', 'devops:application:query', 2, 1, @devops_menu_id, 'application', 'ep:connection', 'devops/application/index', 'DevopsApplication', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM `system_menu` WHERE `parent_id` = @devops_menu_id AND `path` = 'application' AND `deleted` = b'0'
);
SET @devops_application_menu_id := (
  SELECT `id` FROM `system_menu`
  WHERE `parent_id` = @devops_menu_id AND `path` = 'application' AND `deleted` = b'0'
  ORDER BY `id` DESC LIMIT 1
);

INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 'DevOps 环境', 'devops:environment:query', 2, 2, @devops_menu_id, 'environment', 'ep:monitor', 'devops/environment/index', 'DevopsEnvironment', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM `system_menu` WHERE `parent_id` = @devops_menu_id AND `path` = 'environment' AND `deleted` = b'0'
);
SET @devops_environment_menu_id := (
  SELECT `id` FROM `system_menu`
  WHERE `parent_id` = @devops_menu_id AND `path` = 'environment' AND `deleted` = b'0'
  ORDER BY `id` DESC LIMIT 1
);

INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '环境大盘', 'devops:environment:query', 2, 99, @devops_environment_menu_id, '/devops/environment/dashboard', 'ep:data-analysis', 'devops/environment/dashboard', 'DevopsEnvironmentDashboard', 0, b'0', b'1', b'0', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM `system_menu` WHERE `parent_id` = @devops_environment_menu_id AND `path` = '/devops/environment/dashboard' AND `deleted` = b'0'
);

INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 'DevOps 变更', 'devops:change:query', 2, 3, @devops_menu_id, 'change', 'ep:promotion', 'devops/change/index', 'DevopsChange', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM `system_menu` WHERE `parent_id` = @devops_menu_id AND `path` = 'change' AND `deleted` = b'0'
);
SET @devops_change_menu_id := (
  SELECT `id` FROM `system_menu`
  WHERE `parent_id` = @devops_menu_id AND `path` = 'change' AND `deleted` = b'0'
  ORDER BY `id` DESC LIMIT 1
);

-- Re-resolve the parent/application menu so the pipeline designer subsection can be run by itself.
INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 'DevOps', '', 1, 80, 0, '/devops', 'ep:connection', NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM `system_menu` WHERE `parent_id` = 0 AND `path` = '/devops' AND `deleted` = b'0'
);
SET @devops_menu_id := (
  SELECT `id` FROM `system_menu`
  WHERE `parent_id` = 0 AND `path` = '/devops' AND `deleted` = b'0'
  ORDER BY `id` DESC LIMIT 1
);

INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 'DevOps 应用', 'devops:application:query', 2, 1, @devops_menu_id, 'application', 'ep:connection', 'devops/application/index', 'DevopsApplication', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM `system_menu` WHERE `parent_id` = @devops_menu_id AND `path` = 'application' AND `deleted` = b'0'
);
SET @devops_application_menu_id := (
  SELECT `id` FROM `system_menu`
  WHERE `parent_id` = @devops_menu_id AND `path` = 'application' AND `deleted` = b'0'
  ORDER BY `id` DESC LIMIT 1
);
SET @devops_pipeline_designer_menu_id := (
  SELECT `id` FROM `system_menu`
  WHERE `parent_id` = @devops_application_menu_id AND `path` = '/devops/pipeline/designer' AND `deleted` = b'0'
  ORDER BY `id` DESC LIMIT 1
);
UPDATE `system_menu`
SET `name` = '流水线配置',
    `permission` = 'devops:pipeline:query',
    `type` = 2,
    `sort` = 99,
    `parent_id` = @devops_application_menu_id,
    `path` = '/devops/pipeline/designer',
    `icon` = 'ep:share',
    `component` = 'devops/pipeline/designer',
    `component_name` = 'DevopsPipelineDesigner',
    `status` = 0,
    `visible` = b'0',
    `keep_alive` = b'1',
    `always_show` = b'0',
    `updater` = '1',
    `update_time` = NOW()
WHERE @devops_pipeline_designer_menu_id IS NULL
  AND `parent_id` = @devops_menu_id
  AND `path` = 'pipeline'
  AND `deleted` = b'0';
SET @devops_pipeline_designer_menu_id := (
  SELECT `id` FROM `system_menu`
  WHERE `parent_id` = @devops_application_menu_id AND `path` = '/devops/pipeline/designer' AND `deleted` = b'0'
  ORDER BY `id` DESC LIMIT 1
);
INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '流水线配置', 'devops:pipeline:query', 2, 99, @devops_application_menu_id, '/devops/pipeline/designer', 'ep:share', 'devops/pipeline/designer', 'DevopsPipelineDesigner', 0, b'0', b'1', b'0', '1', NOW(), '1', NOW(), b'0'
WHERE @devops_pipeline_designer_menu_id IS NULL;
SET @devops_pipeline_designer_menu_id := (
  SELECT `id` FROM `system_menu`
  WHERE `parent_id` = @devops_application_menu_id AND `path` = '/devops/pipeline/designer' AND `deleted` = b'0'
  ORDER BY `id` DESC LIMIT 1
);
UPDATE `system_menu`
SET `name` = '流水线配置',
    `permission` = 'devops:pipeline:query',
    `type` = 2,
    `sort` = 99,
    `parent_id` = @devops_application_menu_id,
    `path` = '/devops/pipeline/designer',
    `icon` = 'ep:share',
    `component` = 'devops/pipeline/designer',
    `component_name` = 'DevopsPipelineDesigner',
    `status` = 0,
    `visible` = b'0',
    `keep_alive` = b'1',
    `always_show` = b'0',
    `updater` = '1',
    `update_time` = NOW()
WHERE `id` = @devops_pipeline_designer_menu_id
  AND `deleted` = b'0';
UPDATE `system_menu`
SET `visible` = b'0',
    `status` = 1,
    `updater` = '1',
    `update_time` = NOW()
WHERE `parent_id` = @devops_menu_id
  AND `path` = 'pipeline'
  AND `deleted` = b'0';

-- ----------------------------
-- DevOps application permissions
-- ----------------------------
INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '应用查询', 'devops:application:query', 3, 1, @devops_application_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `parent_id` = @devops_application_menu_id AND `permission` = 'devops:application:query' AND `deleted` = b'0');

INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '应用新增', 'devops:application:create', 3, 2, @devops_application_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `parent_id` = @devops_application_menu_id AND `permission` = 'devops:application:create' AND `deleted` = b'0');

INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '应用修改', 'devops:application:update', 3, 3, @devops_application_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `parent_id` = @devops_application_menu_id AND `permission` = 'devops:application:update' AND `deleted` = b'0');

INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '应用删除', 'devops:application:delete', 3, 4, @devops_application_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `parent_id` = @devops_application_menu_id AND `permission` = 'devops:application:delete' AND `deleted` = b'0');

INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '应用环境配置', 'devops:application:update-envs', 3, 5, @devops_application_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `parent_id` = @devops_application_menu_id AND `permission` = 'devops:application:update-envs' AND `deleted` = b'0');

INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '应用发布提交', 'devops:application:release-submit', 3, 6, @devops_application_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `parent_id` = @devops_application_menu_id AND `permission` = 'devops:application:release-submit' AND `deleted` = b'0');

-- ----------------------------
-- DevOps environment permissions
-- ----------------------------
INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '环境查询', 'devops:environment:query', 3, 1, @devops_environment_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `parent_id` = @devops_environment_menu_id AND `permission` = 'devops:environment:query' AND `deleted` = b'0');

INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '环境新增', 'devops:environment:create', 3, 2, @devops_environment_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `parent_id` = @devops_environment_menu_id AND `permission` = 'devops:environment:create' AND `deleted` = b'0');

INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '环境修改', 'devops:environment:update', 3, 3, @devops_environment_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `parent_id` = @devops_environment_menu_id AND `permission` = 'devops:environment:update' AND `deleted` = b'0');

INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '环境删除', 'devops:environment:delete', 3, 4, @devops_environment_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `parent_id` = @devops_environment_menu_id AND `permission` = 'devops:environment:delete' AND `deleted` = b'0');

-- ----------------------------
-- DevOps change permissions
-- ----------------------------
INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '变更查询', 'devops:change:query', 3, 1, @devops_change_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `parent_id` = @devops_change_menu_id AND `permission` = 'devops:change:query' AND `deleted` = b'0');

INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '变更新增', 'devops:change:create', 3, 2, @devops_change_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `parent_id` = @devops_change_menu_id AND `permission` = 'devops:change:create' AND `deleted` = b'0');

INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '变更修改', 'devops:change:update', 3, 3, @devops_change_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `parent_id` = @devops_change_menu_id AND `permission` = 'devops:change:update' AND `deleted` = b'0');

INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '变更删除', 'devops:change:delete', 3, 4, @devops_change_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `parent_id` = @devops_change_menu_id AND `permission` = 'devops:change:delete' AND `deleted` = b'0');

INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '变更发布', 'devops:change:release', 3, 5, @devops_change_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `parent_id` = @devops_change_menu_id AND `permission` = 'devops:change:release' AND `deleted` = b'0');

INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '变更废弃', 'devops:change:discard', 3, 6, @devops_change_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `parent_id` = @devops_change_menu_id AND `permission` = 'devops:change:discard' AND `deleted` = b'0');

INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '挂载环境', 'devops:change:mount-env', 3, 7, @devops_change_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `parent_id` = @devops_change_menu_id AND `permission` = 'devops:change:mount-env' AND `deleted` = b'0');

INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '移除环境', 'devops:change:unmount-env', 3, 8, @devops_change_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `parent_id` = @devops_change_menu_id AND `permission` = 'devops:change:unmount-env' AND `deleted` = b'0');

-- ----------------------------
-- DevOps pipeline permissions
-- ----------------------------
-- Re-resolve the pipeline designer menu so the permission subsection can be run by itself.
INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 'DevOps', '', 1, 80, 0, '/devops', 'ep:connection', NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM `system_menu` WHERE `parent_id` = 0 AND `path` = '/devops' AND `deleted` = b'0'
);
SET @devops_menu_id := (
  SELECT `id` FROM `system_menu`
  WHERE `parent_id` = 0 AND `path` = '/devops' AND `deleted` = b'0'
  ORDER BY `id` DESC LIMIT 1
);
INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 'DevOps 应用', 'devops:application:query', 2, 1, @devops_menu_id, 'application', 'ep:connection', 'devops/application/index', 'DevopsApplication', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM `system_menu` WHERE `parent_id` = @devops_menu_id AND `path` = 'application' AND `deleted` = b'0'
);
SET @devops_application_menu_id := (
  SELECT `id` FROM `system_menu`
  WHERE `parent_id` = @devops_menu_id AND `path` = 'application' AND `deleted` = b'0'
  ORDER BY `id` DESC LIMIT 1
);
INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '流水线配置', 'devops:pipeline:query', 2, 99, @devops_application_menu_id, '/devops/pipeline/designer', 'ep:share', 'devops/pipeline/designer', 'DevopsPipelineDesigner', 0, b'0', b'1', b'0', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM `system_menu` WHERE `parent_id` = @devops_application_menu_id AND `path` = '/devops/pipeline/designer' AND `deleted` = b'0'
);
SET @devops_pipeline_designer_menu_id := (
  SELECT `id` FROM `system_menu`
  WHERE `parent_id` = @devops_application_menu_id AND `path` = '/devops/pipeline/designer' AND `deleted` = b'0'
  ORDER BY `id` DESC LIMIT 1
);

INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '流水线查询', 'devops:pipeline:query', 3, 1, @devops_pipeline_designer_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `parent_id` = @devops_pipeline_designer_menu_id AND `permission` = 'devops:pipeline:query' AND `deleted` = b'0');

INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '流水线新增', 'devops:pipeline:create', 3, 2, @devops_pipeline_designer_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `parent_id` = @devops_pipeline_designer_menu_id AND `permission` = 'devops:pipeline:create' AND `deleted` = b'0');

INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '流水线保存草稿', 'devops:pipeline:update', 3, 3, @devops_pipeline_designer_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `parent_id` = @devops_pipeline_designer_menu_id AND `permission` = 'devops:pipeline:update' AND `deleted` = b'0');

INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '流水线删除', 'devops:pipeline:delete', 3, 4, @devops_pipeline_designer_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `parent_id` = @devops_pipeline_designer_menu_id AND `permission` = 'devops:pipeline:delete' AND `deleted` = b'0');

INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '流水线发布', 'devops:pipeline:publish', 3, 5, @devops_pipeline_designer_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `parent_id` = @devops_pipeline_designer_menu_id AND `permission` = 'devops:pipeline:publish' AND `deleted` = b'0');

UPDATE `system_menu`
SET `name` = CASE `permission`
        WHEN 'devops:pipeline:query' THEN '流水线查询'
        WHEN 'devops:pipeline:create' THEN '流水线新增'
        WHEN 'devops:pipeline:update' THEN '流水线保存草稿'
        WHEN 'devops:pipeline:delete' THEN '流水线删除'
        WHEN 'devops:pipeline:publish' THEN '流水线发布'
      END,
    `sort` = CASE `permission`
        WHEN 'devops:pipeline:query' THEN 1
        WHEN 'devops:pipeline:create' THEN 2
        WHEN 'devops:pipeline:update' THEN 3
        WHEN 'devops:pipeline:delete' THEN 4
        WHEN 'devops:pipeline:publish' THEN 5
      END,
    `type` = 3,
    `path` = '',
    `icon` = '',
    `component` = '',
    `component_name` = NULL,
    `status` = 0,
    `visible` = b'1',
    `keep_alive` = b'1',
    `always_show` = b'1',
    `updater` = '1',
    `update_time` = NOW()
WHERE `parent_id` = @devops_pipeline_designer_menu_id
  AND `permission` IN (
    'devops:pipeline:query',
    'devops:pipeline:create',
    'devops:pipeline:update',
    'devops:pipeline:delete',
    'devops:pipeline:publish'
  )
  AND `deleted` = b'0';
