package cn.iocoder.yudao.module.bpm.api.task.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotEmpty;

@Schema(description = "RPC 服务 - 流程实例的取消 Request DTO")
@Data
public class BpmProcessInstanceCancelReqDTO {

    @Schema(description = "流程实例的编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotEmpty(message = "流程实例的编号不能为空")
    private String id;

    @Schema(description = "取消原因", requiredMode = Schema.RequiredMode.REQUIRED, example = "流水线已取消")
    @NotEmpty(message = "取消原因不能为空")
    private String reason;

}
