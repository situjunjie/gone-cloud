-- Remove the obsolete WORK_ITEM work-item category from existing MySQL data.
-- This script intentionally keeps CommentTargetTypeEnum.WORK_ITEM and WorklogGroupByEnum.WORK_ITEM:
-- they identify a target and a grouping dimension, not a work-item category.
-- Project business tables use logical deletion, so this migration follows that convention.

START TRANSACTION;

-- A surviving child cannot keep a parent reference to a removed legacy-category work item.
UPDATE `proj_work_item` child
JOIN `proj_work_item` parent ON parent.`id` = child.`parent_work_item_id`
  AND parent.`tenant_id` = child.`tenant_id`
SET child.`parent_work_item_id` = NULL,
    child.`updater` = '1',
    child.`update_time` = NOW()
WHERE child.`deleted` = b'0'
  AND parent.`deleted` = b'0'
  AND parent.`category` = 'WORK_ITEM';

-- Clean work-item-owned data before hiding the obsolete work items themselves.
UPDATE `proj_work_item_field_value_option` value_option
JOIN `proj_work_item` work_item ON work_item.`id` = value_option.`work_item_id`
  AND work_item.`tenant_id` = value_option.`tenant_id`
SET value_option.`deleted` = b'1',
    value_option.`updater` = '1',
    value_option.`update_time` = NOW()
WHERE value_option.`deleted` = b'0'
  AND work_item.`deleted` = b'0'
  AND work_item.`category` = 'WORK_ITEM';

UPDATE `proj_work_item_field_value` field_value
JOIN `proj_work_item` work_item ON work_item.`id` = field_value.`work_item_id`
  AND work_item.`tenant_id` = field_value.`tenant_id`
SET field_value.`deleted` = b'1',
    field_value.`updater` = '1',
    field_value.`update_time` = NOW()
WHERE field_value.`deleted` = b'0'
  AND work_item.`deleted` = b'0'
  AND work_item.`category` = 'WORK_ITEM';

UPDATE `proj_worklog` worklog
JOIN `proj_work_item` work_item ON work_item.`id` = worklog.`work_item_id`
  AND work_item.`tenant_id` = worklog.`tenant_id`
SET worklog.`deleted` = b'1',
    worklog.`updater` = '1',
    worklog.`update_time` = NOW()
WHERE worklog.`deleted` = b'0'
  AND work_item.`deleted` = b'0'
  AND work_item.`category` = 'WORK_ITEM';

UPDATE `proj_comment_mention` mention
JOIN `proj_comment` comment ON comment.`id` = mention.`comment_id`
  AND comment.`tenant_id` = mention.`tenant_id`
JOIN `proj_work_item` work_item ON work_item.`id` = comment.`target_id`
  AND work_item.`tenant_id` = comment.`tenant_id`
SET mention.`deleted` = b'1',
    mention.`updater` = '1',
    mention.`update_time` = NOW()
WHERE mention.`deleted` = b'0'
  AND comment.`deleted` = b'0'
  AND comment.`target_type` = 'WORK_ITEM'
  AND work_item.`deleted` = b'0'
  AND work_item.`category` = 'WORK_ITEM';

UPDATE `proj_comment` comment
JOIN `proj_work_item` work_item ON work_item.`id` = comment.`target_id`
  AND work_item.`tenant_id` = comment.`tenant_id`
SET comment.`status` = 'DELETED',
    comment.`content` = NULL,
    comment.`deleted` = b'1',
    comment.`updater` = '1',
    comment.`update_time` = NOW()
WHERE comment.`deleted` = b'0'
  AND comment.`target_type` = 'WORK_ITEM'
  AND work_item.`deleted` = b'0'
  AND work_item.`category` = 'WORK_ITEM';

-- A field scoped only to the removed category must be hidden with its options and values.
UPDATE `proj_work_item_field_value_option` value_option
JOIN `proj_work_item_field` field ON field.`id` = value_option.`field_id`
  AND field.`tenant_id` = value_option.`tenant_id`
SET value_option.`deleted` = b'1',
    value_option.`updater` = '1',
    value_option.`update_time` = NOW()
WHERE value_option.`deleted` = b'0'
  AND field.`deleted` = b'0'
  AND field.`applicable_category` = 'WORK_ITEM';

UPDATE `proj_work_item_field_value` field_value
JOIN `proj_work_item_field` field ON field.`id` = field_value.`field_id`
  AND field.`tenant_id` = field_value.`tenant_id`
SET field_value.`deleted` = b'1',
    field_value.`updater` = '1',
    field_value.`update_time` = NOW()
WHERE field_value.`deleted` = b'0'
  AND field.`deleted` = b'0'
  AND field.`applicable_category` = 'WORK_ITEM';

UPDATE `proj_work_item_field_option` field_option
JOIN `proj_work_item_field` field ON field.`id` = field_option.`field_id`
  AND field.`tenant_id` = field_option.`tenant_id`
SET field_option.`deleted` = b'1',
    field_option.`updater` = '1',
    field_option.`update_time` = NOW()
WHERE field_option.`deleted` = b'0'
  AND field.`deleted` = b'0'
  AND field.`applicable_category` = 'WORK_ITEM';

UPDATE `proj_work_item_field` field
SET field.`deleted` = b'1',
    field.`updater` = '1',
    field.`update_time` = NOW()
WHERE field.`deleted` = b'0'
  AND field.`applicable_category` = 'WORK_ITEM';

UPDATE `proj_work_item`
SET `deleted` = b'1',
    `updater` = '1',
    `update_time` = NOW()
WHERE `deleted` = b'0'
  AND `category` = 'WORK_ITEM';

-- Remove saved views that contain the obsolete category in a supported category filter.
-- MySQL 8 JSON_TABLE is used so unrelated text filters containing WORK_ITEM are retained.
UPDATE `proj_work_item_query_view`
SET `deleted` = b'1',
    `updater` = '1',
    `update_time` = NOW()
WHERE `deleted` = b'0'
  AND JSON_VALID(`condition_json`)
  AND (
      JSON_UNQUOTE(JSON_EXTRACT(`condition_json`, '$.category')) = 'WORK_ITEM'
      OR EXISTS (
          SELECT 1
          FROM JSON_TABLE(`condition_json`, '$.conditions[*]'
              COLUMNS (
                  field_name VARCHAR(64) PATH '$.field',
                  value_text VARCHAR(1024) PATH '$.value',
                  values_text VARCHAR(4096) PATH '$.values'
              )
          ) condition_item
          WHERE condition_item.field_name = 'category'
            AND (FIND_IN_SET('WORK_ITEM', REPLACE(condition_item.value_text, ' ', '')) > 0
                 OR JSON_CONTAINS(condition_item.values_text, JSON_QUOTE('WORK_ITEM')))
      )
      OR EXISTS (
          SELECT 1
          FROM JSON_TABLE(`condition_json`, '$.conditionGroups[*].conditions[*]'
              COLUMNS (
                  field_name VARCHAR(64) PATH '$.field',
                  value_text VARCHAR(1024) PATH '$.value',
                  values_text VARCHAR(4096) PATH '$.values'
              )
          ) condition_item
          WHERE condition_item.field_name = 'category'
            AND (FIND_IN_SET('WORK_ITEM', REPLACE(condition_item.value_text, ' ', '')) > 0
                 OR JSON_CONTAINS(condition_item.values_text, JSON_QUOTE('WORK_ITEM')))
      )
  );

DELETE FROM `system_dict_data`
WHERE `dict_type` = 'project_work_item_category'
  AND `value` = 'WORK_ITEM';

UPDATE `system_dict_data`
SET `sort` = CASE `value`
        WHEN 'TASK' THEN 20
        WHEN 'BUG' THEN 30
    END,
    `updater` = '1',
    `update_time` = NOW()
WHERE `deleted` = b'0'
  AND `dict_type` = 'project_work_item_category'
  AND `value` IN ('TASK', 'BUG');

UPDATE `system_dict_type`
SET `remark` = '工作项表 category：REQUIREMENT TASK BUG',
    `updater` = '1',
    `update_time` = NOW()
WHERE `deleted` = b'0'
  AND `type` = 'project_work_item_category';

COMMIT;
