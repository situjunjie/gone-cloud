package cn.iocoder.yudao.module.devops.controller.admin.variablegroup.vo;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.validation.InEnum;
import cn.iocoder.yudao.module.devops.enums.VariableGroupScopeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "管理后台 - DevOps 变量组新增/修改 Request VO")
public class VariableGroupSaveReqVO {
    private Long id;
    @NotBlank @Size(max = 64) private String name;
    @NotBlank @InEnum(VariableGroupScopeEnum.class) private String scope;
    private Long appId;
    private Long applicationEnvId;
    @NotBlank @Size(max = 20000) private String variables;
    @NotNull @InEnum(CommonStatusEnum.class) private Integer status;
    @Size(max = 512) private String remark;
}
