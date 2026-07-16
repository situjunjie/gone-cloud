package cn.iocoder.yudao.module.project.controller.admin.workitem.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 工作项自定义字段保存 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkItemFieldSaveReqVO extends WorkItemFieldBaseReqVO {

    private Long id;

    @NotNull(message = "项目编号不能为空")
    private Long projectId;

}
