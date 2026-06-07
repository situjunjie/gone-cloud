package cn.iocoder.yudao.module.devops.enums;

/**
 * DevOps 字典类型的枚举类
 */
public interface DictTypeConstants {

    String REPO_PROVIDER_TYPE = "dev_repo_provider_type"; // 代码库提供方类型
    String REPO_PROVIDER_AUTH_TYPE = "dev_repo_provider_auth_type"; // 代码源认证类型
    String CHANGE_STATUS = "dev_change_status"; // 变更状态
    String CHANGE_CODE_REVIEW_STATUS = "dev_change_code_review_status"; // 变更代码审核状态
    String ENV_STAGE = "dev_env_stage"; // 环境阶段
    String INFRA_TYPE = "dev_infra_type"; // 基础设施类型
    String CHANGE_ENV_MOUNT_STATUS = "dev_change_env_mount_status"; // 变更环境挂载状态
    String PIPELINE_MERGE_STATUS = "dev_pipeline_merge_status"; // 合并状态
    String PIPELINE_STAGE_STATUS = "dev_pipeline_stage_status"; // 流水线阶段状态
    String APPROVAL_STATUS = "dev_approval_status"; // 审批状态
    String PIPELINE_DEFINITION_VERSION_STATUS = "dev_pipeline_definition_version_status"; // 流水线定义版本状态
    String PIPELINE_RUN_STATUS = "dev_pipeline_run_status"; // 流水线运行状态

}
