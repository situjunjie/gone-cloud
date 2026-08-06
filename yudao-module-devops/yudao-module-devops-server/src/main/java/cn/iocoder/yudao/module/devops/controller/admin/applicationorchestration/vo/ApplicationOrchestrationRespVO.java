package cn.iocoder.yudao.module.devops.controller.admin.applicationorchestration.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - DevOps 应用编排 Response VO")
@Data
public class ApplicationOrchestrationRespVO {

    private Long id;
    private Long appId;
    private String appKey;
    private String appName;
    private String orchestrationType;
    private ApplicationOrchestrationConfigVO config;
    private String canvasJson;
    private LocalDateTime updateTime;

}
