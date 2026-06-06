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
    ErrorCode ENVIRONMENT_INFRA_TYPE_NOT_SUPPORTED = new ErrorCode(1_011_001_003, "暂不支持该环境基础设施类型");
    ErrorCode ENVIRONMENT_KUBECONFIG_REQUIRED = new ErrorCode(1_011_001_004, "Kubernetes kubeconfig 不能为空");
    ErrorCode ENVIRONMENT_KUBERNETES_CONFIG_INVALID = new ErrorCode(1_011_001_005, "Kubernetes 配置无效：{}");
    ErrorCode ENVIRONMENT_KUBERNETES_CONNECTION_FAIL = new ErrorCode(1_011_001_006, "Kubernetes 连接失败：{}");
    ErrorCode ENVIRONMENT_KUBERNETES_NAMESPACE_REQUIRED = new ErrorCode(1_011_001_007, "Kubernetes Namespace 不能为空");

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

    // ========== 代码源 1-011-005-000 ==========
    ErrorCode REPOSITORY_PROVIDER_NOT_EXISTS = new ErrorCode(1_011_005_000, "代码源不存在");
    ErrorCode REPOSITORY_PROVIDER_NAME_DUPLICATE = new ErrorCode(1_011_005_001, "代码源名称已存在");
    ErrorCode REPOSITORY_PROVIDER_TYPE_NOT_SUPPORTED = new ErrorCode(1_011_005_002, "暂不支持该代码源类型");
    ErrorCode REPOSITORY_PROVIDER_AUTH_TYPE_NOT_SUPPORTED = new ErrorCode(1_011_005_003, "暂不支持该代码源认证方式");
    ErrorCode REPOSITORY_PROVIDER_ACCESS_TOKEN_REQUIRED = new ErrorCode(1_011_005_004, "Access Token 不能为空");
    ErrorCode REPOSITORY_PROVIDER_GITLAB_CONNECTION_FAIL = new ErrorCode(1_011_005_005, "GitLab 连接失败：{}");

}
