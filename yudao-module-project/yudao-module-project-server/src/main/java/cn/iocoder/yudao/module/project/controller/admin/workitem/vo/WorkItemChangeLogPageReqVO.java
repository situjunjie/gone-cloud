package cn.iocoder.yudao.module.project.controller.admin.workitem.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 工作项修改动态分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkItemChangeLogPageReqVO extends PageParam {

    @Schema(description = "工作项编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "工作项编号不能为空")
    private Long workItemId;

}
