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
 * DevOps 应用 DO。
 */
@TableName("dev_application")
@KeySequence("dev_application_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ApplicationDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 应用标识。
     */
    private String appKey;
    /**
     * 应用名称。
     */
    private String name;
    /**
     * 应用描述。
     */
    private String description;
    /**
     * 应用图标。
     */
    private String icon;
    /**
     * 代码源编号。
     */
    private Long repositoryProviderId;
    /**
     * 代码库提供方类型。字典：dev_repo_provider_type。
     */
    private String repoProviderType;
    /**
     * 代码库唯一标识。
     */
    private String repoIdentifier;
    /**
     * 代码库地址。
     */
    private String repoUrl;
    /**
     * 默认主干分支。
     */
    private String defaultBranchName;
    /**
     * 负责人用户编号。
     */
    private Long ownerUserId;
    /**
     * 状态。枚举 {@link CommonStatusEnum}。
     */
    private Integer status;
    /**
     * 备注。
     */
    private String remark;

}
