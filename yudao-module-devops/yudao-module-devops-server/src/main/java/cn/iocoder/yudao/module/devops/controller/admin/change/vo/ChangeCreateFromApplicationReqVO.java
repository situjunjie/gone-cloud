package cn.iocoder.yudao.module.devops.controller.admin.change.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - DevOps 应用详情页创建变更 Request VO")
@Data
public class ChangeCreateFromApplicationReqVO {

    @Schema(description = "应用编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "应用编号不能为空")
    private Long appId;

    @Schema(description = "变更标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "发布网关")
    @NotBlank(message = "变更标题不能为空")
    @Size(max = 200, message = "变更标题长度不能超过 200 个字符")
    private String title;

    @Schema(description = "变更分支名称中间部分，后端会生成 feat/{branchSlug}-{openTimestamp}", requiredMode = Schema.RequiredMode.REQUIRED, example = "login-page")
    @NotBlank(message = "变更分支名称不能为空")
    @Size(max = 100, message = "变更分支名称长度不能超过 100 个字符")
    private String branchSlug;

    @Schema(description = "打开新建变更弹窗时的时间戳", requiredMode = Schema.RequiredMode.REQUIRED, example = "1717651234567")
    @NotNull(message = "打开时间戳不能为空")
    @Positive(message = "打开时间戳必须大于 0")
    private Long openTimestamp;

}
