package cn.iocoder.yudao.module.devops.controller.admin.environment.vo;

import cn.iocoder.yudao.framework.common.validation.InEnum;
import cn.iocoder.yudao.module.devops.enums.EnvironmentKubernetesResourceKindEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - DevOps 环境 Kubernetes 资源 YAML 查看 Request VO")
@Data
public class EnvironmentKubernetesResourceYamlReqVO {

    @Schema(description = "环境编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "环境编号不能为空")
    private Long id;

    @Schema(description = "资源类型，参见 devops_kubernetes_resource_kind", requiredMode = Schema.RequiredMode.REQUIRED, example = "DEPLOYMENT")
    @NotBlank(message = "资源类型不能为空")
    @InEnum(EnvironmentKubernetesResourceKindEnum.class)
    private String kind;

    @Schema(description = "资源名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "yudao-server")
    @NotBlank(message = "资源名称不能为空")
    @Size(max = 253, message = "资源名称长度不能超过 253 个字符")
    private String name;

    @Schema(description = "Namespace，未传则使用环境配置的 Namespace", example = "prod")
    @Size(max = 63, message = "Namespace 长度不能超过 63 个字符")
    private String namespace;

}
