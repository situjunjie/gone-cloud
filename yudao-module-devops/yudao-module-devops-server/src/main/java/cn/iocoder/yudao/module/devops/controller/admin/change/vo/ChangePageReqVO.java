package cn.iocoder.yudao.module.devops.controller.admin.change.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
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

    @Schema(description = "状态，参见 dev_change_status", example = "0")
    private Integer status;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
