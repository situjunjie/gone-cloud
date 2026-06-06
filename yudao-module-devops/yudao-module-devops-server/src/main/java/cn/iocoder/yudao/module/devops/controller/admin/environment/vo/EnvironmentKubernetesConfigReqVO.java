package cn.iocoder.yudao.module.devops.controller.admin.environment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - DevOps 环境 Kubernetes 配置 Request VO")
@Data
public class EnvironmentKubernetesConfigReqVO {

    @Schema(description = "kubeconfig 文件内容；创建 K8S 环境时必填，修改时为空则保持原值")
    @Size(max = 20000, message = "kubeconfig 长度不能超过 20000 个字符")
    private String kubeconfig;

}
