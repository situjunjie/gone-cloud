package cn.iocoder.yudao.module.devops.controller.admin.application.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - DevOps 应用分页 Request VO")
@Data
public class ApplicationPageReqVO extends PageParam {

    @Schema(description = "应用标识，模糊匹配", example = "gone-cloud")
    private String appKey;

    @Schema(description = "应用名称，模糊匹配", example = "Gone Cloud")
    private String name;

    @Schema(description = "代码库提供方类型，参见 dev_repo_provider_type", example = "GITLAB")
    private String repoProviderType;

    @Schema(description = "负责人用户编号", example = "1")
    private Long ownerUserId;

    @Schema(description = "状态，参见 common_status", example = "0")
    private Integer status;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
