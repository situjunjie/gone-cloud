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
    ErrorCode CHANGE_BRANCH_NAME_INVALID = new ErrorCode(1_011_003_004, "变更分支名称不符合 Git 分支规范");
    ErrorCode CHANGE_LATEST_COMMIT_NOT_EXISTS = new ErrorCode(1_011_003_005, "变更暂无最新提交，不能完成代码审核");

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
    ErrorCode REPOSITORY_PROVIDER_DELETE_FAIL_APPLICATION_EXISTS = new ErrorCode(1_011_005_006, "代码源已被应用引用，不能删除");
    ErrorCode REPOSITORY_PROVIDER_GITLAB_BRANCH_CREATE_FAIL = new ErrorCode(1_011_005_007, "GitLab 分支创建失败：{}");
    ErrorCode REPOSITORY_PROVIDER_GITLAB_COMPARE_FAIL = new ErrorCode(1_011_005_008, "GitLab 代码比较失败：{}");

    // ========== 流水线 1-011-006-000 ==========
    ErrorCode PIPELINE_DEFINITION_NOT_EXISTS = new ErrorCode(1_011_006_000, "流水线定义不存在");
    ErrorCode PIPELINE_VERSION_NOT_EXISTS = new ErrorCode(1_011_006_001, "流水线版本不存在");
    ErrorCode PIPELINE_DRAFT_NOT_EXISTS = new ErrorCode(1_011_006_002, "流水线草稿不存在");
    ErrorCode PIPELINE_SPEC_INVALID = new ErrorCode(1_011_006_003, "流水线配置校验不通过");
    ErrorCode PIPELINE_NODE_TYPE_NOT_SUPPORTED = new ErrorCode(1_011_006_004, "流水线节点类型不支持：{}");
    ErrorCode PIPELINE_NODE_PARAM_INVALID = new ErrorCode(1_011_006_005, "流水线节点参数无效：{}");
    ErrorCode PIPELINE_GRAPH_HAS_CYCLE = new ErrorCode(1_011_006_006, "流水线节点不能形成环路");
    ErrorCode PIPELINE_GRAPH_START_NODE_INVALID = new ErrorCode(1_011_006_007, "流水线必须且只能有一个开始节点");
    ErrorCode PIPELINE_GRAPH_TERMINAL_NODE_INVALID = new ErrorCode(1_011_006_008, "流水线必须且只能有一个结束节点");
    ErrorCode PIPELINE_JENKINSFILE_GENERATE_FAIL = new ErrorCode(1_011_006_009, "Jenkinsfile 生成失败：{}");
    ErrorCode PIPELINE_PUBLISHED_VERSION_NOT_EXISTS = new ErrorCode(1_011_006_010, "应用环境没有已发布流水线版本");
    ErrorCode PIPELINE_RUN_NOT_EXISTS = new ErrorCode(1_011_006_011, "流水线运行不存在");
    ErrorCode PIPELINE_RUN_LOG_NOT_EXISTS = new ErrorCode(1_011_006_012, "流水线运行日志不存在");
    ErrorCode PIPELINE_RUN_ACTIVE_EXISTS = new ErrorCode(1_011_006_013, "当前应用环境已有执行中的流水线运行");
    ErrorCode PIPELINE_RUN_LOG_STATE_INVALID = new ErrorCode(1_011_006_014, "当前流水线运行状态不允许执行该操作");
    ErrorCode PIPELINE_CODE_MERGE_CONFLICT_UNRESOLVED = new ErrorCode(1_011_006_015, "仍有未解决的代码冲突");
    ErrorCode PIPELINE_CODE_MERGE_CONFLICT_UNSUPPORTED = new ErrorCode(1_011_006_016, "当前冲突不支持在线解决");
    ErrorCode PIPELINE_CODE_MERGE_GIT_EXEC_FAIL = new ErrorCode(1_011_006_017, "Git 执行失败：{}");
    ErrorCode PIPELINE_CODE_MERGE_WORKSPACE_NOT_EXISTS = new ErrorCode(1_011_006_018, "代码合并工作区不存在");
    ErrorCode PIPELINE_CODE_MERGE_REPOSITORY_AUTH_NOT_SUPPORTED = new ErrorCode(1_011_006_019, "当前代码源暂不支持代码合并认证方式");
    ErrorCode PIPELINE_JENKINS_CONFIG_INVALID = new ErrorCode(1_011_006_020, "Jenkins 配置无效：{}");
    ErrorCode PIPELINE_JENKINS_TRIGGER_FAIL = new ErrorCode(1_011_006_021, "Jenkins 构建触发失败：{}");
    ErrorCode PIPELINE_JENKINS_CALLBACK_TOKEN_INVALID = new ErrorCode(1_011_006_022, "Jenkins 回调令牌无效");
    ErrorCode PIPELINE_JENKINS_CALLBACK_INVALID = new ErrorCode(1_011_006_023, "Jenkins 回调请求无效：{}");
    ErrorCode PIPELINE_JENKINS_CONSOLE_UNAVAILABLE = new ErrorCode(1_011_006_024, "Jenkins Console 暂不可用：{}");
    ErrorCode PIPELINE_JENKINS_CONSOLE_FETCH_FAIL = new ErrorCode(1_011_006_025, "Jenkins Console 获取失败：{}");
    ErrorCode PIPELINE_JENKINS_TOOL_FETCH_FAIL = new ErrorCode(1_011_006_026, "Jenkins 工具列表获取失败：{}");
    ErrorCode PIPELINE_APPROVAL_BUSINESS_KEY_INVALID = new ErrorCode(1_011_006_027, "流水线审批业务标识无效：{}");

    // ========== 部署单 1-011-007-000 ==========
    ErrorCode DEPLOYMENT_ORDER_NOT_EXISTS = new ErrorCode(1_011_007_000, "部署单不存在");
    ErrorCode DEPLOYMENT_ORDER_STATE_INVALID = new ErrorCode(1_011_007_001, "当前部署单状态不允许执行该操作");
    ErrorCode DEPLOYMENT_NODE_PARAM_INVALID = new ErrorCode(1_011_007_002, "部署节点参数无效：{}");
    ErrorCode DEPLOYMENT_ENVIRONMENT_NOT_K8S = new ErrorCode(1_011_007_003, "容器部署仅支持 Kubernetes 环境");
    ErrorCode DEPLOYMENT_KUBERNETES_WORKLOAD_NOT_EXISTS = new ErrorCode(1_011_007_004, "Kubernetes Deployment 不存在：{}");
    ErrorCode DEPLOYMENT_KUBERNETES_CONTAINER_NOT_EXISTS = new ErrorCode(1_011_007_005, "Kubernetes 容器不存在：{}");
    ErrorCode DEPLOYMENT_KUBERNETES_APPLY_FAIL = new ErrorCode(1_011_007_006, "Kubernetes 部署提交失败：{}");
    ErrorCode DEPLOYMENT_KUBERNETES_ROLLOUT_TIMEOUT = new ErrorCode(1_011_007_007, "Kubernetes Deployment rollout 超时");
    ErrorCode DEPLOYMENT_MANIFEST_REQUIRED = new ErrorCode(1_011_007_008, "Deployment YAML 不能为空");
    ErrorCode DEPLOYMENT_MANIFEST_INVALID = new ErrorCode(1_011_007_009, "Deployment YAML 无法解析：{}");
    ErrorCode DEPLOYMENT_MANIFEST_KIND_NOT_SUPPORTED = new ErrorCode(1_011_007_010, "当前仅支持 Deployment 类型的 YAML");
    ErrorCode DEPLOYMENT_MANIFEST_NAME_REQUIRED = new ErrorCode(1_011_007_011, "Deployment YAML 缺少 metadata.name");
    ErrorCode DEPLOYMENT_MANIFEST_CONTAINER_NOT_EXISTS = new ErrorCode(1_011_007_012, "Deployment YAML 中不存在目标容器：{}");

    // ========== Kubernetes Pod 终端 1-011-008-000 ==========
    ErrorCode KUBERNETES_POD_NOT_EXISTS = new ErrorCode(1_011_008_000, "Kubernetes Pod 不存在：{}");
    ErrorCode KUBERNETES_POD_NOT_RUNNING = new ErrorCode(1_011_008_001, "Kubernetes Pod 当前不是 Running 状态：{}");
    ErrorCode KUBERNETES_POD_CONTAINER_REQUIRED = new ErrorCode(1_011_008_002, "Pod 包含多个容器，请明确选择容器");
    ErrorCode KUBERNETES_POD_CONTAINER_NOT_EXISTS = new ErrorCode(1_011_008_003, "Kubernetes Pod 容器不存在：{}");
    ErrorCode KUBERNETES_TERMINAL_EXEC_FAIL = new ErrorCode(1_011_008_004, "Kubernetes 终端连接失败：{}");

    // ========== 离线镜像包 1-011-009-000 ==========
    ErrorCode OFFLINE_IMAGE_PACKAGE_NOT_EXISTS = new ErrorCode(1_011_009_000, "离线镜像包不存在");

}
