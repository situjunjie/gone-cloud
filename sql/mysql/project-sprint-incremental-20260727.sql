-- Project sprint create API incremental migration (2026-07-27)
-- Adds sprint form fields and the system-level create permission.

SET @project_schema_name := DATABASE();

SET @project_add_sprint_capacity_sql := IF(
  EXISTS (
    SELECT 1 FROM `information_schema`.`tables`
    WHERE `table_schema` = @project_schema_name
      AND `table_name` = 'proj_sprint'
  ) AND NOT EXISTS (
    SELECT 1 FROM `information_schema`.`columns`
    WHERE `table_schema` = @project_schema_name
      AND `table_name` = 'proj_sprint'
      AND `column_name` = 'capacity_hours'
  ),
  'ALTER TABLE `proj_sprint` ADD COLUMN `capacity_hours` decimal(10,1) DEFAULT NULL COMMENT ''工时容量（小时）'' AFTER `actual_end_time`',
  'SELECT 1'
);
PREPARE project_add_sprint_capacity_stmt FROM @project_add_sprint_capacity_sql;
EXECUTE project_add_sprint_capacity_stmt;
DEALLOCATE PREPARE project_add_sprint_capacity_stmt;

SET @project_add_sprint_description_sql := IF(
  EXISTS (
    SELECT 1 FROM `information_schema`.`tables`
    WHERE `table_schema` = @project_schema_name
      AND `table_name` = 'proj_sprint'
  ) AND NOT EXISTS (
    SELECT 1 FROM `information_schema`.`columns`
    WHERE `table_schema` = @project_schema_name
      AND `table_name` = 'proj_sprint'
      AND `column_name` = 'description'
  ),
  'ALTER TABLE `proj_sprint` ADD COLUMN `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''迭代描述'' AFTER `capacity_hours`',
  'SELECT 1'
);
PREPARE project_add_sprint_description_stmt FROM @project_add_sprint_description_sql;
EXECUTE project_add_sprint_description_stmt;
DEALLOCATE PREPARE project_add_sprint_description_stmt;

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
SELECT '迭代新增', 'project:sprint:create', 3, 9, @project_project_menu_id, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE @project_project_menu_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `system_menu`
    WHERE `parent_id` = @project_project_menu_id
      AND `permission` = 'project:sprint:create'
      AND `deleted` = b'0'
  );

SELECT `column_name`, `column_type`, `is_nullable`
FROM `information_schema`.`columns`
WHERE `table_schema` = DATABASE()
  AND `table_name` = 'proj_sprint'
  AND `column_name` IN ('capacity_hours', 'description')
ORDER BY `ordinal_position`;

SELECT `id`, `name`, `permission`, `parent_id`
FROM `system_menu`
WHERE `permission` = 'project:sprint:create'
  AND `deleted` = b'0';
