package cn.iocoder.yudao.module.project.controller.admin.worklog.vo;

import cn.iocoder.yudao.framework.common.validation.InEnum;
import cn.iocoder.yudao.module.project.enums.WorklogTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class WorklogFilterReqVO {

    @Schema(description = "项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "项目编号不能为空")
    private Long projectId;
    @Schema(description = "迭代编号", example = "2048")
    private Long sprintId;
    @Schema(description = "工作项编号", example = "4096")
    private Long workItemId;
    @Schema(description = "工时类型：ESTIMATED、ACTUAL")
    @InEnum(value = WorklogTypeEnum.class, message = "工时类型不正确")
    private String type;
    @Schema(description = "工作类别，参见 project_worklog_category")
    private String category;
    @Schema(description = "工时负责人用户编号", example = "1")
    private Long ownerUserId;
    @Schema(description = "工作日期开始")
    private LocalDate workDateStart;
    @Schema(description = "工作日期结束")
    private LocalDate workDateEnd;

    @AssertTrue(message = "工作日期结束不能早于开始日期")
    public boolean isWorkDateRangeValid() {
        return workDateStart == null || workDateEnd == null || !workDateEnd.isBefore(workDateStart);
    }

}
