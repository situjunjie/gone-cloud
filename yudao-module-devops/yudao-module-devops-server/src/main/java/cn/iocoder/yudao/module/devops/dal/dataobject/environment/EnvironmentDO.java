package cn.iocoder.yudao.module.devops.dal.dataobject.environment;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.mybatis.core.type.EncryptTypeHandler;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * DevOps 环境 DO。
 */
@TableName(value = "dev_environment", autoResultMap = true)
@KeySequence("dev_environment_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class EnvironmentDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 环境标识。
     */
    private String envKey;
    /**
     * 环境名称。
     */
    private String envName;
    /**
     * 环境阶段。字典：dev_env_stage。
     */
    private String envStage;
    /**
     * 基础设施类型。字典：dev_infra_type。
     */
    private String infraType;
    /**
     * 基础设施连接配置 JSON，加密存储。
     */
    @TableField(typeHandler = EncryptTypeHandler.class)
    @ToString.Exclude
    private String infraConfig;
    /**
     * 环境描述。
     */
    private String description;
    /**
     * 状态。枚举 {@link CommonStatusEnum}。
     */
    private Integer status;
    /**
     * 备注。
     */
    private String remark;

}
