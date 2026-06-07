package cn.iocoder.yudao.module.devops.framework.git;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GitCommandResult {

    private int exitCode;
    private String output;

    public boolean isSuccess() {
        return exitCode == 0;
    }

}
