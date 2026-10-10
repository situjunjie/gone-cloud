package cn.iocoder.yudao.module.devops.service.pipeline.execution.context;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import cn.iocoder.yudao.module.devops.framework.git.GitWorkspaceRuntimeRef;

@Data
public class CodeMergeContext {

    private String baseBranch;
    private String baseCommitSha;
    private String deployBranch;
    private Boolean deployBranchPushedForConflict;
    private String workspaceKey;
    private String workspacePath;
    private GitWorkspaceRuntimeRef runtimeRef;
    private Boolean pushOnSuccess;
    private Long currentChangeId;
    private String currentBranchName;
    private List<CodeMergeItemContext> items = new ArrayList<>();
    private List<CodeMergeConflictContext> conflicts = new ArrayList<>();
    private List<CodeMergeResolutionContext> resolutions = new ArrayList<>();

}
