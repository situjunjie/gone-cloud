package cn.iocoder.yudao.module.system.controller.admin.user.vo.favorite;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 用户收藏新增 Request VO")
@Data
public class UserFavoriteCreateReqVO {

    @Schema(description = "业务类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "DEVOPS_APPLICATION")
    @NotBlank(message = "业务类型不能为空")
    @Size(max = 64, message = "业务类型长度不能超过 64 个字符")
    private String bizType;

    @Schema(description = "业务对象编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "业务对象编号不能为空")
    private Long bizId;

}
