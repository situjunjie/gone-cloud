package cn.iocoder.yudao.module.devops.dal.dataobject.repositoryprovider;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.mybatis.core.type.EncryptTypeHandler;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.yudao.module.devops.enums.RepositoryProviderAuthTypeEnum;
import cn.iocoder.yudao.module.devops.enums.RepositoryProviderCheckStatusEnum;
import cn.iocoder.yudao.module.devops.enums.RepositoryProviderTypeEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * DevOps 代码源 DO。
 */
@TableName(value = "devops_repository_provider", autoResultMap = true)
@KeySequence("devops_repository_provider_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RepositoryProviderDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 代码源名称。
     */
    private String name;
    /**
     * 提供方类型。枚举 {@link RepositoryProviderTypeEnum}。
     */
    private String providerType;
    /**
     * 代码托管平台地址。
     */
    private String serverUrl;
    /**
     * API 地址。
     */
    private String apiUrl;
    /**
     * 认证类型。枚举 {@link RepositoryProviderAuthTypeEnum}。
     */
    private String authType;
    /**
     * 访问令牌。
     */
    @TableField(typeHandler = EncryptTypeHandler.class)
    @ToString.Exclude
    private String accessToken;
    /**
     * 令牌掩码。
     */
    private String tokenMask;
    /**
     * 授权范围。
     */
    private String scopes;
    /**
     * 状态。枚举 {@link CommonStatusEnum}。
     */
    private Integer status;
    /**
     * 最近检测时间。
     */
    private LocalDateTime lastCheckTime;
    /**
     * 最近检测状态。枚举 {@link RepositoryProviderCheckStatusEnum}。
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
