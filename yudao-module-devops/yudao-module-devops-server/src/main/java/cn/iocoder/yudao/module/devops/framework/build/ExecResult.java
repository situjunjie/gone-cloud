package cn.iocoder.yudao.module.devops.framework.build;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 构建脚本执行结果。
 */
@Data
@AllArgsConstructor
public class ExecResult {

    /**
     * 进程退出码。
     */
    private int exitCode;

    /**
     * 错误信息,可空。成功时通常为 null;失败/超时时给出已脱敏的简要原因。
     */
    private String errorMessage;

    /**
     * 退出码为 0 视为成功。
     */
    public boolean isSuccess() {
        return exitCode == 0;
    }

    public static ExecResult success() {
        return new ExecResult(0, null);
    }

    public static ExecResult failure(int exitCode, String errorMessage) {
        return new ExecResult(exitCode, errorMessage);
    }

}
