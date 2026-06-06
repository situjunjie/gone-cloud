package cn.iocoder.yudao.module.devops.controller.admin.repositoryprovider.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - DevOps 代码源分页 Request VO")
@Data
public class RepositoryProviderPageReqVO extends PageParam {

    @Schema(description = "代码源名称，模糊匹配", example = "公司 GitLab")
    private String name;

    @Schema(description = "提供方类型，参见 dev_repo_provider_type", example = "GITLAB")
    private String providerType;

    @Schema(description = "认证类型，参见 dev_repo_provider_auth_type", example = "ACCESS_TOKEN")
    private String authType;

    @Schema(description = "状态，参见 common_status", example = "0")
    private Integer status;

    @Schema(description = "最近检测状态，0 成功 1 失败", example = "0")
    private Integer lastCheckStatus;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
