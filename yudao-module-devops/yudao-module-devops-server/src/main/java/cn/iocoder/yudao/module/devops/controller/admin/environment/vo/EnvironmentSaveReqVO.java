package cn.iocoder.yudao.module.devops.controller.admin.environment.vo;

import cn.iocoder.yudao.framework.common.validation.InEnum;
import cn.iocoder.yudao.module.devops.enums.EnvironmentInfraTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - DevOps 环境创建/修改 Request VO")
@Data
public class EnvironmentSaveReqVO {

    @Schema(description = "环境编号，修改时必填", example = "1024")
    private Long id;

    @Schema(description = "环境标识", requiredMode = Schema.RequiredMode.REQUIRED, example = "test")
    @NotBlank(message = "环境标识不能为空")
    @Size(max = 64, message = "环境标识长度不能超过 64 个字符")
    private String envKey;

    @Schema(description = "环境名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "测试环境")
    @NotBlank(message = "环境名称不能为空")
    @Size(max = 128, message = "环境名称长度不能超过 128 个字符")
    private String envName;

    @Schema(description = "环境阶段，参见 dev_env_stage", requiredMode = Schema.RequiredMode.REQUIRED, example = "TEST")
    @NotBlank(message = "环境阶段不能为空")
    private String envStage;

    @Schema(description = "基础设施类型，参见 dev_infra_type", requiredMode = Schema.RequiredMode.REQUIRED, example = "HOST")
    @NotBlank(message = "基础设施类型不能为空")
    @InEnum(EnvironmentInfraTypeEnum.class)
    private String infraType;

    @Schema(description = "Kubernetes 连接配置，infraType=K8S 时使用")
    @Valid
    private EnvironmentKubernetesConfigReqVO kubernetesConfig;

    @Schema(description = "Docker 连接配置，infraType=DOCKER 时使用")
    @Valid
    private EnvironmentDockerConfigReqVO dockerConfig;

    @Schema(description = "环境描述")
    @Size(max = 512, message = "环境描述长度不能超过 512 个字符")
    private String description;

    @Schema(description = "状态，参见 common_status", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "状态不能为空")
    private Integer status;

    @Schema(description = "备注")
    @Size(max = 512, message = "备注长度不能超过 512 个字符")
    private String remark;

}
