package cn.iocoder.yudao.module.devops.controller.admin.applicationorchestration.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "管理后台 - DevOps 应用编排校验消息")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationOrchestrationValidationMessageRespVO {

    private String componentId;
    private String field;
    private String code;
    private String message;

}
