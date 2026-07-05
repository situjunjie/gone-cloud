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
    private Map<String, Object> condition;
    /**
     * 备注。
     */
    private String remark;

}
