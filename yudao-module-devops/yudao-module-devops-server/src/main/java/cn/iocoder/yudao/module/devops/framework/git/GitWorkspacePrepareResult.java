package cn.iocoder.yudao.module.devops.framework.git;

import lombok.Data;

@Data
public class GitWorkspacePrepareResult {

    private String workspaceKey;
    /**
     * 兼容旧语义：工作区准备完成后的 HEAD，用于空变更发布时作为部署提交。
     */
    private String baseCommitSha;
    /**
     * 来源基线分支名称。
     */
    private String baseBranch;
    /**
     * 部署分支名称。
     */
    private String deployBranch;
    /**
     * 远端基线分支最新提交。
     */
    private String baseRemoteCommitSha;
    /**
     * 工作区准备完成后的 HEAD。
     */
    private String preparedCommitSha;
    /**
     * 远端部署分支是否已存在。
     */
    private Boolean deployBranchExisted;

}
