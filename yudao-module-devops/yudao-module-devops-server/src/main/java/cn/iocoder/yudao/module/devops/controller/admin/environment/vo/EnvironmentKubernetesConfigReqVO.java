package cn.iocoder.yudao.module.devops.controller.admin.environment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - DevOps 环境 Kubernetes 配置 Request VO")
@Data
public class EnvironmentKubernetesConfigReqVO {

    @Schema(description = "kubeconfig 文件内容；创建 K8S 环境时必填，修改时为空则保持原值")
    @Size(max = 20000, message = "kubeconfig 长度不能超过 20000 个字符")
    private String kubeconfig;

    @Schema(description = "部署目标 Namespace；K8S 环境必填", requiredMode = Schema.RequiredMode.REQUIRED, example = "test")
    @Size(max = 63, message = "Namespace 长度不能超过 63 个字符")
    @Pattern(regexp = "^[a-z0-9]([-a-z0-9]*[a-z0-9])?$", message = "Namespace 只能包含小写字母、数字和中划线，且必须以字母或数字开头和结尾")
    private String namespace;

}
