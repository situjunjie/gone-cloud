package cn.iocoder.yudao.module.project.controller.admin.workitem.vo;

import cn.iocoder.yudao.framework.common.validation.InEnum;
import cn.iocoder.yudao.module.project.enums.WorkItemQueryViewScopeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Schema(description = "管理后台 - 工作项查询视图创建/修改 Request VO")
@Data
public class WorkItemQueryViewSaveReqVO {

    @Schema(description = "视图编号，修改时必填", example = "1024")
    private Long id;

    @Schema(description = "视图名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "我的待办")
    @NotBlank(message = "视图名称不能为空")
    @Size(max = 128, message = "视图名称长度不能超过 128 个字符")
    private String name;

    @Schema(description = "视图编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "my-todo")
    @NotBlank(message = "视图编码不能为空")
    @Size(max = 64, message = "视图编码长度不能超过 64 个字符")
    private String code;

    @Schema(description = "视图级别，参见 project_work_item_query_view_scope", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "USER")
    @NotBlank(message = "视图级别不能为空")
    @InEnum(WorkItemQueryViewScopeEnum.class)
    private String scope;

    @Schema(description = "前端路由路径", example = "/project/work-item?view=my-todo")
    @Size(max = 256, message = "前端路由路径长度不能超过 256 个字符")
    private String routePath;

    @Schema(description = "排序", example = "0")
    private Integer sort;

    @Schema(description = "查询条件 JSON，结构兼容工作项分页查询条件", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "查询条件不能为空")
    private Map<String, Object> condition;

    @Schema(description = "视图配置 JSON，用于保存列、排序、分组、展示模式等列表状态")
    private Map<String, Object> config;

    @Schema(description = "备注")
    @Size(max = 512, message = "备注长度不能超过 512 个字符")
    private String remark;

}
