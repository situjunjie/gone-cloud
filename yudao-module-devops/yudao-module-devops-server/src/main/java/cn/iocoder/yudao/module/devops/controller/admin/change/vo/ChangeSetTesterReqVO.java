package cn.iocoder.yudao.module.devops.controller.admin.change.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - DevOps 变更设置测试者 Request VO")
@Data
public class ChangeSetTesterReqVO {

    @Schema(description = "变更编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "变更编号不能为空")
    private Long id;

    @Schema(description = "测试者用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "测试者用户编号不能为空")
    private Long testerUserId;

}
