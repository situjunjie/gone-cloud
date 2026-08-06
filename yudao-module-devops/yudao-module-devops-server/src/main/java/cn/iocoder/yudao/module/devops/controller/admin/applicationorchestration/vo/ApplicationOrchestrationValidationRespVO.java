package cn.iocoder.yudao.module.devops.controller.admin.applicationorchestration.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ApplicationOrchestrationValidationRespVO {

    private Boolean valid;
    private ApplicationOrchestrationConfigVO normalizedConfig;
    private List<ApplicationOrchestrationResourceSummaryRespVO> resources = new ArrayList<>();
    private List<ApplicationOrchestrationValidationMessageRespVO> errors = new ArrayList<>();

}
