-- ============================================================
-- DevOps 应用成员增量迁移
-- 适用旧结构：不存在 dev_application_member
--
-- 仅新增一张成员表；拥有者不落库，由 dev_application.owner_user_id 派生，
-- 因此无需回填历史数据。
--
-- 执行前请先备份数据库。
-- ============================================================

CREATE TABLE IF NOT EXISTS `dev_application_member` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '应用成员编号',
  `app_id` bigint NOT NULL COMMENT '应用编号',
  `user_id` bigint NOT NULL COMMENT '成员用户编号',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_tenant_app_user` (`tenant_id`, `app_id`, `user_id`) USING BTREE,
  KEY `idx_tenant_app` (`tenant_id`, `app_id`) USING BTREE
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='DevOps 应用成员表';
