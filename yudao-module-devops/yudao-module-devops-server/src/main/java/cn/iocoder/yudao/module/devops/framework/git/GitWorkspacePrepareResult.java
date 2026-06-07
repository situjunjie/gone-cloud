package cn.iocoder.yudao.module.devops.framework.git;

import lombok.Data;

@Data
public class GitWorkspacePrepareResult {

    private String workspaceKey;
    private String baseCommitSha;

}
