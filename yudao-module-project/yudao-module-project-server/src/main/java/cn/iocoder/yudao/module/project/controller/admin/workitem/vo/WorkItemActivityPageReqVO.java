package cn.iocoder.yudao.module.project.controller.admin.workitem.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 工作项动态分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkItemActivityPageReqVO extends PageParam {

    public static final int MAX_LOOKBACK_COUNT = 200;

    @Schema(description = "工作项编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "工作项编号不能为空")
    private Long workItemId;

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "工作项动态最多查询前 200 条")
    public boolean isLookbackValid() {
        return getPageNo() != null && getPageSize() != null
                && (long) getPageNo() * getPageSize() <= MAX_LOOKBACK_COUNT;
    }

}
