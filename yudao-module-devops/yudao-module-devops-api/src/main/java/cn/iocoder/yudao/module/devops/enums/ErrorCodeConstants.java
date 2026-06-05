package cn.iocoder.yudao.module.devops.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * DevOps 错误码枚举类。
 *
 * devops 系统，使用 1-011-000-000 段。
 */
public interface ErrorCodeConstants {

    // ========== 应用 1-011-000-000 ==========
    ErrorCode APPLICATION_NOT_EXISTS = new ErrorCode(1_011_000_000, "应用不存在");
    ErrorCode APPLICATION_APP_KEY_DUPLICATE = new ErrorCode(1_011_000_001, "应用标识已存在");
    ErrorCode APPLICATION_REPO_IDENTIFIER_DUPLICATE = new ErrorCode(1_011_000_002, "代码库唯一标识已存在");
    ErrorCode APPLICATION_DELETE_FAIL_CHANGE_EXISTS = new ErrorCode(1_011_000_003, "应用存在变更，不能删除");

    // ========== 环境 1-011-001-000 ==========
    ErrorCode ENVIRONMENT_NOT_EXISTS = new ErrorCode(1_011_001_000, "环境不存在");
    ErrorCode ENVIRONMENT_ENV_KEY_DUPLICATE = new ErrorCode(1_011_001_001, "环境标识已存在");
    ErrorCode ENVIRONMENT_DELETE_FAIL_APPLICATION_ENV_EXISTS = new ErrorCode(1_011_001_002, "环境已被应用配置引用，不能删除");

    // ========== 应用环境 1-011-002-000 ==========
    ErrorCode APPLICATION_ENV_NOT_EXISTS = new ErrorCode(1_011_002_000, "应用环境关系不存在");
    ErrorCode APPLICATION_ENV_DUPLICATE = new ErrorCode(1_011_002_001, "应用环境关系已存在");

    // ========== 变更 1-011-003-000 ==========
    ErrorCode CHANGE_NOT_EXISTS = new ErrorCode(1_011_003_000, "变更不存在");
    ErrorCode CHANGE_KEY_DUPLICATE = new ErrorCode(1_011_003_001, "变更标识已存在");
    ErrorCode CHANGE_BRANCH_NAME_DUPLICATE = new ErrorCode(1_011_003_002, "变更分支名称已存在");
    ErrorCode CHANGE_STATUS_NOT_ACTIVE = new ErrorCode(1_011_003_003, "变更不是有效状态，不能执行该操作");

    // ========== 变更环境 1-011-004-000 ==========
    ErrorCode CHANGE_ENV_NOT_EXISTS = new ErrorCode(1_011_004_000, "变更环境关系不存在");
    ErrorCode CHANGE_ENV_DUPLICATE = new ErrorCode(1_011_004_001, "变更环境关系已存在");

}
