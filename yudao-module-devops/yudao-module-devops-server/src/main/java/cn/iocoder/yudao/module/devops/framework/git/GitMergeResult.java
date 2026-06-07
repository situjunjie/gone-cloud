package cn.iocoder.yudao.module.devops.framework.git;

import lombok.Data;

import java.util.List;

@Data
public class GitMergeResult {

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_CONFLICTING = "CONFLICTING";

    private String status;
    private String mergeCommitSha;
    private List<GitConflictDescriptor> conflicts;
    private String output;

}
