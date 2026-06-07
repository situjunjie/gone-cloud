package cn.iocoder.yudao.module.devops.controller.admin.change.vo;

import cn.iocoder.yudao.framework.common.validation.InEnum;
import cn.iocoder.yudao.module.devops.enums.ChangeCodeReviewStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - DevOps 变更分页 Request VO")
@Data
public class ChangePageReqVO extends PageParam {

    @Schema(description = "应用编号", example = "1")
    private Long appId;

    @Schema(description = "变更标识，模糊匹配", example = "GONE-1")
    private String changeKey;

    @Schema(description = "变更标题，模糊匹配", example = "发布网关")
    private String title;

    @Schema(description = "变更分支名称，模糊匹配", example = "feature/gone-1")
    private String branchName;

    @Schema(description = "负责人用户编号", example = "1")
    private Long ownerUserId;

    @Schema(description = "测试者用户编号", example = "1")
    private Long testerUserId;

    @Schema(description = "测试是否通过，0 未测试/未通过，1 已通过", example = "0")
    @Min(value = 0, message = "测试是否通过最小值为 0")
    @Max(value = 1, message = "测试是否通过最大值为 1")
    private Integer testPassed;

    @Schema(description = "代码审核者用户编号", example = "1")
    private Long codeReviewerUserId;

    @Schema(description = "代码审核状态，参见 dev_change_code_review_status", example = "0")
    @InEnum(ChangeCodeReviewStatusEnum.class)
    private Integer codeReviewStatus;

    @Schema(description = "状态，参见 dev_change_status", example = "0")
    private Integer status;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
