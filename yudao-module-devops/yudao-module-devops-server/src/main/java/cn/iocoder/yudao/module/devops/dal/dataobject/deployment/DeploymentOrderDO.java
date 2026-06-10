package cn.iocoder.yudao.module.devops.dal.dataobject.deployment;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * DevOps 部署单 DO。
 */
@TableName("dev_deployment_order")
@KeySequence("dev_deployment_order_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DeploymentOrderDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 流水线运行编号。
     */
    private Long pipelineRunId;
    /**
     * 流水线运行日志编号。
     */
    private Long pipelineRunLogId;
    /**
     * 流水线节点编号。
     */
    private String nodeId;
    /**
     * 流水线节点类型。
     */
    private String nodeType;
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
     * 环境编号。
     */
    private Long environmentId;
    /**
     * 基础设施类型。
     */
    private String infraType;
    /**
     * 部署类型。
     */
    private String deployType;
    /**
     * 部署状态。
     */
    private String deployStatus;
    /**
     * 当前执行次数。
     */
    private Integer attempt;
    /**
     * 当前执行阶段。
     */
    private String currentStage;
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
     * Kubernetes Namespace。
     */
    private String namespace;
    /**
     * 工作负载类型。
     */
    private String workloadKind;
    /**
     * 工作负载名称。
     */
    private String workloadName;
    /**
     * 容器名称。
     */
    private String containerName;
    /**
     * 目标镜像。
     */
    private String image;
    /**
     * 目标副本数。
     */
    private Integer replicas;
    /**
     * 上一个镜像。
     */
    private String previousImage;
    /**
     * 上一个副本数。
     */
    private Integer previousReplicas;
    /**
     * 上一个 Deployment revision。
     */
    private String previousRevision;
    /**
     * 目标 Deployment revision。
     */
    private String targetRevision;
    /**
     * Deployment UID。
     */
    private String workloadUid;
    /**
     * Deployment generation。
     */
    private Long workloadGeneration;
    /**
     * 配置快照 JSON。
     */
    private String configJson;
    /**
     * 结果 JSON。
     */
    private String resultJson;
    /**
     * 错误信息。
     */
    private String errorMessage;

}
