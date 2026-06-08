package cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - DevOps Jenkins 统一回调 Request VO")
@Data
public class PipelineJenkinsCallbackReqVO {

    @Schema(description = "动作：STARTED / COMPLETED / FAILED", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "动作不能为空")
    private String action;

    @Schema(description = "事件编号，用于幂等")
    private String eventId;

    @Schema(description = "流水线版本编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "流水线版本编号不能为空")
    private Long pipelineVersionId;

    @Schema(description = "节点编号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "节点编号不能为空")
    private String nodeId;

    @Schema(description = "节点类型", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "节点类型不能为空")
    private String nodeType;

    @Schema(description = "节点名称")
    private String nodeName;

    @Schema(description = "Jenkins Job 名称")
    private String jenkinsJobName;

    @Schema(description = "Jenkins 构建编号")
    private String jenkinsBuildNumber;

    @Schema(description = "Jenkins 构建地址")
    private String jenkinsBuildUrl;

    @Schema(description = "Jenkins input 编号")
    private String inputId;

    @Schema(description = "提交 SHA")
    private String commitSha;

    @Schema(description = "回调时间")
    private String timestamp;

    @Schema(description = "摘要")
    private String message;

    @Schema(description = "产物列表")
    private List<Map<String, Object>> artifacts;

}
