-- Project schema draft
-- 当前已沉淀：项目基础资料、角色权限、项目成员、项目迭代、工作项、评论、工作项查询视图

DROP TABLE IF EXISTS `proj_project`;
CREATE TABLE `proj_project` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '项目编号',
  `cover_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '项目封面地址',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '项目名称',
  `visibility` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '公开性，参见 project_visibility',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '项目编号展示值',
  `description` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '项目描述',
  `announcement` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '项目公告',
  `owner_user_id` bigint NOT NULL COMMENT '负责人用户编号',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态，参见 project_status',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_tenant_code` (`tenant_id`, `code`) USING BTREE,
  KEY `idx_tenant_visibility` (`tenant_id`, `visibility`) USING BTREE,
  KEY `idx_tenant_status` (`tenant_id`, `status`) USING BTREE,
  KEY `idx_tenant_owner_user_id` (`tenant_id`, `owner_user_id`) USING BTREE
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目表';

DROP TABLE IF EXISTS `proj_project_role`;
CREATE TABLE `proj_project_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '项目角色编号',
  `scope` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色范围：GLOBAL、PROJECT',
  `project_id` bigint NOT NULL DEFAULT 0 COMMENT '项目编号，全局模板固定为 0',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色编码，创建后不可修改',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色名称',
  `builtin_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NONE' COMMENT '内置类型：ADMIN、PARTICIPANT、NONE',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
  `remark` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_tenant_scope_project_code` (`tenant_id`, `scope`, `project_id`, `code`) USING BTREE,
  KEY `idx_tenant_project_builtin` (`tenant_id`, `project_id`, `builtin_type`) USING BTREE
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目角色及租户角色模板表';

DROP TABLE IF EXISTS `proj_project_role_permission`;
CREATE TABLE `proj_project_role_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '角色权限编号',
  `role_id` bigint NOT NULL COMMENT '角色编号',
  `permission_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '固定项目权限编码',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_tenant_role_permission` (`tenant_id`, `role_id`, `permission_code`) USING BTREE,
  KEY `idx_tenant_permission_code` (`tenant_id`, `permission_code`) USING BTREE
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目角色权限表';

DROP TABLE IF EXISTS `proj_project_member`;
CREATE TABLE `proj_project_member` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '项目成员编号',
  `project_id` bigint NOT NULL COMMENT '项目编号',
  `user_id` bigint NOT NULL COMMENT '用户编号',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_tenant_project_user` (`tenant_id`, `project_id`, `user_id`) USING BTREE,
  KEY `idx_tenant_user_id` (`tenant_id`, `user_id`) USING BTREE
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目成员表';

DROP TABLE IF EXISTS `proj_project_member_role`;
CREATE TABLE `proj_project_member_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '成员角色关系编号',
  `project_id` bigint NOT NULL COMMENT '项目编号',
  `member_id` bigint NOT NULL COMMENT '项目成员编号',
  `role_id` bigint NOT NULL COMMENT '项目角色编号',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_tenant_member_role` (`tenant_id`, `member_id`, `role_id`) USING BTREE,
  KEY `idx_tenant_project_role` (`tenant_id`, `project_id`, `role_id`) USING BTREE
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目成员角色关系表';

DROP TABLE IF EXISTS `proj_sprint`;
CREATE TABLE `proj_sprint` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '迭代编号',
  `project_id` bigint NOT NULL COMMENT '归属项目编号',
  `title` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '迭代标题',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态，参见 project_sprint_status',
  `owner_user_id` bigint NOT NULL COMMENT '负责人用户编号',
  `planned_start_time` datetime DEFAULT NULL COMMENT '计划开始时间',
  `planned_end_time` datetime DEFAULT NULL COMMENT '计划结束时间',
  `actual_start_time` datetime DEFAULT NULL COMMENT '实际开始时间',
  `actual_end_time` datetime DEFAULT NULL COMMENT '实际结束时间',
  `capacity_hours` decimal(10,1) DEFAULT NULL COMMENT '工时容量（小时）',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '迭代描述',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_tenant_project_id` (`tenant_id`, `project_id`) USING BTREE,
  KEY `idx_tenant_status` (`tenant_id`, `status`) USING BTREE,
  KEY `idx_tenant_owner_user_id` (`tenant_id`, `owner_user_id`) USING BTREE,
  KEY `idx_tenant_project_status` (`tenant_id`, `project_id`, `status`) USING BTREE
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目迭代表';

DROP TABLE IF EXISTS `proj_work_item`;
CREATE TABLE `proj_work_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '工作项编号',
  `work_item_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工作项编号展示值',
  `title` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工作项标题',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '工作项描述',
  `category` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工作项分类，参见 project_work_item_category',
  `priority` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '优先级，参见 project_work_item_priority',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态，参见 project_work_item_status',
  `assignee_user_id` bigint NOT NULL COMMENT '负责人用户编号',
  `project_id` bigint NOT NULL COMMENT '归属项目编号',
  `sprint_id` bigint DEFAULT NULL COMMENT '归属迭代编号',
  `planned_start_time` datetime DEFAULT NULL COMMENT '计划开始时间',
  `planned_end_time` datetime DEFAULT NULL COMMENT '计划完成时间',
  `estimated_hours` decimal(20,2) NOT NULL DEFAULT 0.00 COMMENT '预估工时物化合计',
  `actual_hours` decimal(20,2) NOT NULL DEFAULT 0.00 COMMENT '实际工时物化合计',
  `parent_work_item_id` bigint DEFAULT NULL COMMENT '父工作项编号',
  `remark` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_tenant_work_item_no` (`tenant_id`, `work_item_no`) USING BTREE,
  KEY `idx_tenant_category` (`tenant_id`, `category`) USING BTREE,
  KEY `idx_tenant_status` (`tenant_id`, `status`) USING BTREE,
  KEY `idx_tenant_priority` (`tenant_id`, `priority`) USING BTREE,
  KEY `idx_tenant_assignee_user_id` (`tenant_id`, `assignee_user_id`) USING BTREE,
  KEY `idx_tenant_project_id` (`tenant_id`, `project_id`) USING BTREE,
  KEY `idx_tenant_sprint_id` (`tenant_id`, `sprint_id`) USING BTREE,
  KEY `idx_tenant_parent_work_item_id` (`tenant_id`, `parent_work_item_id`) USING BTREE,
  KEY `idx_tenant_planned_end_time` (`tenant_id`, `planned_end_time`) USING BTREE
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目工作项表';

DROP TABLE IF EXISTS `proj_worklog`;
CREATE TABLE `proj_worklog` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '工时编号',
  `project_id` bigint NOT NULL COMMENT '归属项目编号',
  `work_item_id` bigint NOT NULL COMMENT '工作项编号',
  `type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工时类型：ESTIMATED、ACTUAL',
  `hours` decimal(10,2) NOT NULL COMMENT '工时数（小时）',
  `category` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工作类别，参见 project_worklog_category',
  `description` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工作描述',
  `owner_user_id` bigint NOT NULL COMMENT '工时负责人用户编号',
  `work_date` date DEFAULT NULL COMMENT '工作日期，实际工时必填',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_tenant_project_work_date_type` (`tenant_id`, `project_id`, `work_date`, `type`) USING BTREE,
  KEY `idx_tenant_work_item_type` (`tenant_id`, `work_item_id`, `type`) USING BTREE,
  KEY `idx_tenant_project_owner_date` (`tenant_id`, `project_id`, `owner_user_id`, `work_date`) USING BTREE,
  KEY `idx_tenant_project_category` (`tenant_id`, `project_id`, `category`) USING BTREE
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目工时表';

DROP TABLE IF EXISTS `proj_comment`;
CREATE TABLE `proj_comment` (
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

DROP TABLE IF EXISTS `proj_comment_mention`;
CREATE TABLE `proj_comment_mention` (
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

DROP TABLE IF EXISTS `proj_work_item_field`;
CREATE TABLE `proj_work_item_field` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '工作项字段编号',
  `scope` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PROJECT' COMMENT '生效范围：GLOBAL 租户内全局、PROJECT 指定项目',
  `project_id` bigint NOT NULL COMMENT '归属项目编号，全局字段固定为 0',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字段编码，有效字段集合内唯一且创建后不可修改',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字段名称',
  `field_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字段类型',
  `applicable_category` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '适用工作项分类，为空表示全部分类',
  `required` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否必填',
  `enabled` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否启用',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
  `config_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '字段扩展配置 JSON',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_tenant_scope_project_code` (`tenant_id`, `scope`, `project_id`, `code`) USING BTREE,
  KEY `idx_tenant_scope_project_enabled_sort` (`tenant_id`, `scope`, `project_id`, `enabled`, `sort`) USING BTREE
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目工作项字段定义表';

DROP TABLE IF EXISTS `proj_work_item_field_option`;
CREATE TABLE `proj_work_item_field_option` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '工作项字段选项编号',
  `field_id` bigint NOT NULL COMMENT '字段编号',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '选项编码，字段内唯一且创建后不可修改',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '选项名称',
  `color` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '展示颜色',
  `enabled` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否启用',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_tenant_field_code` (`tenant_id`, `field_id`, `code`) USING BTREE,
  KEY `idx_tenant_field_enabled_sort` (`tenant_id`, `field_id`, `enabled`, `sort`) USING BTREE
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目工作项字段选项表';

DROP TABLE IF EXISTS `proj_work_item_field_value`;
CREATE TABLE `proj_work_item_field_value` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '工作项字段值编号',
  `work_item_id` bigint NOT NULL COMMENT '工作项编号',
  `field_id` bigint NOT NULL COMMENT '字段编号',
  `text_value` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '文本值',
  `number_value` decimal(20,6) DEFAULT NULL COMMENT '数字值',
  `datetime_value` datetime DEFAULT NULL COMMENT '日期时间值',
  `boolean_value` bit(1) DEFAULT NULL COMMENT '布尔值',
  `user_value` bigint DEFAULT NULL COMMENT '用户值',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_tenant_work_item_field` (`tenant_id`, `work_item_id`, `field_id`) USING BTREE,
  KEY `idx_tenant_field_text` (`tenant_id`, `field_id`, `text_value`(191), `work_item_id`) USING BTREE,
  KEY `idx_tenant_field_number` (`tenant_id`, `field_id`, `number_value`, `work_item_id`) USING BTREE,
  KEY `idx_tenant_field_datetime` (`tenant_id`, `field_id`, `datetime_value`, `work_item_id`) USING BTREE,
  KEY `idx_tenant_field_user` (`tenant_id`, `field_id`, `user_value`, `work_item_id`) USING BTREE,
  KEY `idx_tenant_field_boolean` (`tenant_id`, `field_id`, `boolean_value`, `work_item_id`) USING BTREE
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目工作项字段值表';

DROP TABLE IF EXISTS `proj_work_item_field_value_option`;
CREATE TABLE `proj_work_item_field_value_option` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '工作项字段选择值编号',
  `field_value_id` bigint NOT NULL COMMENT '字段值编号',
  `work_item_id` bigint NOT NULL COMMENT '工作项编号',
  `field_id` bigint NOT NULL COMMENT '字段编号',
  `option_id` bigint NOT NULL COMMENT '字段选项编号',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_tenant_value_option` (`tenant_id`, `field_value_id`, `option_id`) USING BTREE,
  KEY `idx_tenant_field_option_work_item` (`tenant_id`, `field_id`, `option_id`, `work_item_id`) USING BTREE,
  KEY `idx_tenant_work_item_field` (`tenant_id`, `work_item_id`, `field_id`) USING BTREE
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目工作项字段选择值表';

DROP TABLE IF EXISTS `proj_work_item_query_view`;
CREATE TABLE `proj_work_item_query_view` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '工作项查询视图编号',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '视图名称',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '视图编码，用于路由或查询参数',
  `scope` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '视图级别，参见 project_work_item_query_view_scope',
  `user_id` bigint NOT NULL DEFAULT 0 COMMENT '用户编号，系统级视图固定为 0',
  `route_path` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '前端路由路径',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
  `condition_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '查询条件 JSON，结构兼容工作项分页查询条件',
  `config_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '视图配置 JSON，用于保存列、排序、分组、展示模式等列表状态',
  `remark` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_tenant_scope_user_code` (`tenant_id`, `scope`, `user_id`, `code`) USING BTREE,
  KEY `idx_tenant_scope_sort` (`tenant_id`, `scope`, `sort`) USING BTREE,
  KEY `idx_tenant_user_sort` (`tenant_id`, `user_id`, `sort`) USING BTREE
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目工作项查询视图表';
