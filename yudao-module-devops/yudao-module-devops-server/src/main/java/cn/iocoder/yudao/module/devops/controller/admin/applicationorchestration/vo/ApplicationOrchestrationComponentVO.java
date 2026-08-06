package cn.iocoder.yudao.module.devops.controller.admin.applicationorchestration.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "管理后台 - DevOps 应用编排组件")
@Data
public class ApplicationOrchestrationComponentVO {

    @Schema(description = "组件标识", requiredMode = Schema.RequiredMode.REQUIRED, example = "demo-service")
    @NotBlank(message = "组件标识不能为空")
    private String id;

    @Schema(description = "组件名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "demo-service")
    @NotBlank(message = "组件名称不能为空")
    private String name;

    @Schema(description = "模板标识", requiredMode = Schema.RequiredMode.REQUIRED, example = "SERVICE")
    @NotBlank(message = "模板标识不能为空")
    private String templateKey;

    @Schema(description = "Kubernetes 资源类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "Service")
    @NotBlank(message = "Kubernetes 资源类型不能为空")
    private String kind;

    @Schema(description = "应用顺序", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "应用顺序不能为空")
    private Integer applyOrder;

    @Schema(description = "依赖组件标识")
    private List<String> dependsOn = new ArrayList<>();

    @Schema(description = "是否启用", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "是否启用不能为空")
    private Boolean enabled;

    @Schema(description = "Kubernetes YAML", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Kubernetes YAML 不能为空")
    private String yaml;

}
