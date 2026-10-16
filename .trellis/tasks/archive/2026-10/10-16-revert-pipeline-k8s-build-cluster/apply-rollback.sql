START TRANSACTION;

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
       t.diagram_json, t.spec_json,
       COALESCE(NULLIF(t.node_schema_version,''), '1.0'),
       t.cache_config_json,
       t.id, t.version_no,
       d.published_version_id, cur.version_no,
       'K8s 构建集群下线，回退到平台本机 Docker 构建',
       NOW(), 1, '1', NOW(), '1', NOW(), b'0', t.tenant_id
FROM (
  SELECT t0.id AS target_version_id, t0.definition_id,
         (SELECT COALESCE(MAX(v2.version_no),0)+1
            FROM dev_pipeline_definition_version v2
           WHERE v2.definition_id = t0.definition_id
             AND v2.version_status = 1 AND v2.deleted = b'0') AS new_version_no
  FROM dev_pipeline_definition_version t0
  WHERE t0.id IN (155,167,182,158,113,169,189,191,204,208,236,152,126,139)
) p
JOIN dev_pipeline_definition_version t ON t.id = p.target_version_id
JOIN dev_pipeline_definition d ON d.id = p.definition_id
LEFT JOIN dev_pipeline_definition_version cur ON cur.id = d.published_version_id
WHERE NOT EXISTS (
        SELECT 1 FROM dev_pipeline_definition_version x
         WHERE x.definition_id = p.definition_id AND x.deleted = b'0'
           AND x.rollback_reason = 'K8s 构建集群下线，回退到平台本机 Docker 构建');
SELECT ROW_COUNT() AS inserted_rows;

UPDATE dev_pipeline_definition d
JOIN dev_pipeline_definition_version v
  ON v.definition_id = d.id AND v.deleted = b'0'
 AND v.rollback_reason = 'K8s 构建集群下线，回退到平台本机 Docker 构建'
SET d.published_version_id = v.id, d.updater = '1', d.update_time = NOW();
SELECT ROW_COUNT() AS updated_definitions;

COMMIT;
