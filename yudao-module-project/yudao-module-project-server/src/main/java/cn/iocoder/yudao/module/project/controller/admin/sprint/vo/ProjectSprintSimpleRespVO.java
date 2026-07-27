package cn.iocoder.yudao.module.project.controller.admin.sprint.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 项目迭代精简信息 Response VO")
@Data
public class ProjectSprintSimpleRespVO {

    @Schema(description = "迭代编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "迭代名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "第 1 迭代")
    private String name;

}
