-- Project work-item materialized worklog aggregates (2026-07-28).
-- Apply after project-worklog-incremental-20260728.sql.
-- Safe to rerun: columns are conditional, existing counters are widened, and active worklogs rebuild them.

ALTER TABLE `proj_work_item` ADD COLUMN IF NOT EXISTS `estimated_hours` decimal(20,2) NOT NULL DEFAULT 0.00 COMMENT '预估工时物化合计';
ALTER TABLE `proj_work_item` ADD COLUMN IF NOT EXISTS `actual_hours` decimal(20,2) NOT NULL DEFAULT 0.00 COMMENT '实际工时物化合计';
ALTER TABLE `proj_work_item`
    MODIFY COLUMN `estimated_hours` decimal(20,2) NOT NULL DEFAULT 0.00 COMMENT '预估工时物化合计',
    MODIFY COLUMN `actual_hours` decimal(20,2) NOT NULL DEFAULT 0.00 COMMENT '实际工时物化合计';

UPDATE `proj_work_item` wi
LEFT JOIN (
    SELECT tenant_id, project_id, work_item_id,
           COALESCE(SUM(CASE WHEN type = 'ESTIMATED' THEN hours ELSE 0 END), 0) AS estimated_hours,
           COALESCE(SUM(CASE WHEN type = 'ACTUAL' THEN hours ELSE 0 END), 0) AS actual_hours
    FROM `proj_worklog`
    WHERE deleted = b'0'
    GROUP BY tenant_id, project_id, work_item_id
) totals ON totals.tenant_id = wi.tenant_id AND totals.project_id = wi.project_id AND totals.work_item_id = wi.id
SET wi.estimated_hours = COALESCE(totals.estimated_hours, 0),
    wi.actual_hours = COALESCE(totals.actual_hours, 0)
WHERE wi.deleted = b'0';
