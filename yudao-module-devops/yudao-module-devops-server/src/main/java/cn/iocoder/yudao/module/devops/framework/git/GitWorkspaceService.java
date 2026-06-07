package cn.iocoder.yudao.module.devops.framework.git;

import java.util.List;

public interface GitWorkspaceService {

    GitWorkspacePrepareResult prepareWorkspace(Long pipelineRunId, String repoUrl, String accessToken,
                                               String baseBranch, String deployBranch);

    String resolveRemoteBranchCommit(String workspaceKey, String branchName);

    GitMergeResult merge(String workspaceKey, String commitSha, String message);

    GitConflictContent readConflictContent(String workspaceKey, GitConflictDescriptor conflict);

    String continueMerge(String workspaceKey, List<GitFileResolution> resolutions, String message);

    void abortMerge(String workspaceKey);

    void pushDeployBranch(String workspaceKey, String deployBranch);

    void cleanup(String workspaceKey);

}
