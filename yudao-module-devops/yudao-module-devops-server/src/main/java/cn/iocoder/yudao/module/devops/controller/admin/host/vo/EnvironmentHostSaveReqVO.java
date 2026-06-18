package cn.iocoder.yudao.module.devops.controller.admin.host.vo;

import cn.iocoder.yudao.framework.common.validation.InEnum;
import cn.iocoder.yudao.module.devops.enums.HostAuthTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - DevOps 环境主机创建/修改 Request VO")
@Data
public class EnvironmentHostSaveReqVO {

    @Schema(description = "主机编号，修改时必填", example = "1024")
    private Long id;

    @Schema(description = "环境编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    @NotNull(message = "环境编号不能为空")
    private Long envId;

    @Schema(description = "主机标识", requiredMode = Schema.RequiredMode.REQUIRED, example = "app-01")
    @NotBlank(message = "主机标识不能为空")
    @Size(max = 64, message = "主机标识长度不能超过 64 个字符")
    private String hostKey;

    @Schema(description = "主机名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "应用服务器 01")
    @NotBlank(message = "主机名称不能为空")
    @Size(max = 128, message = "主机名称长度不能超过 128 个字符")
    private String hostName;

    @Schema(description = "SSH 主机地址", requiredMode = Schema.RequiredMode.REQUIRED, example = "192.168.1.10")
    @NotBlank(message = "SSH 主机地址不能为空")
    @Size(max = 255, message = "SSH 主机地址长度不能超过 255 个字符")
    private String host;

    @Schema(description = "SSH 端口", requiredMode = Schema.RequiredMode.REQUIRED, example = "22")
    @NotNull(message = "SSH 端口不能为空")
    @Min(value = 1, message = "SSH 端口必须大于 0")
    @Max(value = 65535, message = "SSH 端口不能超过 65535")
    private Integer port;

    @Schema(description = "SSH 用户名", requiredMode = Schema.RequiredMode.REQUIRED, example = "root")
    @NotBlank(message = "SSH 用户名不能为空")
    @Size(max = 128, message = "SSH 用户名长度不能超过 128 个字符")
    private String username;

    @Schema(description = "认证方式", requiredMode = Schema.RequiredMode.REQUIRED, example = "PASSWORD")
    @NotBlank(message = "认证方式不能为空")
    @InEnum(HostAuthTypeEnum.class)
    private String authType;

    @Schema(description = "SSH 密码，authType=PASSWORD 时使用")
    private String password;

    @Schema(description = "SSH 私钥，authType=PRIVATE_KEY 时使用")
    private String privateKey;

    @Schema(description = "SSH 私钥口令")
    private String passphrase;

    @Schema(description = "是否启用 sudo", example = "false")
    private Boolean sudoEnabled;

    @Schema(description = "主机描述")
    @Size(max = 512, message = "主机描述长度不能超过 512 个字符")
    private String description;

    @Schema(description = "状态，参见 common_status", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "状态不能为空")
    private Integer status;

    @Schema(description = "备注")
    @Size(max = 512, message = "备注长度不能超过 512 个字符")
    private String remark;

}
