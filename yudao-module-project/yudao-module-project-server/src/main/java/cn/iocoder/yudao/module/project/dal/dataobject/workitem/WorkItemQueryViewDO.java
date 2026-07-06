package cn.iocoder.yudao.module.project.dal.dataobject.workitem;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.yudao.module.project.enums.WorkItemQueryViewScopeEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 项目工作项查询视图 DO。
 */
@TableName(value = "proj_work_item_query_view", autoResultMap = true)
@KeySequence("proj_work_item_query_view_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class WorkItemQueryViewDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 视图名称。
     */
    private String name;
    /**
     * 视图编码，用于路由或查询参数。
     */
    private String code;
    /**
     * 视图级别。枚举 {@link WorkItemQueryViewScopeEnum}。
     */
    private String scope;
    /**
     * 用户编号。系统级视图固定为 0。
     */
    private Long userId;
    /**
     * 前端路由路径。
     */
    private String routePath;
    /**
     * 排序。
     */
    private Integer sort;
    /**
     * 查询条件 JSON。
     *
     * <p>
     * 保存工作项列表查询条件快照，结构兼容当前 {@code ProjectWorkItemPageReqVO}，后续新增查询字段无需变更本表结构。
     * </p>
     */
    @TableField(value = "condition_json", typeHandler = JacksonTypeHandler.class)
    private QueryCondition condition;
    /**
     * 视图配置 JSON。
     *
     * <p>
     * 保存列表展示状态，例如列配置、排序、分组、视图模式等。查询语义仍放在 {@link #condition}。
     * </p>
     */
    @TableField(value = "config_json", typeHandler = JacksonTypeHandler.class)
    private ViewConfig config;
    /**
     * 备注。
     */
    private String remark;

    /**
     * 工作项查询条件快照。
     */
    @Data
    public static class QueryCondition {

        private String keyword;

        private String category;

        private String status;

        private String priority;

        private Long assigneeUserId;

        private String creator;

        private Long projectId;

        private Long sprintId;

        private LocalDateTime[] plannedEndTime;

        private LocalDateTime[] createTime;

        private List<FieldCondition> conditions;

    }

    /**
     * 字段逻辑查询条件。
     */
    @Data
    public static class FieldCondition {

        private String field;

        private String operator;

        private String value;

        private String valueEnd;

        private List<String> values;

    }

    /**
     * 工作项视图展示配置。
     */
    @Data
    public static class ViewConfig {

        private String viewMode;

        private List<ColumnConfig> columns;

        private List<SortConfig> sorts;

        private String groupBy;

        /**
         * 前端保留扩展配置，避免新增轻量 UI 状态时频繁改表。
         */
        private Map<String, Object> extra;

    }

    /**
     * 列配置。
     */
    @Data
    public static class ColumnConfig {

        private String field;

        private String title;

        private Boolean visible;

        private Integer width;

        private Boolean fixed;

        private Integer order;

    }

    /**
     * 排序配置。
     */
    @Data
    public static class SortConfig {

        private String field;

        private String order;

    }

}
