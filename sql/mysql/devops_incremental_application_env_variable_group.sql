-- ============================================================
-- DevOps 应用环境 / 变量组关联增量迁移
-- 适用旧结构：
--   dev_variable_group.application_env_id 存在
--   dev_application_env.name、variable_group_id 不存在
--
-- 执行前请先备份数据库。
-- ============================================================

START TRANSACTION;

-- 1. 应用环境增加自定义名称和变量组关联字段
ALTER TABLE `dev_application_env`
    ADD COLUMN `name` varchar(128) DEFAULT NULL
        COMMENT '应用自定义环境名称'
        AFTER `env_id`,
    ADD COLUMN `variable_group_id` bigint DEFAULT NULL
        COMMENT '关联变量组编号'
        AFTER `name`;

-- 2. 使用基础环境名称初始化应用自定义名称
UPDATE `dev_application_env` ae
INNER JOIN `dev_environment` e ON e.id = ae.env_id
SET ae.name = e.env_name
WHERE ae.name IS NULL OR ae.name = '';

-- 3. 将旧的环境级变量组关联回填到应用环境
--    同时把环境级变量组的 app_id 回填为应用编号
UPDATE `dev_variable_group` vg
INNER JOIN `dev_application_env` ae
        ON ae.id = vg.application_env_id
SET vg.app_id = ae.app_id,
    ae.variable_group_id = vg.id
WHERE vg.scope = 'APPLICATION_ENV'
  AND vg.application_env_id IS NOT NULL
  AND vg.application_env_id <> 0;

-- 4. 检查一个应用环境对应多个旧环境级变量组的情况
--    若返回记录，请人工处理后再继续执行第 5 步
SELECT
    vg.tenant_id,
    vg.application_env_id,
    COUNT(*) AS variable_group_count,
    GROUP_CONCAT(vg.id ORDER BY vg.id) AS variable_group_ids
FROM `dev_variable_group` vg
WHERE vg.scope = 'APPLICATION_ENV'
  AND vg.application_env_id IS NOT NULL
  AND vg.application_env_id <> 0
  AND vg.deleted = b'0'
GROUP BY vg.tenant_id, vg.application_env_id
HAVING COUNT(*) > 1;

-- 5. 删除变量组旧索引
ALTER TABLE `dev_variable_group`
    DROP INDEX `uk_tenant_scope_name_key`,
    DROP INDEX `idx_tenant_app_env`;

-- 6. 新唯一约束不再包含 application_env_id
ALTER TABLE `dev_variable_group`
    ADD UNIQUE KEY `uk_tenant_scope_name_key`
        (`tenant_id`, `scope`, `app_id`, `name`);

-- 7. 删除变量组旧的应用环境关联列
ALTER TABLE `dev_variable_group`
    DROP COLUMN `application_env_id`;

-- 8. 为新的应用环境变量组关联增加索引
ALTER TABLE `dev_application_env`
    ADD KEY `idx_tenant_variable_group_id`
        (`tenant_id`, `variable_group_id`);

COMMIT;
