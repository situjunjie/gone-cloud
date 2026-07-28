package cn.iocoder.yudao.module.bpm.api.task.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "RPC 服务 - 流程实例状态 Response DTO")
@Data
public class BpmProcessInstanceStatusRespDTO {

    @Schema(description = "流程实例的编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private String id;

    @Schema(description = "流程定义的标识", requiredMode = Schema.RequiredMode.REQUIRED, example = "leave")
    private String processDefinitionKey;

    @Schema(description = "流程实例的状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    private Integer status;

    @Schema(description = "业务的唯一标识", example = "devops:pipeline-approval:1024:approval")
    private String businessKey;

}
