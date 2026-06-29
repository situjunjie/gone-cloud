package cn.iocoder.yudao.module.devops.controller.admin.application.vo;

import cn.iocoder.yudao.module.devops.dal.dataobject.environment.EnvironmentDO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.dromara.core.trans.anno.Trans;
import org.dromara.core.trans.constant.TransType;
import org.dromara.core.trans.vo.VO;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - DevOps 应用环境 Response VO")
@Data
public class ApplicationEnvRespVO implements VO {

    @Schema(description = "应用环境关系编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "应用编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long appId;

    @Schema(description = "环境编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    @Trans(type = TransType.SIMPLE, target = EnvironmentDO.class, fields = "envName", ref = "envName")
    private Long envId;
    @Schema(description = "环境名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "测试环境")
    private String envName;

    @Schema(description = "显示顺序", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    private Integer displayOrder;

    @Schema(description = "当前快照编号", example = "4096")
    private Long currentSnapshotId;

    @Schema(description = "状态，参见 common_status", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
