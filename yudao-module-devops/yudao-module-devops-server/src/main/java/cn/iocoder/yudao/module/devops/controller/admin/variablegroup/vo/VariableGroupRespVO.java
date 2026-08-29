package cn.iocoder.yudao.module.devops.controller.admin.variablegroup.vo;

import lombok.Data;

@Data
public class VariableGroupRespVO {
    private Long id;
    private String name;
    private String scope;
    private Long appId;
    private String variables;
    private Integer status;
    private String remark;
}
