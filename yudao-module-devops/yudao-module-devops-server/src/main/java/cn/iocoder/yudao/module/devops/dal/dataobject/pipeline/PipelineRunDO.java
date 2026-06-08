package cn.iocoder.yudao.module.devops.dal.dataobject.pipeline;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * DevOps 流水线运行 DO。
 */
@TableName("dev_pipeline_run")
@KeySequence("dev_pipeline_run_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PipelineRunDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 流水线定义编号。
     */
    private Long definitionId;
    /**
     * 流水线定义版本编号。
     */
    private Long definitionVersionId;
    /**
     * 应用编号。
     */
    private Long appId;
    /**
     * 应用环境关系编号。
     */
    private Long applicationEnvId;
    /**
     * 变更编号。
     */
    private Long changeId;
    /**
     * 变更环境关系编号。
     */
    private Long changeEnvId;
    /**
     * 部署分支名称。
     */
    private String branchName;
    /**
     * 提交 SHA。
     */
    private String commitSha;
    /**
     * 本次发布提交的变更快照 JSON。
     */
    private String changeSnapshotJson;
    /**
     * 运行状态。字典：dev_pipeline_run_status。
     */
    private Integer runStatus;
    /**
     * 触发来源。
     */
    private String triggerType;
    /**
     * 触发人用户编号。
     */
    private Long triggerUserId;
    /**
     * 触发时间。
     */
    private LocalDateTime triggeredAt;
    /**
     * 开始时间。
     */
    private LocalDateTime startedAt;
    /**
     * 结束时间。
     */
    private LocalDateTime finishedAt;
    /**
     * Jenkins 队列编号。
     */
    private String jenkinsQueueId;
    /**
     * Jenkins 构建编号。
     */
    private String jenkinsBuildNumber;
    /**
     * 错误信息。
     */
    private String errorMessage;

}
