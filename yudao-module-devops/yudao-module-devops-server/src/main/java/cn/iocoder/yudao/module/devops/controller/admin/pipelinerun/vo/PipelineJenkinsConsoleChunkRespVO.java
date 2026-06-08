package cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - DevOps Jenkins Console 分片 Response VO")
@Data
public class PipelineJenkinsConsoleChunkRespVO {

    @Schema(description = "Console 文本增量")
    private String text;

    @Schema(description = "下一次读取 offset")
    private Long offset;

    @Schema(description = "Jenkins 是否还有后续数据")
    private Boolean hasMore;

}
