package cn.iocoder.yudao.module.project.controller.admin.workitem.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Schema(description = "管理后台 - 工作项查询视图 Response VO")
@Data
public class WorkItemQueryViewRespVO {

    @Schema(description = "视图编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "视图名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "我的待办")
    private String name;

    @Schema(description = "视图编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "my-todo")
    private String code;

    @Schema(description = "视图级别，参见 project_work_item_query_view_scope", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "USER")
    private String scope;

    @Schema(description = "用户编号，系统级视图固定为 0", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long userId;

    @Schema(description = "前端路由路径", example = "/project/work-item?view=my-todo")
    private String routePath;

    @Schema(description = "排序", example = "0")
    private Integer sort;

    @Schema(description = "查询条件 JSON，结构兼容工作项分页查询条件")
    private Map<String, Object> condition;

    @Schema(description = "视图配置 JSON，用于保存列、排序、分组、展示模式等列表状态")
    private Map<String, Object> config;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

    @Schema(description = "更新时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime updateTime;

}
