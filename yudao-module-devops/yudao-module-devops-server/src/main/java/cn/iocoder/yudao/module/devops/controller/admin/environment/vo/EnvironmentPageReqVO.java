package cn.iocoder.yudao.module.devops.controller.admin.environment.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - DevOps 环境分页 Request VO")
@Data
public class EnvironmentPageReqVO extends PageParam {

    @Schema(description = "环境标识，模糊匹配", example = "test")
    private String envKey;

    @Schema(description = "环境名称，模糊匹配", example = "测试环境")
    private String envName;

    @Schema(description = "环境阶段，参见 dev_env_stage", example = "TEST")
    private String envStage;

    @Schema(description = "基础设施类型，参见 dev_infra_type", example = "HOST")
    private String infraType;

    @Schema(description = "状态，参见 common_status", example = "0")
    private Integer status;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
