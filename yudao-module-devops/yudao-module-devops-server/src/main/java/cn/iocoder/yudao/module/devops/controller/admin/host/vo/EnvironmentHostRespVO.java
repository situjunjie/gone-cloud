package cn.iocoder.yudao.module.devops.controller.admin.host.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - DevOps 环境主机 Response VO")
@Data
public class EnvironmentHostRespVO {

    @Schema(description = "主机编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "环境编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    private Long envId;

    @Schema(description = "主机标识", requiredMode = Schema.RequiredMode.REQUIRED, example = "app-01")
    private String hostKey;

    @Schema(description = "主机名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "应用服务器 01")
    private String hostName;

    @Schema(description = "SSH 主机地址", requiredMode = Schema.RequiredMode.REQUIRED, example = "192.168.1.10")
    private String host;

    @Schema(description = "SSH 端口", requiredMode = Schema.RequiredMode.REQUIRED, example = "22")
    private Integer port;

    @Schema(description = "SSH 用户名", requiredMode = Schema.RequiredMode.REQUIRED, example = "root")
    private String username;

    @Schema(description = "认证方式", requiredMode = Schema.RequiredMode.REQUIRED, example = "PASSWORD")
    private String authType;

    @Schema(description = "是否已配置凭据", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean credentialConfigured;

    @Schema(description = "是否启用 sudo")
    private Boolean sudoEnabled;

    @Schema(description = "主机描述")
    private String description;

    @Schema(description = "状态，参见 common_status", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer status;

    @Schema(description = "最近检测状态")
    private Integer lastCheckStatus;

    @Schema(description = "最近检测时间")
    private LocalDateTime lastCheckTime;

    @Schema(description = "最近检测消息")
    private String lastCheckMessage;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
