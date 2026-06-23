package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo.PipelineRunLogLineRespVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineJobRuntime;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineRunLogFileStorage;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 流水线步骤文件日志辅助。
 */
@Slf4j
@Component
public class PipelineStepLogFileHelper {

    @Resource
    private PipelineRunLogFileStorage pipelineRunLogFileStorage;
    @Resource
    private FileApi fileApi;

    public PipelineRunLogFileStorage.PipelineRunLogFiles prepareLogFiles(PipelineRunLogDO runLog,
                                                                         PipelineJobRuntime runtime) {
        Path workspace = runtime == null ? null : runtime.getWorkspace();
        if (workspace == null) {
            return null;
        }
        return pipelineRunLogFileStorage.prepare(runLog, workspace);
    }

    public List<String> readSummaryLines(PipelineRunLogDO runLog, int maxLines) {
        List<String> logLines = new ArrayList<>();
        for (PipelineRunLogLineRespVO line : pipelineRunLogFileStorage.readLines(List.of(runLog), runLog.getStepId(),
                null, maxLines)) {
            logLines.add(line.getContent());
        }
        return logLines;
    }

    public void uploadFullLog(PipelineRunLogDO runLog) {
        byte[] content = pipelineRunLogFileStorage.buildFullLog(runLog);
        if (content.length == 0) {
            return;
        }
        try {
            String name = "pipeline-run-" + runLog.getPipelineRunId() + "-step-" + runLog.getStepId() + ".log";
            String directory = "devops/pipeline-run/" + runLog.getPipelineRunId();
            runLog.setLogFileUrl(fileApi.createFile(content, name, directory, "text/plain"));
        } catch (Exception ex) {
            log.warn("[uploadFullLog][runId({}) stepId({}) upload failed: {}]",
                    runLog.getPipelineRunId(), runLog.getStepId(), ex.getMessage(), ex);
        }
    }

}
