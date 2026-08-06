package cn.iocoder.yudao.module.devops.controller.admin.applicationorchestration.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "管理后台 - DevOps 应用编排配置")
@Data
public class ApplicationOrchestrationConfigVO {

    @Schema(description = "Schema 版本", example = "1.0")
    private String schemaVersion = "1.0";

    @Schema(description = "有序组件列表")
    @Valid
    private List<ApplicationOrchestrationComponentVO> components = new ArrayList<>();

}
