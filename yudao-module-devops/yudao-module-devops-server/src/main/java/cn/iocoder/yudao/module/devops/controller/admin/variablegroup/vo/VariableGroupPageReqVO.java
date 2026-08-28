package cn.iocoder.yudao.module.devops.controller.admin.variablegroup.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;

@Data
public class VariableGroupPageReqVO extends PageParam {
    private String name;
    private String scope;
    private Long appId;
    private Long applicationEnvId;
    private Integer status;
}
