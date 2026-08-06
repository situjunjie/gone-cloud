package cn.iocoder.yudao.module.devops.controller.admin.applicationorchestration.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationOrchestrationResourceSummaryRespVO {

    private String componentId;
    private Integer applyOrder;
    private String apiVersion;
    private String kind;
    private String name;

}
