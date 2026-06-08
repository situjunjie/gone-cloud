package cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "管理后台 - DevOps Jenkins 统一回调 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PipelineJenkinsCallbackRespVO {

    @Schema(description = "是否接收")
    private Boolean accepted;

    @Schema(description = "是否重复回调")
    private Boolean duplicate;

}
