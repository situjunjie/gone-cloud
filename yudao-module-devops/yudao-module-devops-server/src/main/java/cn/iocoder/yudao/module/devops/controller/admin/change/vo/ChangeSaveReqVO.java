package cn.iocoder.yudao.module.devops.controller.admin.change.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - DevOps 变更创建/修改 Request VO")
@Data
public class ChangeSaveReqVO {

    @Schema(description = "变更编号，修改时必填", example = "1024")
    private Long id;

    @Schema(description = "应用编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "应用编号不能为空")
    private Long appId;

    @Schema(description = "变更标识", requiredMode = Schema.RequiredMode.REQUIRED, example = "GONE-1")
    @NotBlank(message = "变更标识不能为空")
    @Size(max = 64, message = "变更标识长度不能超过 64 个字符")
    private String changeKey;

    @Schema(description = "变更标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "发布网关")
    @NotBlank(message = "变更标题不能为空")
    @Size(max = 200, message = "变更标题长度不能超过 200 个字符")
    private String title;

    @Schema(description = "变更描述")
    @Size(max = 1000, message = "变更描述长度不能超过 1000 个字符")
    private String description;

    @Schema(description = "变更分支名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "feature/gone-1")
    @NotBlank(message = "变更分支名称不能为空")
    @Size(max = 128, message = "变更分支名称长度不能超过 128 个字符")
    private String branchName;

    @Schema(description = "来源基线分支名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "master")
    @NotBlank(message = "来源基线分支名称不能为空")
    @Size(max = 64, message = "来源基线分支名称长度不能超过 64 个字符")
    private String sourceBaseBranchName;

    @Schema(description = "负责人用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "负责人用户编号不能为空")
    private Long ownerUserId;

    @Schema(description = "备注")
    @Size(max = 512, message = "备注长度不能超过 512 个字符")
    private String remark;

}
