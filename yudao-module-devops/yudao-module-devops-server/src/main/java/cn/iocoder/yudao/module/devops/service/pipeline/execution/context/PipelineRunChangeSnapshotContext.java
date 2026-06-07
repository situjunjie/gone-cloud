package cn.iocoder.yudao.module.devops.service.pipeline.execution.context;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 流水线运行中，本次发布提交的变更快照。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PipelineRunChangeSnapshotContext {

    /**
     * 变更编号。
     */
    private Long changeId;
    /**
     * 发布提交时，变更分支对应的远端 commit SHA。
     */
    private String commitSha;

}
