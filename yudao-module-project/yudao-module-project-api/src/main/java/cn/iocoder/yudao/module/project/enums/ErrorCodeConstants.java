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
    ErrorCode PROJECT_ARCHIVED = new ErrorCode(1_050_000_003, "项目已归档");
    ErrorCode PROJECT_MEMBER_NOT_EXISTS = new ErrorCode(1_050_000_004, "项目成员不存在");
    ErrorCode PROJECT_MEMBER_DUPLICATE = new ErrorCode(1_050_000_005, "项目成员已存在");
    ErrorCode PROJECT_OWNER_CANNOT_REMOVE = new ErrorCode(1_050_000_006, "项目负责人不能直接移除");
    ErrorCode PROJECT_ACCESS_DENIED = new ErrorCode(1_050_000_007, "无权访问该项目");
    ErrorCode PROJECT_ONLY_PRIVATE_SUPPORTED = new ErrorCode(1_050_000_009, "当前仅支持私有项目");
    ErrorCode PROJECT_ROLE_NOT_EXISTS = new ErrorCode(1_050_000_010, "项目角色不存在");
    ErrorCode PROJECT_ROLE_CODE_DUPLICATE = new ErrorCode(1_050_000_011, "项目角色编码已存在");
    ErrorCode PROJECT_BUILTIN_ROLE_IMMUTABLE = new ErrorCode(1_050_000_012, "内置角色不可修改或删除");
    ErrorCode PROJECT_ADMIN_PERMISSION_IMMUTABLE = new ErrorCode(1_050_000_013, "管理员角色权限不可修改");
    ErrorCode PROJECT_PERMISSION_CODE_INVALID = new ErrorCode(1_050_000_014, "项目权限编码无效：{}");
    ErrorCode PROJECT_PARTICIPANT_PERMISSION_REQUIRED = new ErrorCode(1_050_000_015, "参与人角色必须保留权限：{}");
    ErrorCode PROJECT_ROLE_IN_USE = new ErrorCode(1_050_000_016, "项目角色仍有关联成员，不能删除");
    ErrorCode PROJECT_MEMBER_ROLE_REQUIRED = new ErrorCode(1_050_000_017, "项目成员至少需要一个角色");
    ErrorCode PROJECT_ROLE_SCOPE_MISMATCH = new ErrorCode(1_050_000_018, "项目角色作用域或归属项目不匹配");
    ErrorCode PROJECT_ADMIN_REQUIRED = new ErrorCode(1_050_000_019, "仅项目管理员可执行该操作");
    ErrorCode PROJECT_PERMISSION_DENIED = new ErrorCode(1_050_000_020, "缺少项目权限：{}");
    ErrorCode PROJECT_OWNER_ADMIN_REQUIRED = new ErrorCode(1_050_000_021, "项目负责人必须保留管理员角色");

    // ========== 工作项 1-050-001-000 ==========
    ErrorCode PROJECT_WORK_ITEM_NOT_EXISTS = new ErrorCode(1_050_001_000, "工作项不存在");
    ErrorCode PROJECT_WORK_ITEM_PLANNED_TIME_INVALID = new ErrorCode(1_050_001_001, "计划完成时间不能早于计划开始时间");
    ErrorCode PROJECT_WORK_ITEM_PARENT_INVALID = new ErrorCode(1_050_001_002, "父工作项无效");
    ErrorCode PROJECT_WORK_ITEM_HAS_CHILDREN = new ErrorCode(1_050_001_003, "存在子工作项，不能移动或删除");
    ErrorCode PROJECT_WORK_ITEM_FIELD_NOT_SUPPORTED = new ErrorCode(1_050_001_004, "工作项字段不支持快捷更新：{}");
    ErrorCode PROJECT_WORK_ITEM_FIELD_VALUE_INVALID = new ErrorCode(1_050_001_005, "工作项字段值无效：{}");
    ErrorCode PROJECT_WORK_ITEM_QUERY_FIELD_NOT_SUPPORTED = new ErrorCode(1_050_001_006, "工作项查询字段不支持：{}");
    ErrorCode PROJECT_WORK_ITEM_QUERY_CONDITION_INVALID = new ErrorCode(1_050_001_007, "工作项查询条件无效：{}");
    ErrorCode WORK_ITEM_QUERY_VIEW_NOT_EXISTS = new ErrorCode(1_050_001_008, "工作项查询视图不存在");
    ErrorCode WORK_ITEM_QUERY_VIEW_CODE_DUPLICATE = new ErrorCode(1_050_001_009, "工作项查询视图编码已存在");
    ErrorCode WORK_ITEM_QUERY_VIEW_ACCESS_DENIED = new ErrorCode(1_050_001_010, "无权访问该工作项查询视图");
    ErrorCode WORK_ITEM_FIELD_NOT_EXISTS = new ErrorCode(1_050_001_011, "工作项自定义字段不存在");
    ErrorCode WORK_ITEM_FIELD_CODE_DUPLICATE = new ErrorCode(1_050_001_012, "工作项自定义字段编码已存在");
    ErrorCode WORK_ITEM_FIELD_IMMUTABLE = new ErrorCode(1_050_001_013, "字段编码和字段类型创建后不可修改");
    ErrorCode WORK_ITEM_FIELD_OPTION_NOT_EXISTS = new ErrorCode(1_050_001_014, "工作项自定义字段选项不存在");
    ErrorCode WORK_ITEM_FIELD_OPTION_CODE_DUPLICATE = new ErrorCode(1_050_001_015, "工作项自定义字段选项编码已存在");
    ErrorCode WORK_ITEM_FIELD_OPTION_UNSUPPORTED = new ErrorCode(1_050_001_016, "当前字段类型不支持选项");
    ErrorCode WORK_ITEM_CUSTOM_FIELD_VALUE_INVALID = new ErrorCode(1_050_001_017, "自定义字段值无效：{}");
    ErrorCode WORK_ITEM_CUSTOM_FIELD_REQUIRED = new ErrorCode(1_050_001_018, "自定义字段必填：{}");
    ErrorCode WORK_ITEM_CUSTOM_FIELD_PROJECT_CHANGE_DENIED = new ErrorCode(1_050_001_019, "工作项存在自定义字段值，不能修改所属项目");
    ErrorCode WORK_ITEM_CUSTOM_FIELD_CATEGORY_CHANGE_DENIED = new ErrorCode(1_050_001_020, "工作项分类修改后已有自定义字段不再适用：{}");
    ErrorCode WORK_ITEM_FIELD_SCOPE_MISMATCH = new ErrorCode(1_050_001_021, "工作项自定义字段作用域不匹配");

}
