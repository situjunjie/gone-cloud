package cn.iocoder.yudao.module.project.controller.admin.worklog.vo;

import cn.iocoder.yudao.framework.common.validation.InEnum;
import cn.iocoder.yudao.module.project.enums.WorklogTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "管理后台 - 项目工时创建 Request VO")
@Data
public class WorklogCreateReqVO {

    @NotNull(message = "工作项编号不能为空")
    private Long workItemId;
    @NotBlank(message = "工时类型不能为空")
    @InEnum(value = WorklogTypeEnum.class, message = "工时类型不正确")
    private String type;
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

    @AssertTrue(message = "实际工时的工作日期不能为空")
    public boolean isActualWorkDateValid() {
        return !WorklogTypeEnum.ACTUAL.getValue().equals(type) || workDate != null;
    }

}
