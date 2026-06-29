package cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo;

import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.environment.EnvironmentDO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.dromara.core.trans.anno.Trans;
import org.dromara.core.trans.constant.TransType;
import org.dromara.core.trans.vo.VO;

@Schema(description = "管理后台 - DevOps 流水线定义 Response VO")
@Data
public class PipelineDefinitionRespVO implements VO {

    @Schema(description = "流水线定义编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "流水线名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "测试环境流水线")
    private String name;

    @Schema(description = "流水线标识", requiredMode = Schema.RequiredMode.REQUIRED, example = "app-env-1")
    private String definitionKey;

    @Schema(description = "应用编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @Trans(type = TransType.SIMPLE, target = ApplicationDO.class, fields = "name", ref = "appName")
    private Long appId;
    @Schema(description = "应用名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "Gone Cloud")
    private String appName;

    @Schema(description = "应用环境关系编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    private Long applicationEnvId;

    @Schema(description = "环境编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    @Trans(type = TransType.SIMPLE, target = EnvironmentDO.class, fields = { "envKey", "envName" },
            refs = { "envKey", "envName" })
    private Long envId;
    @Schema(description = "环境标识", requiredMode = Schema.RequiredMode.REQUIRED, example = "test")
    private String envKey;
    @Schema(description = "环境名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "测试环境")
    private String envName;

    @Schema(description = "状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer status;

    @Schema(description = "草稿版本编号")
    private Long draftVersionId;

    @Schema(description = "已发布版本编号")
    private Long publishedVersionId;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "草稿版本")
    private PipelineDefinitionVersionRespVO draftVersion;

    @Schema(description = "已发布版本")
    private PipelineDefinitionVersionRespVO publishedVersion;

}
