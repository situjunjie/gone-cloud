package cn.iocoder.yudao.module.project.dal.dataobject.workitem;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.yudao.module.project.enums.ProjectWorkItemCategoryEnum;
import cn.iocoder.yudao.module.project.enums.ProjectWorkItemPriorityEnum;
import cn.iocoder.yudao.module.project.enums.ProjectWorkItemStatusEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 项目工作项 DO。
 */
@TableName("proj_work_item")
@KeySequence("proj_work_item_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ProjectWorkItemDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 工作项编号展示值。
     */
    private String workItemNo;
    /**
     * 工作项标题。
     */
    private String title;
    /**
     * 工作项描述。
     */
    private String description;
    /**
     * 工作项分类。枚举 {@link ProjectWorkItemCategoryEnum}。
     */
    private String category;
    /**
     * 优先级。枚举 {@link ProjectWorkItemPriorityEnum}。
     */
    private String priority;
    /**
     * 状态。枚举 {@link ProjectWorkItemStatusEnum}。
     */
    private String status;
    /**
     * 负责人用户编号。
     */
    private Long assigneeUserId;
    /**
     * 归属项目编号。
     */
    private Long projectId;
    /**
     * 归属迭代编号。
     */
    private Long sprintId;
    /**
     * 计划开始时间。
     */
    private LocalDateTime plannedStartTime;
    /**
     * 计划完成时间。
     */
    private LocalDateTime plannedEndTime;
    /**
     * 预估工时物化合计，仅由工时事务维护。
     */
    private BigDecimal estimatedHours;
    /**
     * 实际工时物化合计，仅由工时事务维护。
     */
    private BigDecimal actualHours;
    /**
     * 父工作项编号。
     */
    private Long parentWorkItemId;
    /**
     * 备注。
     */
    private String remark;

}
