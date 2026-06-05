package cn.iocoder.yudao.module.devops.controller.admin.change.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - DevOps 变更废弃 Request VO")
@Data
public class ChangeDiscardReqVO {

    @Schema(description = "变更编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "变更编号不能为空")
    private Long id;

    @Schema(description = "废弃原因")
    @Size(max = 512, message = "废弃原因长度不能超过 512 个字符")
    private String discardReason;

}
