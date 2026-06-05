package cn.iocoder.yudao.module.devops.dal.dataobject.application;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * DevOps 应用环境关系 DO。
 */
@TableName("dev_application_env")
@KeySequence("dev_application_env_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ApplicationEnvDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 应用编号。
     */
    private Long appId;
    /**
     * 环境编号。
     */
    private Long envId;
    /**
     * 显示顺序。
     */
    private Integer displayOrder;
    /**
     * 部署分支名称模式。
     */
    private String deployBranchNamePattern;
    /**
     * 流水线定义编号。
     */
    private Long pipelineDefinitionId;
    /**
     * 是否需要审批。
     */
    private Boolean approvalRequired;
    /**
     * 审批配置 JSON。
     */
    private String approvalConfigJson;
    /**
     * 当前快照编号。
     */
    private Long currentSnapshotId;
    /**
     * 状态。枚举 {@link CommonStatusEnum}。
     */
    private Integer status;
    /**
     * 备注。
     */
    private String remark;

}
