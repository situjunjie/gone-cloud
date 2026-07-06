package cn.iocoder.yudao.module.project.dal.dataobject.project;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.yudao.module.project.enums.ProjectSprintStatusEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 项目迭代 DO。
 */
@TableName("proj_sprint")
@KeySequence("proj_sprint_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ProjectSprintDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 归属项目编号。
     */
    private Long projectId;
    /**
     * 迭代标题。
     */
    private String title;
    /**
     * 状态。枚举 {@link ProjectSprintStatusEnum}。
     */
    private String status;
    /**
     * 负责人用户编号。
     */
    private Long ownerUserId;
    /**
     * 计划开始时间。
     */
    private LocalDateTime plannedStartTime;
    /**
     * 计划结束时间。
     */
    private LocalDateTime plannedEndTime;
    /**
     * 实际开始时间。
     */
    private LocalDateTime actualStartTime;
    /**
     * 实际结束时间。
     */
    private LocalDateTime actualEndTime;

}
