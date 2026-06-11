package cn.iocoder.yudao.module.devops.controller.admin.offlineimage.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - DevOps 离线镜像包分页 Request VO")
@Data
public class OfflineImagePackagePageReqVO extends PageParam {

    @Schema(description = "流水线运行编号", example = "800")
    private Long pipelineRunId;

    @Schema(description = "镜像名称，模糊匹配", example = "gone/yudao-gateway")
    private String imageName;

    @Schema(description = "状态（0 打包中 1 就绪 2 失败）", example = "1")
    private Integer status;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
