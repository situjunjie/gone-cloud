package cn.iocoder.yudao.module.devops.dal.dataobject.change;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * DevOps 变更 DO。
 */
@TableName("dev_change")
@KeySequence("dev_change_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ChangeDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 应用编号。
     */
    private Long appId;
    /**
     * 变更标识。
     */
    private String changeKey;
    /**
     * 变更标题。
     */
    private String title;
    /**
     * 变更描述。
     */
    private String description;
    /**
     * 变更分支名称。
     */
    private String branchName;
    /**
     * 来源基线分支名称。
     */
    private String sourceBaseBranchName;
    /**
     * 负责人用户编号。
     */
    private Long ownerUserId;
    /**
     * 状态。字典：dev_change_status。
     */
    private Integer status;
    /**
     * 最新提交 SHA。
     */
    private String latestCommitSha;
    /**
     * 最新提交信息。
     */
    private String latestCommitMessage;
    /**
     * 最新提交时间。
     */
    private LocalDateTime latestCommitAt;
    /**
     * 发布时间。
     */
    private LocalDateTime releasedAt;
    /**
     * 合并回主干时间。
     */
    private LocalDateTime mergedToMasterAt;
    /**
     * 废弃时间。
     */
    private LocalDateTime discardedAt;
    /**
     * 废弃原因。
     */
    private String discardReason;
    /**
     * 备注。
     */
    private String remark;

}
