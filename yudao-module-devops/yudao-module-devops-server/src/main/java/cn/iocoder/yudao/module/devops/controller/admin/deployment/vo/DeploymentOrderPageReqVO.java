package cn.iocoder.yudao.module.devops.controller.admin.deployment.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - DevOps 部署单分页 Request VO")
@Data
public class DeploymentOrderPageReqVO extends PageParam {

    @Schema(description = "流水线运行编号", example = "800")
    private Long pipelineRunId;

    @Schema(description = "应用编号", example = "1")
    private Long appId;

    @Schema(description = "应用环境关系编号", example = "100")
    private Long applicationEnvId;

    @Schema(description = "环境编号", example = "10")
    private Long environmentId;

    @Schema(description = "部署状态", example = "RUNNING")
    private String deployStatus;

    @Schema(description = "工作负载名称，模糊匹配", example = "gone-server")
    private String workloadName;

    @Schema(description = "触发时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] triggeredAt;

}
