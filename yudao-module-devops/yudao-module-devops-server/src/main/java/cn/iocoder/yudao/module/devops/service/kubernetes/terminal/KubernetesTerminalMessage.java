package cn.iocoder.yudao.module.devops.service.kubernetes.terminal;

import lombok.Data;

/**
 * Kubernetes Pod 终端 WebSocket 消息。
 */
@Data
public class KubernetesTerminalMessage {

    public static final String TYPE_INPUT = "input";
    public static final String TYPE_RESIZE = "resize";
    public static final String TYPE_CLOSE = "close";
    public static final String TYPE_OUTPUT = "output";
    public static final String TYPE_ERROR = "error";
    public static final String TYPE_CLOSED = "closed";

    private String type;

    private String data;

    private String message;

    private String reason;

    private Integer cols;

    private Integer rows;

}
