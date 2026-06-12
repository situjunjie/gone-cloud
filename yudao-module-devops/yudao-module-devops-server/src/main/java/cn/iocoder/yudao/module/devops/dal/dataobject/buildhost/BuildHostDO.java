package cn.iocoder.yudao.module.devops.dal.dataobject.buildhost;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.mybatis.core.type.EncryptTypeHandler;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.yudao.module.devops.enums.BuildHostTypeEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 构建主机 DO。
 *
 * <p>流水线 BUILD 类节点执行 shell 的运行机：{@code type=SSH} 为远程专用构建机（主路径），
 * {@code type=LOCAL} 为平台本机退化特例。与「部署目标环境」{@code dev_environment} 语义不同，
 * 构建主机不接收应用部署，只承载构建脚本执行。
 *
 * <p>SSH 登录敏感字段（{@code password} / {@code privateKey} / {@code passphrase}）通过
 * {@link EncryptTypeHandler} 加密落库，并 {@link ToString.Exclude} 排除，绝不明文落库、绝不进日志。
 */
@TableName(value = "dev_build_host", autoResultMap = true)
@KeySequence("dev_build_host_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class BuildHostDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 主机名称。
     */
    private String name;
    /**
     * 主机类型。枚举 {@link BuildHostTypeEnum}（LOCAL / SSH）。
     */
    private String type;
    /**
     * 主机地址。SSH 为目标 host，LOCAL 可为 localhost。
     */
    private String host;
    /**
     * SSH 端口。LOCAL 可为空。
     */
    private Integer port;
    /**
     * 登录用户名。
     */
    private String username;
    /**
     * SSH 登录密码，加密存储。仅 SSH 且密码认证时使用。
     */
    @TableField(typeHandler = EncryptTypeHandler.class)
    @ToString.Exclude
    private String password;
    /**
     * SSH 私钥，加密存储。仅 SSH 且密钥认证时使用。
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
     * 平台凭据存储引用 key。预留：指向未来平台凭据中心（registry / git token / ssh key）。
     */
    private String credentialRef;
    /**
     * 主机标签，逗号分隔。供 {@code BuildHostSelector} 按 label 调度。
     */
    private String labels;
    /**
     * 构建工作根目录。run 在此目录下创建独立子目录。
     */
    private String workspaceRoot;
    /**
     * 最大并发构建数。供容量调度限流。
     */
    private Integer maxConcurrency;
    /**
     * 是否启用。
     */
    private Boolean enabled;
    /**
     * 状态。枚举 {@link CommonStatusEnum}。
     */
    private Integer status;
    /**
     * 备注。
     */
    private String remark;

}
