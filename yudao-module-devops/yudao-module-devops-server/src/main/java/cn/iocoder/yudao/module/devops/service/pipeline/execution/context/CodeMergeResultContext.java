package cn.iocoder.yudao.module.devops.service.pipeline.execution.context;

import lombok.Data;

import java.util.List;

@Data
public class CodeMergeResultContext {

    private String deployBranch;
    private String deployCommitSha;
    private List<Long> mergedChangeIds;

}
