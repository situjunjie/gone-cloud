package cn.iocoder.yudao.module.devops.controller.admin.variablegroup.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "管理后台 - DevOps 变量组精简 Response VO")
public class VariableGroupSimpleRespVO {
    private Long id;
    private String name;
    private String scope;
    private Long appId;
    private Integer status;
    private String remark;
}
