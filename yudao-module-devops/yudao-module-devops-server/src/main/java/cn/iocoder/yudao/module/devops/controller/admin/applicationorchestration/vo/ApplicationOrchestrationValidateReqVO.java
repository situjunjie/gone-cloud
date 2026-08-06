package cn.iocoder.yudao.module.devops.controller.admin.applicationorchestration.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - DevOps 应用编排校验 Request VO")
@Data
public class ApplicationOrchestrationValidateReqVO {

    @Schema(description = "应用编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "应用编号不能为空")
    private Long appId;

    @Schema(description = "编排类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "K8S_DEPLOYMENT")
    @NotBlank(message = "编排类型不能为空")
    private String orchestrationType;

    @Schema(description = "编排配置", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "编排配置不能为空")
    @Valid
    private ApplicationOrchestrationConfigVO config;

    @Schema(description = "画布 JSON")
    private String canvasJson;

}
