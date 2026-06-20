package cn.iocoder.yudao.module.devops.dal.dataobject.artifactregistry;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.mybatis.core.type.EncryptTypeHandler;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.yudao.module.devops.enums.ArtifactRegistryAuthTypeEnum;
import cn.iocoder.yudao.module.devops.enums.ArtifactRegistryCheckStatusEnum;
import cn.iocoder.yudao.module.devops.enums.ArtifactRegistryProviderTypeEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * DevOps 制品仓库实例 DO。
 */
@TableName(value = "dev_artifact_registry", autoResultMap = true)
@KeySequence("dev_artifact_registry_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ArtifactRegistryDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 制品仓库名称。
     */
    private String name;
    /**
     * 提供方类型。枚举 {@link ArtifactRegistryProviderTypeEnum}。
     */
    private String providerType;
    /**
     * 服务地址。
     */
    private String serverUrl;
    /**
     * 认证类型。枚举 {@link ArtifactRegistryAuthTypeEnum}。
     */
    private String authType;
    /**
     * 用户名。
     */
    private String username;
    /**
     * 密码。
     */
    @TableField(typeHandler = EncryptTypeHandler.class)
    @ToString.Exclude
    private String password;
    /**
     * 密码掩码。
     */
    private String passwordMask;
    /**
     * 状态。枚举 {@link CommonStatusEnum}。
     */
    private Integer status;
    /**
     * 最近检测时间。
     */
    private LocalDateTime lastCheckTime;
    /**
     * 最近检测状态。枚举 {@link ArtifactRegistryCheckStatusEnum}。
     */
    private Integer lastCheckStatus;
    /**
     * 最近检测结果。
     */
    private String lastCheckMessage;
    /**
     * 备注。
     */
    private String remark;

}
