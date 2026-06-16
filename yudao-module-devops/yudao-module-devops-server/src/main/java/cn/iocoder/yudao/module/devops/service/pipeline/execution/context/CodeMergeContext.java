package cn.iocoder.yudao.module.devops.service.pipeline.execution.context;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CodeMergeContext {

    private String baseBranch;
    private String baseCommitSha;
    private String deployBranch;
    private String workspaceKey;
    private Boolean pushOnSuccess;
    private Long currentChangeId;
    private String currentBranchName;
    private List<CodeMergeItemContext> items = new ArrayList<>();
    private List<CodeMergeConflictContext> conflicts = new ArrayList<>();
    private List<CodeMergeResolutionContext> resolutions = new ArrayList<>();

}
