package cn.iocoder.yudao.module.devops.framework.git;

import java.nio.file.Path;
import java.util.List;

public interface GitWorkspaceService {

    GitWorkspacePrepareResult prepareWorkspace(Long pipelineRunId, String repoUrl, String accessToken,
                                               String baseBranch, String deployBranch);

    GitWorkspacePrepareResult prepareWorkspace(String workspaceKey, Path workspace, String repoUrl, String accessToken,
                                               String baseBranch, String deployBranch);

    void registerExternalWorkspace(String workspaceKey, Path workspace);

    String resolveRemoteBranchCommit(String workspaceKey, String branchName);

    GitMergeResult merge(String workspaceKey, String commitSha, String message);

    /**
     * 校验持久化冲突描述是否仍对应当前工作区中的未合并索引和 Git blob。
     *
     * @param workspaceKey 工作区标识
     * @param conflict 持久化的冲突描述
     * @return 工作区及冲突现场是否仍有效
     */
    boolean isConflictWorkspaceValid(String workspaceKey, GitConflictDescriptor conflict);

    GitConflictContent readConflictContent(String workspaceKey, GitConflictDescriptor conflict);

    String continueMerge(String workspaceKey, List<GitFileResolution> resolutions, String message);

    void abortMerge(String workspaceKey);

    void pushDeployBranch(String workspaceKey, String deployBranch);

    void cleanup(String workspaceKey);

}
