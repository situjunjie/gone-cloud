package cn.iocoder.yudao.module.devops.service.pipeline.execution;

import cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo.CodeMergeConflictDetailRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo.CodeMergeConflictResolutionReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo.CodeMergeConflictRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo.PipelineRunLogRespVO;

import java.util.List;

public interface PipelineExecutionService {

    void startCodeMerge(Long pipelineRunId, List<Long> changeIds, Long userId);

    List<PipelineRunLogRespVO> getRunLogs(Long pipelineRunId);

    List<CodeMergeConflictRespVO> getCodeMergeConflicts(Long pipelineRunId);

    CodeMergeConflictDetailRespVO getCodeMergeConflictDetail(Long pipelineRunId, String filePath);

    void saveCodeMergeConflictResolution(Long pipelineRunId, CodeMergeConflictResolutionReqVO reqVO, Long userId);

    void continueCodeMerge(Long pipelineRunId, Long userId);

    void retryCurrentCodeMergeChange(Long pipelineRunId, Long userId);

    void cancelRun(Long pipelineRunId, Long userId);

}
