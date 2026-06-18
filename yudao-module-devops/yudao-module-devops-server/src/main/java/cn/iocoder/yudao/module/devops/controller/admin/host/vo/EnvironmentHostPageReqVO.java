package cn.iocoder.yudao.module.devops.controller.admin.host.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - DevOps 环境主机分页 Request VO")
@Data
public class EnvironmentHostPageReqVO extends PageParam {

    @Schema(description = "环境编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "环境编号不能为空")
    private Long envId;

    @Schema(description = "主机标识，模糊匹配", example = "app-01")
    private String hostKey;

    @Schema(description = "主机名称，模糊匹配", example = "应用服务器 01")
    private String hostName;

    @Schema(description = "SSH 主机地址，模糊匹配", example = "192.168.1.10")
    private String host;

    @Schema(description = "状态，参见 common_status", example = "0")
    private Integer status;

}
