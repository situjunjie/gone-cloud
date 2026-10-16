-- ============================================================================
-- 回退流水线定义到「本机构建」（K8s 构建集群迁移前）
--
-- 复刻 PipelineDefinitionServiceImpl#rollback（:168）的语义：
--   1) 以「迁移前的历史版本」为内容，新建一个 PUBLISHED 版本（version_no 递增）
--   2) 把 dev_pipeline_definition.published_version_id 指向新版本
--   3) 记录 rollback_from_* / based_on_current_* / rollback_reason 审计字段
--
-- 设计约束：
--   * DBX 策略 allowDangerousSql=false，因此本脚本**不含任何 DDL**
--     （无 DROP / CREATE / TRUNCATE / ALTER）。备份改为「先 SELECT 存盘」。
--   * 纯 DML，只有 1 条 INSERT + 1 条 UPDATE，都带幂等守卫。
--   * rollback_reason 作为本次操作的标记，便于定位与回滚。
--
-- 与 API 版的已知差异（有意为之）：
--   - 不触发 Redis 缓存清除（@CacheEvict APPLICATION_RELEASE_CURRENT_RUN）。
--     执行后需手动删该 key 或重启 devops-server。
--   - cache_config_json 原样复制，不做 normalize。运行时
--     PipelineCacheConfigResolver.resolveVersionConfig 每次读取都会 normalize，
--     行为等价。
--   - 不改 draft 版本（与 API 一致）。draft 仍是 K8s 版。
--
-- 受影响定义（14 个，均有可回退的迁移前版本）：
--   definition_id -> target_version_id（迁移前版本）
--      1 -> 126 (v40)   5 -> 113 (v42)    6 -> 139 (v12)    8 -> 158 (v2)
--     11 -> 169 (v13)  12 -> 152 (v3)    13 -> 155 (v6)    15 -> 167 (v6)
--     16 -> 189 (v16)  17 -> 182 (v1)    18 -> 191 (v1)    21 -> 204 (v2)
--     24 -> 208 (v2)   32 -> 236 (v6)
-- ============================================================================


-- ---------------------------------------------------------------------------
-- Step 0：备份 —— 先 SELECT，把结果存到本地文件（无 DDL）
--   把下面这条的输出保存到
--   .trellis/tasks/archive/2026-10/10-16-revert-pipeline-k8s-build-cluster/
--     backup-published-pointers.tsv
--   回滚本操作时按该文件还原 published_version_id。
-- ---------------------------------------------------------------------------
SELECT d.id AS definition_id,
       d.name AS pipeline_name,
       d.published_version_id AS old_published_version_id,
       v.version_no AS old_published_version_no
FROM dev_pipeline_definition d
LEFT JOIN dev_pipeline_definition_version v ON v.id = d.published_version_id
WHERE d.id IN (1, 5, 6, 8, 11, 12, 13, 15, 16, 17, 18, 21, 24, 32)
ORDER BY d.id;
-- 期望：14 行


-- ---------------------------------------------------------------------------
-- Step 1：预演 —— 确认将要写入的 14 行内容与版本号
--   先跑这条 SELECT，核对无误后再执行 Step 2 的 INSERT。
-- ---------------------------------------------------------------------------
SELECT p.definition_id,
       d.name AS pipeline_name,
       p.target_version_id,
       t.version_no AS target_version_no,
       p.new_version_no,
       (SELECT MAX(v.version_no) FROM dev_pipeline_definition_version v
         WHERE v.definition_id = p.definition_id AND v.deleted = b'0') AS current_max_version_no,
       d.published_version_id AS current_published_version_id
FROM (
  SELECT t0.id AS target_version_id, t0.definition_id,
         (SELECT COALESCE(MAX(v2.version_no), 0) + 1
            FROM dev_pipeline_definition_version v2
           WHERE v2.definition_id = t0.definition_id
             AND v2.version_status = 1
             AND v2.deleted = b'0') AS new_version_no
  FROM dev_pipeline_definition_version t0
  WHERE t0.id IN (155, 167, 182, 158, 113, 169, 189, 191, 204, 208, 236, 152, 126, 139)
) p
JOIN dev_pipeline_definition_version t ON t.id = p.target_version_id
JOIN dev_pipeline_definition d ON d.id = p.definition_id
ORDER BY p.definition_id;
-- 期望：14 行；new_version_no 必须 > current_max_version_no


-- ---------------------------------------------------------------------------
-- Step 2：插入回退版本（内容 = 目标历史版本，状态 = PUBLISHED）
--   幂等守卫：同一 definition 已存在本次标记的版本时跳过。
-- ---------------------------------------------------------------------------
INSERT INTO dev_pipeline_definition_version
  (definition_id, version_no, version_name, version_status,
   diagram_json, spec_json, node_schema_version, cache_config_json,
   rollback_from_version_id, rollback_from_version_no,
   based_on_current_version_id, based_on_current_version_no, rollback_reason,
   published_at, published_by,
   creator, create_time, updater, update_time, deleted, tenant_id)
SELECT p.definition_id,
       p.new_version_no,
       CONCAT('v', p.new_version_no),
       1,
       t.diagram_json,
       t.spec_json,
       COALESCE(NULLIF(t.node_schema_version, ''), '1.0'),
       t.cache_config_json,
       t.id,
       t.version_no,
       d.published_version_id,
       cur.version_no,
       'K8s 构建集群下线，回退到平台本机 Docker 构建',
       NOW(), 1,
       '1', NOW(), '1', NOW(), b'0', t.tenant_id
FROM (
  SELECT t0.id AS target_version_id, t0.definition_id,
         (SELECT COALESCE(MAX(v2.version_no), 0) + 1
            FROM dev_pipeline_definition_version v2
           WHERE v2.definition_id = t0.definition_id
             AND v2.version_status = 1
             AND v2.deleted = b'0') AS new_version_no
  FROM dev_pipeline_definition_version t0
  WHERE t0.id IN (155, 167, 182, 158, 113, 169, 189, 191, 204, 208, 236, 152, 126, 139)
) p
JOIN dev_pipeline_definition_version t ON t.id = p.target_version_id
JOIN dev_pipeline_definition d ON d.id = p.definition_id
LEFT JOIN dev_pipeline_definition_version cur ON cur.id = d.published_version_id
WHERE NOT EXISTS (
        SELECT 1 FROM dev_pipeline_definition_version x
         WHERE x.definition_id = p.definition_id
           AND x.deleted = b'0'
           AND x.rollback_reason = 'K8s 构建集群下线，回退到平台本机 Docker 构建');
-- 期望：14 rows affected


-- ---------------------------------------------------------------------------
-- Step 3：切换定义指针
-- ---------------------------------------------------------------------------
UPDATE dev_pipeline_definition d
JOIN dev_pipeline_definition_version v
  ON v.definition_id = d.id
 AND v.deleted = b'0'
 AND v.rollback_reason = 'K8s 构建集群下线，回退到平台本机 Docker 构建'
SET d.published_version_id = v.id,
    d.updater              = '1',
    d.update_time          = NOW();
-- 期望：14 rows affected


-- ---------------------------------------------------------------------------
-- Step 4：验证 —— 生效版本必须不再含 K8s 构建特征
-- ---------------------------------------------------------------------------
SELECT d.id AS def_id, d.name AS pipeline_name,
       v.id AS active_version_id, v.version_no, v.version_name,
       v.spec_json COLLATE utf8mb4_bin LIKE '%step: KanikoBuild%'  AS has_kaniko,
       v.spec_json COLLATE utf8mb4_bin LIKE '%step: Checkout%'     AS has_checkout,
       v.spec_json COLLATE utf8mb4_bin LIKE '%group: kubernetes/%' AS has_k8s_group,
       v.rollback_from_version_no, v.based_on_current_version_no
FROM dev_pipeline_definition d
JOIN dev_pipeline_definition_version v ON v.id = d.published_version_id
WHERE d.id IN (1, 5, 6, 8, 11, 12, 13, 15, 16, 17, 18, 21, 24, 32)
ORDER BY d.id;
-- 期望：has_kaniko / has_checkout / has_k8s_group 全部为 0


-- ============================================================================
-- 回滚本操作（如需）：
--   1) 按 Step 0 存下的 tsv 还原指针：
--      UPDATE dev_pipeline_definition SET published_version_id = <old_id> WHERE id = <def_id>;
--   2) 删除本次新建的版本行：
--      DELETE FROM dev_pipeline_definition_version
--       WHERE rollback_reason = 'K8s 构建集群下线，回退到平台本机 Docker 构建';
-- ============================================================================
