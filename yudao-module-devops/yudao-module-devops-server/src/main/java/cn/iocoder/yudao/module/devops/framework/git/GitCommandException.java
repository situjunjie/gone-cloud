package cn.iocoder.yudao.module.devops.framework.git;

import lombok.Getter;

/**
 * Git 命令异常。
 */
@Getter
public class GitCommandException extends RuntimeException {

    private final String output;

    public GitCommandException(String message, String output) {
        super(message);
        this.output = output;
    }

}
