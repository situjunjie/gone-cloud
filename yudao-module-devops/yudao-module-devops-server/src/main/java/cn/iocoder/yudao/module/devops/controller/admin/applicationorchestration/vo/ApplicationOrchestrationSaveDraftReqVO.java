package cn.iocoder.yudao.module.devops.controller.admin.applicationorchestration.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - DevOps 应用编排保存草稿 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ApplicationOrchestrationSaveDraftReqVO extends ApplicationOrchestrationValidateReqVO {

    @Schema(description = "页面读取到的更新时间，更新已有草稿时必填")
    private LocalDateTime updateTime;

}
