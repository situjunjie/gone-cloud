package cn.iocoder.yudao.module.project.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * Project 错误码枚举类。
 *
 * project 系统，使用 1-050-000-000 段。
 */
public interface ErrorCodeConstants {

    // ========== 项目 1-050-000-000 ==========
    ErrorCode PROJECT_NOT_EXISTS = new ErrorCode(1_050_000_000, "项目不存在");
    ErrorCode PROJECT_CODE_DUPLICATE = new ErrorCode(1_050_000_001, "项目编号已存在");
    ErrorCode PROJECT_OWNER_INVALID = new ErrorCode(1_050_000_002, "项目负责人无效");
    ErrorCode PROJECT_ARCHIVED = new ErrorCode(1_050_000_003, "项目已归档");
    ErrorCode PROJECT_MEMBER_NOT_EXISTS = new ErrorCode(1_050_000_004, "项目成员不存在");
    ErrorCode PROJECT_MEMBER_DUPLICATE = new ErrorCode(1_050_000_005, "项目成员已存在");
    ErrorCode PROJECT_OWNER_CANNOT_REMOVE = new ErrorCode(1_050_000_006, "项目负责人不能直接移除");
    ErrorCode PROJECT_ACCESS_DENIED = new ErrorCode(1_050_000_007, "无权访问该项目");
    ErrorCode PROJECT_OWNER_ROLE_CHANGE_UNSUPPORTED = new ErrorCode(1_050_000_008, "负责人角色请通过项目负责人转移修改");

    // ========== 工作项 1-050-001-000 ==========
    ErrorCode PROJECT_WORK_ITEM_NOT_EXISTS = new ErrorCode(1_050_001_000, "工作项不存在");
    ErrorCode PROJECT_WORK_ITEM_PLANNED_TIME_INVALID = new ErrorCode(1_050_001_001, "计划完成时间不能早于计划开始时间");
    ErrorCode PROJECT_WORK_ITEM_PARENT_INVALID = new ErrorCode(1_050_001_002, "父工作项无效");
    ErrorCode PROJECT_WORK_ITEM_HAS_CHILDREN = new ErrorCode(1_050_001_003, "存在子工作项，不能移动或删除");

}
