package cn.iocoder.yudao.module.devops.dal.dataobject.change;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * DevOps 变更环境关系 DO。
 */
@TableName("dev_change_env")
@KeySequence("dev_change_env_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ChangeEnvDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 变更编号。
     */
    private Long changeId;
    /**
     * 应用环境关系编号。
     */
    private Long applicationEnvId;
    /**
     * 挂载状态。字典：dev_change_env_mount_status。
     */
    private Integer mountStatus;
    private LocalDateTime mountedAt;
    private Long mountedBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime unmountedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long unmountedBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String unmountedReason;
    private Long lastPipelineRunId;
    private Long lastSnapshotId;
    /**
     * 最近合并状态。字典：dev_pipeline_merge_status。
     */
    private Integer lastMergeStatus;
    /**
     * 最近构建状态。字典：dev_pipeline_stage_status。
     */
    private Integer lastBuildStatus;
    /**
     * 最近测试状态。字典：dev_pipeline_stage_status。
     */
    private Integer lastTestStatus;
    /**
     * 最近部署状态。字典：dev_pipeline_stage_status。
     */
    private Integer lastDeployStatus;
    private String lastErrorMessage;
    /**
     * 审批状态。字典：dev_approval_status。
     */
    private Integer approvalStatus;
    private LocalDateTime approvedAt;
    private Long approvedBy;
    private Boolean includedInCurrentSnapshot;

}
