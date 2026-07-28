package cn.iocoder.yudao.module.project.controller.admin.worklog.vo;

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

@Schema(description = "管理后台 - 项目工时更新 Request VO")
@Data
public class WorklogUpdateReqVO {

    @NotNull(message = "工时编号不能为空")
    private Long id;
    @NotNull(message = "工时数不能为空")
    @DecimalMin(value = "0", message = "工时数不能小于 0")
    @DecimalMax(value = "99999999.99", message = "工时数不能超过 99999999.99")
    @Digits(integer = 8, fraction = 2, message = "工时数最多 8 位整数和 2 位小数")
    private BigDecimal hours;
    @NotBlank(message = "工作类别不能为空")
    private String category;
    @Size(max = 1000, message = "工作描述长度不能超过 1000 个字符")
    private String description;
    private Long ownerUserId;
    private LocalDate workDate;

}
