package cn.iocoder.yudao.module.devops.dal.dataobject.host;

import cn.iocoder.yudao.framework.mybatis.core.type.EncryptTypeHandler;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * DevOps 环境主机 DO。
 */
@TableName(value = "dev_environment_host", autoResultMap = true)
@KeySequence("dev_environment_host_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class EnvironmentHostDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 环境编号。
     */
    private Long envId;
    /**
     * 主机标识。
     */
    private String hostKey;
    /**
     * 主机名称。
     */
    private String hostName;
    /**
     * SSH 主机地址。
     */
    private String host;
    /**
     * SSH 端口。
     */
    private Integer port;
    /**
     * SSH 用户名。
     */
    private String username;
    /**
     * 认证方式。
     */
    private String authType;
    /**
     * SSH 密码，加密存储。
     */
    @TableField(typeHandler = EncryptTypeHandler.class)
    @ToString.Exclude
    private String password;
    /**
     * SSH 私钥，加密存储。
     */
    @TableField(typeHandler = EncryptTypeHandler.class)
    @ToString.Exclude
    private String privateKey;
    /**
     * SSH 私钥口令，加密存储。
     */
    @TableField(typeHandler = EncryptTypeHandler.class)
    @ToString.Exclude
    private String passphrase;
    /**
     * 是否启用 sudo。
     */
    private Boolean sudoEnabled;
    /**
     * 主机描述。
     */
    private String description;
    /**
     * 状态。枚举 {@link cn.iocoder.yudao.framework.common.enums.CommonStatusEnum}。
     */
    private Integer status;
    /**
     * 最近检测状态。
     */
    private Integer lastCheckStatus;
    /**
     * 最近检测时间。
     */
    private LocalDateTime lastCheckTime;
    /**
     * 最近检测消息。
     */
    private String lastCheckMessage;
    /**
     * 备注。
     */
    private String remark;

}
