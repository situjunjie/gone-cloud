package cn.iocoder.yudao.module.devops.framework.build;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * SSH 构建目标。
 *
 * <p>{@link SshBuildExecutor} 借此连接远程构建机。由 {@code PipelineExecutionEngine} 从选中的
 * {@code BuildHostDO} 映射而来(host / port / username + 加密读出后的明文凭据),随
 * {@link ExecContext} 传入。{@code LocalBuildExecutor} 忽略本字段。
 *
 * <p>认证优先级:存在 {@link #privateKey} 时走密钥认证({@link #passphrase} 可选),
 * 否则走 {@link #password} 密码认证。
 *
 * <p>注意:{@link #password} / {@link #privateKey} / {@link #passphrase} 为敏感凭据,
 * 已从 {@link #toString()} 中排除,日志与异常绝不得回显其值。
 */
@Getter
@Builder
@ToString
public class SshTarget {

    /**
     * 目标主机地址。
     */
    private final String host;

    /**
     * SSH 端口。为空时由 {@link SshBuildExecutor} 按 22 兜底。
     */
    private final Integer port;

    /**
     * 登录用户名。
     */
    private final String username;

    /**
     * 登录密码(密码认证)。敏感字段。
     */
    @ToString.Exclude
    private final String password;

    /**
     * 登录私钥 PEM/OpenSSH 文本(密钥认证)。敏感字段。
     */
    @ToString.Exclude
    private final String privateKey;

    /**
     * 私钥口令(密钥认证,可选)。敏感字段。
     */
    @ToString.Exclude
    private final String passphrase;

}
