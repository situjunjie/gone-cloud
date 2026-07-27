package cn.iocoder.yudao.module.project.controller.admin.sprint.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "管理后台 - 项目迭代创建 Request VO")
@Data
public class ProjectSprintCreateReqVO {

    @Schema(description = "项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "项目编号不能为空")
    private Long projectId;

    @Schema(description = "迭代名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "第 1 迭代")
    @NotBlank(message = "迭代名称不能为空")
    @Size(max = 50, message = "迭代名称长度不能超过 50 个字符")
    private String title;

    @Schema(description = "负责人用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "负责人用户编号不能为空")
    private Long ownerUserId;

    @Schema(description = "开始日期", example = "2026-07-27")
    private LocalDate startDate;

    @Schema(description = "结束日期", example = "2026-08-09")
    private LocalDate endDate;

    @Schema(description = "工时容量（小时）", example = "7.5")
    @DecimalMin(value = "0.0", message = "工时容量不能小于 0")
    @DecimalMax(value = "999999999.9", message = "工时容量不能超过 999999999.9")
    @Digits(integer = 9, fraction = 1, message = "工时容量最多保留 1 位小数")
    private BigDecimal capacityHours;

    @Schema(description = "迭代描述")
    @Size(max = 500, message = "迭代描述长度不能超过 500 个字符")
    private String description;

}
