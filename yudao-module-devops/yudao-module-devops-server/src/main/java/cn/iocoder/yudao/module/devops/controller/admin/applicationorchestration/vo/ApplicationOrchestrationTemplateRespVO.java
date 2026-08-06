package cn.iocoder.yudao.module.devops.controller.admin.applicationorchestration.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationOrchestrationTemplateRespVO {

    private String templateKey;
    private String name;
    private String kind;
    private String defaultYaml;

}
