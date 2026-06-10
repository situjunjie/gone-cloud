package cn.iocoder.yudao.module.devops.service.pipeline.execution;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 流水线代码合并异步启动服务。
 */
@Service
@Slf4j
public class PipelineCodeMergeAsyncService {

    @Resource
    private PipelineExecutionService pipelineExecutionService;

    @Async
    public void startCodeMergeAsync(Long pipelineRunId, List<Long> changeIds, Long userId) {
        try {
            pipelineExecutionService.startCodeMerge(pipelineRunId, changeIds, userId);
        } catch (Exception ex) {
            log.error("[startCodeMergeAsync][pipelineRunId({}) changeIds({}) userId({}) 启动代码合并失败]",
                    pipelineRunId, changeIds, userId, ex);
        }
    }

}
