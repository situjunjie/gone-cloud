-- Project comment incremental migration (2026-07-22).
-- Apply after project-rbac-incremental-20260721.sql.

CREATE TABLE IF NOT EXISTS `proj_comment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '评论编号',
  `project_id` bigint NOT NULL COMMENT '归属项目编号，由目标解析得到',
  `target_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '目标类型：WORK_ITEM',
  `target_id` bigint NOT NULL COMMENT '目标编号',
  `reply_to_comment_id` bigint DEFAULT NULL COMMENT '回复引用的评论编号',
  `author_user_id` bigint NOT NULL COMMENT '作者用户编号',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT 'Markdown 正文，删除后清空',
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态：NORMAL、DELETED',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除，仅用于数据治理',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_target_timeline` (`tenant_id`, `project_id`, `target_type`, `target_id`, `create_time`, `id`) USING BTREE,
  KEY `idx_reply_to` (`tenant_id`, `reply_to_comment_id`) USING BTREE
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目评论表';

CREATE TABLE IF NOT EXISTS `proj_comment_mention` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '评论 @ 关系编号',
  `comment_id` bigint NOT NULL COMMENT '评论编号',
  `mentioned_user_id` bigint NOT NULL COMMENT '被 @ 的用户编号',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_tenant_comment_user` (`tenant_id`, `comment_id`, `mentioned_user_id`) USING BTREE,
  KEY `idx_tenant_mentioned_user` (`tenant_id`, `mentioned_user_id`, `comment_id`) USING BTREE
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目评论 @ 用户关系表';

-- Restore logically deleted grants before inserting missing grants because the unique key omits deleted.
UPDATE `proj_project_role_permission` permission
JOIN `proj_project_role` role ON role.`id` = permission.`role_id`
SET permission.`deleted` = b'0', permission.`update_time` = NOW()
WHERE role.`builtin_type` = 'PARTICIPANT'
  AND role.`deleted` = b'0'
  AND permission.`permission_code` IN ('COMMENT_QUERY', 'COMMENT_CREATE', 'COMMENT_UPDATE', 'COMMENT_DELETE')
  AND permission.`deleted` = b'1';

INSERT INTO `proj_project_role_permission`
(`role_id`, `permission_code`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT role.`id`, code.`permission_code`, '1', NOW(), '1', NOW(), b'0', role.`tenant_id`
FROM `proj_project_role` role
JOIN (
  SELECT 'COMMENT_QUERY' AS `permission_code`
  UNION ALL SELECT 'COMMENT_CREATE'
  UNION ALL SELECT 'COMMENT_UPDATE'
  UNION ALL SELECT 'COMMENT_DELETE'
) code
WHERE role.`builtin_type` = 'PARTICIPANT'
  AND role.`deleted` = b'0'
  AND NOT EXISTS (
    SELECT 1 FROM `proj_project_role_permission` permission
    WHERE permission.`tenant_id` = role.`tenant_id`
      AND permission.`role_id` = role.`id`
      AND permission.`permission_code` = code.`permission_code`
  );

-- Restore or create the tenant-shared Admin notification template.
UPDATE `system_notify_template`
SET `deleted` = b'0', `status` = 0, `update_time` = NOW()
WHERE `code` = 'PROJECT_COMMENT_MENTION' AND `deleted` = b'1';

INSERT INTO `system_notify_template`
(`name`, `code`, `nickname`, `content`, `type`, `params`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '项目评论提及提醒', 'PROJECT_COMMENT_MENTION', '项目协作',
       '{authorName} 在工作项 {workItemNo}（ID: {targetId}）中提到了你：{contentPreview}',
       2, '["authorName","workItemNo","targetId","contentPreview"]', 0,
       'Project 评论模块 @ 提醒', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (
  SELECT 1 FROM `system_notify_template`
  WHERE `code` = 'PROJECT_COMMENT_MENTION'
);

-- Role permission caches use a 30-minute TTL. Evict project:role_permissions entries
-- during rollout or wait for expiry before verifying existing participant roles.
