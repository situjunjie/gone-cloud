-- ============================================================
-- DevOps 移除 APPLICATION_ENV 变量组作用域
--
-- 目标：变量组仅保留 GLOBAL、APPLICATION 两种作用域。应用环境绑定
--       某个 APPLICATION 变量组，并要求变量组 app_id 与应用环境 app_id 一致。
--
-- 执行前请先备份数据库。脚本发现无法安全迁移的数据时会在更新前终止。
-- ============================================================

DELIMITER //

DROP PROCEDURE IF EXISTS migrate_devops_application_env_variable_group_scope //

CREATE PROCEDURE migrate_devops_application_env_variable_group_scope()
BEGIN
    -- 同一旧环境变量组不能被不同应用的环境复用，否则无法确定迁移后的 app_id。
    IF EXISTS (
        SELECT 1
        FROM dev_variable_group vg
        INNER JOIN dev_application_env ae ON ae.variable_group_id = vg.id
        WHERE vg.scope = 'APPLICATION_ENV'
          AND vg.deleted = b'0'
          AND ae.deleted = b'0'
        GROUP BY vg.id
        HAVING COUNT(DISTINCT ae.app_id) > 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '存在被不同应用环境复用的 APPLICATION_ENV 变量组，无法自动迁移';
    END IF;

    -- 迁移到 APPLICATION 后不能与同应用下已有同名变量组冲突。
    IF EXISTS (
        SELECT 1
        FROM dev_variable_group legacy
        INNER JOIN dev_application_env ae ON ae.variable_group_id = legacy.id
        INNER JOIN dev_variable_group current
                ON current.tenant_id = legacy.tenant_id
               AND current.scope = 'APPLICATION'
               AND current.app_id = ae.app_id
               AND current.name = legacy.name
               AND current.deleted = b'0'
        WHERE legacy.scope = 'APPLICATION_ENV'
          AND legacy.deleted = b'0'
          AND ae.deleted = b'0'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'APPLICATION_ENV 变量组与目标应用变量组名称冲突，无法自动迁移';
    END IF;

    -- 无法从环境关系或现有 app_id 推断所属应用的旧变量组需要人工处理。
    IF EXISTS (
        SELECT 1
        FROM dev_variable_group vg
        WHERE vg.scope = 'APPLICATION_ENV'
          AND vg.deleted = b'0'
          AND (vg.app_id IS NULL OR vg.app_id = 0)
          AND NOT EXISTS (
              SELECT 1
              FROM dev_application_env ae
              WHERE ae.variable_group_id = vg.id
                AND ae.deleted = b'0'
          )
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '存在无法确定应用归属的 APPLICATION_ENV 变量组，无法自动迁移';
    END IF;

    START TRANSACTION;

    -- 已关联环境的旧变量组以应用环境所属应用为准，迁移作用域和 app_id。
    UPDATE dev_variable_group vg
    INNER JOIN dev_application_env ae
            ON ae.variable_group_id = vg.id
           AND ae.deleted = b'0'
    SET vg.scope = 'APPLICATION',
        vg.app_id = ae.app_id
    WHERE vg.scope = 'APPLICATION_ENV'
      AND vg.deleted = b'0';

    -- 未关联环境、但已有有效 app_id 的旧变量组同样转为应用变量组。
    UPDATE dev_variable_group
    SET scope = 'APPLICATION'
    WHERE scope = 'APPLICATION_ENV'
      AND deleted = b'0'
      AND app_id IS NOT NULL
      AND app_id <> 0;

    COMMIT;
END //

CALL migrate_devops_application_env_variable_group_scope() //
DROP PROCEDURE migrate_devops_application_env_variable_group_scope //

DELIMITER ;
