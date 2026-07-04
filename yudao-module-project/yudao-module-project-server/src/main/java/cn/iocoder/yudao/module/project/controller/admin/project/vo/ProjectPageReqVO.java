package cn.iocoder.yudao.module.project.controller.admin.project.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.validation.InEnum;
import cn.iocoder.yudao.module.project.enums.ProjectStatusEnum;
import cn.iocoder.yudao.module.project.enums.ProjectVisibilityEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 项目分页 Request VO")
@Data
public class ProjectPageReqVO extends PageParam {

    @Schema(description = "关键字，匹配项目编号或项目名称", example = "RD")
    private String keyword;

    @Schema(description = "公开性，参见 project_visibility", example = "PUBLIC")
    @InEnum(ProjectVisibilityEnum.class)
    private String visibility;

    @Schema(description = "状态，参见 project_status", example = "ACTIVE")
    @InEnum(ProjectStatusEnum.class)
    private String status;

    @Schema(description = "负责人用户编号", example = "1")
    private Long ownerUserId;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
